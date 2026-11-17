package com.performance.platform.reporting.engine;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.GeneratorHealth;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.LoadWindow;
import com.performance.platform.reporting.model.InjectionReportEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InjectionMeasurementsReportTest {
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration W = Duration.ofSeconds(5);

    private static InjectionReportEntry entry(String agent, long p95, LatencyHistogram h, List<LoadWindow> ws,
                                              GeneratorHealth health) {
        long req = ws.stream().mapToLong(LoadWindow::requests).sum();
        var r = new InjectionResult(new TaskId("inj"), "Sim", Duration.ofSeconds(10), req, req, 0, 0.0, 1.0,
                p95, p95, p95, p95, p95, p95, 1, 1.0, Path.of("."), Map.of(), T0, T0.plusSeconds(10), h, ws,
                health, true);
        return new InjectionReportEntry(new TaskId("inj"), r, Path.of("."), agent);
    }

    @Test
    void exposesPlannedAchievedGlobalWorstAgentPerAgentAndHealth() {
        var h1 = LatencyHistogram.empty();
        var h2 = LatencyHistogram.empty();
        for (int i = 0; i < 90; i++) h1 = h1.with(10, true);
        for (int i = 0; i < 10; i++) h2 = h2.with(100, true);
        var a = entry("a", 10, h1, List.of(
                new LoadWindow(T0, W, 5, 4, 60, 0, false),
                new LoadWindow(T0.plus(W), W, 30, 28, 30, 0, true)), new GeneratorHealth(0.5, Duration.ofMillis(20), 100));
        var b = entry("b", 100, h2, List.of(
                new LoadWindow(T0, W, 5, 5, 6, 0, false),
                new LoadWindow(T0.plus(W), W, 10, 9, 4, 0, true)), new GeneratorHealth(0.9, Duration.ofMillis(80), 200));

        var s = InjectionMeasurementsSection.build(List.of(a, b));

        assertEquals(2, s.timeline().size());
        assertEquals(10, s.timeline().get(0).planned());
        assertEquals(9, s.timeline().get(0).achieved());
        assertEquals("non mesuré", s.timeline().get(0).label());
        assertEquals("mesuré", s.timeline().get(1).label());
        assertTrue(s.globalAvailable());
        assertEquals(10L, s.globalPercentiles().get("p50"));
        assertEquals(100L, s.globalPercentiles().get("p99"));
        assertEquals(100L, s.worstAgentPercentiles().get("p95"));
        assertEquals(2, s.agents().size());
        assertEquals(0.9, s.agents().stream().filter(x -> x.agentId().equals("a")).findFirst().orElseThrow().requestShare(), 0.01);
        assertEquals(0.9, s.health().stream().filter(x -> x.agentId().equals("b")).findFirst().orElseThrow().cpuLoad());
        assertEquals(100, s.evidence().mergedHistogram().count());
        assertEquals(2, s.evidence().aggregatedWindows().size());
    }

    @Test
    void globalPercentilesUnavailableWithoutHistogram() {
        var legacy = entry("a", 10, LatencyHistogram.empty(),
                List.of(new LoadWindow(T0, W, 1, 1, 5, 0, true)), GeneratorHealth.unknown());
        var s = InjectionMeasurementsSection.build(List.of(legacy));
        assertFalse(s.globalAvailable());
        assertNull(s.globalPercentiles());
        assertEquals(10L, s.worstAgentPercentiles().get("p95"));
    }

    @Test
    void toMapExposesCanonicalSections() {
        var h = LatencyHistogram.empty().with(10, true);
        var a = entry("a", 10, h, List.of(new LoadWindow(T0, W, 5, 4, 1, 0, false)), GeneratorHealth.unknown());
        var m = InjectionMeasurementsSection.build(List.of(a)).toMap();
        for (var k : List.of("timeline", "globalPercentiles", "worstAgentPercentiles", "agents", "health", "evidence"))
            assertTrue(m.containsKey(k), k);
        assertEquals("non mesuré", ((Map<?, ?>) ((List<?>) m.get("timeline")).get(0)).get("label"));
    }
}
