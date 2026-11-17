package com.performance.platform.reporting.engine;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.GeneratorHealth;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LatencyHistogram;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadPlanEvidenceTest {
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private static InjectionResult result() {
        return new InjectionResult(new TaskId("inj"), "Sim", Duration.ofSeconds(10), 1, 1, 0, 0.0, 1.0,
                1, 1, 1, 1, 1, 1, 1, 1.0, Path.of("."), Map.of(), T0, T0.plusSeconds(10),
                LatencyHistogram.empty(), List.of(), GeneratorHealth.unknown(), true);
    }

    @Test
    void planAndHashAreCarriedIntoEntry() {
        var e = DefaultReportEngine.injectionEntry(new TaskId("inj"), "a", result(),
                Map.of("workloadPlan", Map.of("steps", List.of("s1")), "workloadPlanHash", "abc123"));
        assertTrue(e.planRecorded());
        assertEquals("abc123", e.workloadPlanHash());
        assertEquals(List.of("s1"), e.workloadPlan().get("steps"));
    }

    @Test
    void missingPlanMeansNotRecorded() {
        var e = DefaultReportEngine.injectionEntry(new TaskId("inj"), "a", result(), Map.of());
        assertFalse(e.planRecorded());
        assertEquals("", e.workloadPlanHash());
        assertTrue(e.workloadPlan().isEmpty());
    }
}
