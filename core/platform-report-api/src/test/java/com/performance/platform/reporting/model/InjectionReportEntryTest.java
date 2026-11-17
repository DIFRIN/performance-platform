package com.performance.platform.reporting.model;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InjectionReportEntryTest {

    private static final TaskId TASK = new TaskId("task-1");
    private static final Path DIR = Path.of("/tmp/gatling");
    private static final InjectionResult METRICS = new InjectionResult(TASK, "Sim", Duration.ofSeconds(5),
            100, 99, 1, 1.0, 20.0, 10, 15, 20, 25, 30, 35, 5, 8.5, DIR, Map.of());

    @Test
    void legacyConstructorsHaveNoPlan() {
        var e = new InjectionReportEntry(TASK, METRICS, DIR);
        assertFalse(e.planRecorded());
        assertTrue(e.workloadPlan().isEmpty());
        assertEquals("", e.workloadPlanHash());
        assertNull(e.agentId());
        var e2 = new InjectionReportEntry(TASK, METRICS, DIR, "agent-1");
        assertEquals("agent-1", e2.agentId());
        assertFalse(e2.planRecorded());
    }

    @Test
    void nullsDefault() {
        var e = new InjectionReportEntry(TASK, METRICS, DIR, null, null, null);
        assertTrue(e.workloadPlan().isEmpty());
        assertEquals("", e.workloadPlanHash());
    }

    @Test
    void planRecordedWhenHashPresent() {
        var e = new InjectionReportEntry(TASK, METRICS, DIR, null, Map.of("k", "v"), "abc123");
        assertTrue(e.planRecorded());
        assertEquals("v", e.workloadPlan().get("k"));
    }

    @Test
    void planIsDefensivelyCopied() {
        var src = new HashMap<String, Object>();
        src.put("k", "v");
        var e = new InjectionReportEntry(TASK, METRICS, DIR, null, src, "h");
        src.put("x", "y");
        assertEquals(1, e.workloadPlan().size());
        assertThrows(UnsupportedOperationException.class, () -> e.workloadPlan().put("a", "b"));
    }
}
