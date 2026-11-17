package com.performance.platform.assertion;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.GeneratorHealth;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.LoadWindow;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InjectionAggregatorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static LoadWindow win(int idx, long planned, long started, long req, boolean measured) {
        return new LoadWindow(T0.plusSeconds(5L * idx), Duration.ofSeconds(5), planned, started, req, 0, measured);
    }

    private static InjectionResult result(Instant start, Instant end, LatencyHistogram h, List<LoadWindow> ws,
                                          long total, long failed, long p95) {
        return new InjectionResult(TaskId.of("t"), "Sim", Duration.between(start, end), total, total - failed,
            failed, InjectionResult.computeErrorRate(total, failed), 7.0, p95, p95, p95, p95, p95, p95, 1, 1.0,
            Path.of("."), Map.of(), start, end, h, ws,
            new GeneratorHealth(0.5, Duration.ofMillis(40), 10), true);
    }

    private static LatencyHistogram hist(long ms, int n) {
        LatencyHistogram h = LatencyHistogram.empty();
        for (int i = 0; i < n; i++) {
            h = h.with(ms, true);
        }
        return h;
    }

    @Test
    void singleAgentKeepsItsOwnValues() {
        InjectionResult r = new InjectionResult(TaskId.of("t"), "Sim", Duration.ofSeconds(10), 100, 100, 0, 0.0,
            10.0, 5, 6, 7, 8, 9, 20, 1, 3.0, Path.of("."), Map.of());
        var a = InjectionAggregator.aggregate(List.of(r));
        assertThat(a.get("injection.latency.p95").value()).isEqualTo(8.0);
        assertThat(a.get("injection.throughput").value()).isEqualTo(10.0);
        assertThat(a.get("injection.start.skew").value()).isEqualTo(0.0);
        assertThat(a.get("injection.arrival.deviation").isInconclusive()).isTrue();
    }

    @Test
    void threeAgentsMergeHistogramsAndSumThroughput() {
        List<InjectionResult> rs = new ArrayList<>();
        LatencyHistogram all = LatencyHistogram.empty();
        long[] lat = {10, 20, 300};
        for (long l : lat) {
            LatencyHistogram h = hist(l, 100);
            all = all.merge(h);
            List<LoadWindow> ws = List.of(win(0, 50, 50, 100, true), win(1, 50, 40, 100, true));
            rs.add(result(T0, T0.plusSeconds(10), h, ws, 200, 0, l));
        }
        var a = InjectionAggregator.aggregate(rs);
        assertThat(a.get("injection.latency.p95").value()).isEqualTo((double) all.quantile(0.95));
        assertThat(a.get("injection.latency.p95.worst-agent").value()).isEqualTo(300.0);
        assertThat(a.get("injection.throughput").value()).isEqualTo(600.0 / 10.0);
        assertThat(a.get("injection.arrival.planned").value()).isEqualTo(300.0);
        assertThat(a.get("injection.arrival.achieved").value()).isEqualTo(270.0);
        assertThat(a.get("injection.arrival.deviation").value()).isEqualTo(10.0);
    }

    @Test
    void staggeredStartsUseCommonWindowAndReportSkew() {
        LatencyHistogram h = hist(10, 10);
        var early = result(T0, T0.plusSeconds(20), h,
            List.of(win(0, 1, 1, 10, true), win(1, 1, 1, 10, true), win(2, 1, 1, 10, true), win(3, 1, 1, 10, true)),
            40, 0, 10);
        var late = result(T0.plusSeconds(5), T0.plusSeconds(25), h,
            List.of(win(1, 1, 1, 10, true), win(2, 1, 1, 10, true), win(3, 1, 1, 10, true), win(4, 1, 1, 10, true)),
            40, 0, 10);
        var a = InjectionAggregator.aggregate(List.of(early, late));
        // fenêtres communes : 1, 2, 3 -> chaque agent 10 req x3 -> 60 req / 15 s... borné à [5s,20s]
        assertThat(a.get("injection.throughput").value()).isEqualTo(60.0 / 15.0);
        assertThat(a.get("injection.start.skew").value()).isEqualTo(5000.0);
    }

    @Test
    void agentWithoutHistogramMakesGlobalPercentileInconclusive() {
        var withH = result(T0, T0.plusSeconds(10), hist(10, 10), List.of(win(0, 1, 1, 10, true)), 10, 0, 10);
        var legacy = new InjectionResult(TaskId.of("t"), "Sim", Duration.ofSeconds(10), 10, 10, 0, 0.0, 1.0,
            50, 50, 50, 50, 50, 50, 1, 1.0, Path.of("."), Map.of());
        var a = InjectionAggregator.aggregate(List.of(withH, legacy));
        assertThat(a.get("injection.latency.p95").isInconclusive()).isTrue();
        assertThat(a.get("injection.latency.p95.worst-agent").value()).isEqualTo(50.0);
    }

    @Test
    void warmupWindowsAreExcluded() {
        var r = result(T0, T0.plusSeconds(15), hist(10, 10),
            List.of(win(0, 99, 99, 500, false), win(1, 10, 10, 20, true)), 520, 0, 10);
        var a = InjectionAggregator.aggregate(List.of(r));
        assertThat(a.get("injection.arrival.planned").value()).isEqualTo(10.0);
    }
}
