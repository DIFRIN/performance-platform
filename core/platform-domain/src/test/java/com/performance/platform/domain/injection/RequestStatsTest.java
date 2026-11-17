package com.performance.platform.domain.injection;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestStatsTest {

    @Test
    void roundTripMap() {
        var s = new RequestStats(3, 1, LatencyHistogram.empty().with(10, true).with(20, false).with(30, true));
        assertThat(RequestStats.fromMap(s.toMap())).isEqualTo(s);
    }

    @Test
    void nullHistogramDefaultsToEmpty() {
        assertThat(new RequestStats(0, 0, null).histogram()).isEqualTo(LatencyHistogram.empty());
    }

    @Test
    void rejectsInvalidCounters() {
        assertThatThrownBy(() -> new RequestStats(-1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestStats(1, 2, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void injectionResultLegacyConstructorHasEmptyPerRequest() {
        var r = new InjectionResult(TaskId.of("t"), "S", Duration.ofSeconds(1), 0, 0, 0, 0.0, 0.0,
            0, 0, 0, 0, 0, 0, 0, 0.0, Path.of("."), Map.of());
        assertThat(r.perRequest()).isEmpty();
    }

    @Test
    void perRequestIsDefensivelyCopied() {
        var m = new java.util.HashMap<String, RequestStats>();
        m.put("a", new RequestStats(1, 0, null));
        var r = new InjectionResult(TaskId.of("t"), "S", Duration.ofSeconds(1), 1, 1, 0, 0.0, 0.0,
            0, 0, 0, 0, 0, 0, 0, 0.0, Path.of("."), Map.of(), null, null, null, null, null, true, m);
        m.clear();
        assertThat(r.perRequest()).containsKey("a");
    }
}
