package com.performance.platform.assertion;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.RequestStats;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamedRequestMetricsTest {

    private static LatencyHistogram hist(long ms, int n) {
        LatencyHistogram h = LatencyHistogram.empty();
        for (int i = 0; i < n; i++) {
            h = h.with(ms, true);
        }
        return h;
    }

    private static InjectionResult agent(Map<String, RequestStats> per, LatencyHistogram total) {
        return new InjectionResult(TaskId.of("t"), "Sim", Duration.ofSeconds(10), 200, 200, 0, 0.0, 10.0,
            5, 5, 5, 5, 5, 5, 1, 1.0, Path.of("."), Map.of(), Instant.EPOCH, Instant.EPOCH, total,
            List.of(), com.performance.platform.domain.injection.GeneratorHealth.unknown(), true, per);
    }

    @Test
    void namedP95MergesAcrossAgents() {
        LatencyHistogram fast = hist(10, 100);
        LatencyHistogram slow = hist(500, 100);
        var a1 = agent(Map.of("create", new RequestStats(100, 0, slow), "read", new RequestStats(100, 0, fast)),
            slow.merge(fast));
        var a2 = agent(Map.of("create", new RequestStats(100, 0, slow)), slow);
        var a = InjectionAggregator.aggregate(List.of(a1, a2));
        assertThat(a.get("injection.latency.p95[create]").value())
            .isEqualTo((double) slow.merge(slow).quantile(0.95));
        assertThat(a.get("injection.latency.p95[read]").value()).isEqualTo((double) fast.quantile(0.95));
        assertThat(a.get("injection.requests.total[create]").value()).isEqualTo(200.0);
        assertThat(a.get("injection.requests.total").value()).isEqualTo(400.0);
    }

    @Test
    void unknownNameIsInconclusive() {
        var a = InjectionAggregator.aggregate(List.of(agent(Map.of(), hist(10, 5))));
        assertThat(a.get("injection.latency.p95[nope]").isInconclusive()).isTrue();
    }

    @Test
    void unknownBaseMetricRejected() {
        var a = InjectionAggregator.aggregate(List.of(agent(Map.of(), hist(10, 5))));
        assertThatThrownBy(() -> a.get("injection.bogus[x]")).isInstanceOf(IllegalArgumentException.class);
    }
}
