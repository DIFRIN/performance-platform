package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionSummaryTest {

    private static final TaskId ID = new TaskId("my-asst");
    private static final Duration DURATION = Duration.ofMillis(150);
    private static final Instant NOW = Instant.now();

    private static AssertionSummary summary(AssertionStatus status, String description,
                                            List<AssertionSample> history) {
        return new AssertionSummary(ID, status, description, history, DURATION, NOW,
                AssertionFamily.CUSTOM, SloStatement.empty(), List.of(), Rollup.all());
    }

    @Test
    void shouldConstructWithAllFields() {
        var summary = summary(AssertionStatus.PASSED, "ok", List.of());

        assertEquals(ID, summary.assertionId());
        assertEquals(AssertionStatus.PASSED, summary.verdict());
        assertEquals("ok", summary.description());
        assertTrue(summary.history().isEmpty());
        assertEquals(DURATION, summary.evaluationDuration());
        assertEquals(NOW, summary.evaluatedAt());
        assertEquals(AssertionFamily.CUSTOM, summary.family());
        assertTrue(summary.slo().blank());
        assertTrue(summary.checks().isEmpty());
    }

    @Test
    void shouldPreserveHistoryElements() {
        var sample = new AssertionSample(Instant.now(), 3.14, "ms", Map.of());
        var summary = summary(AssertionStatus.FAILED, "fail", List.of(sample));

        assertEquals(1, summary.history().size());
        assertEquals(sample, summary.history().get(0));
    }

    @Test
    void shouldRejectNullAssertionId() {
        assertThrows(NullPointerException.class, () -> new AssertionSummary(
                null, AssertionStatus.PASSED, "d", List.of(), DURATION, NOW,
                AssertionFamily.CUSTOM, SloStatement.empty(), List.of(), Rollup.all()));
    }

    @Test
    void shouldRejectNullStatus() {
        assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, null, "d", List.of(), DURATION, NOW,
                AssertionFamily.CUSTOM, SloStatement.empty(), List.of(), Rollup.all()));
    }

    @Test
    void shouldRejectNullDescription() {
        assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, AssertionStatus.PASSED, null, List.of(), DURATION, NOW,
                AssertionFamily.CUSTOM, SloStatement.empty(), List.of(), Rollup.all()));
    }

    @Test
    void shouldRejectNullDuration() {
        assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, AssertionStatus.PASSED, "d", List.of(), null, NOW,
                AssertionFamily.CUSTOM, SloStatement.empty(), List.of(), Rollup.all()));
    }

    @Test
    void shouldRejectNullEvaluatedAt() {
        assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, AssertionStatus.PASSED, "d", List.of(), DURATION, null,
                AssertionFamily.CUSTOM, SloStatement.empty(), List.of(), Rollup.all()));
    }

    @Test
    void isPassedDependsOnVerdict() {
        assertTrue(summary(AssertionStatus.PASSED, "d", List.of()).isPassed());
        assertFalse(summary(AssertionStatus.FAILED, "d", List.of()).isPassed());
        assertFalse(summary(AssertionStatus.INCONCLUSIVE, "d", List.of()).isPassed());
        assertFalse(summary(AssertionStatus.ERROR, "d", List.of()).isPassed());
        assertFalse(summary(AssertionStatus.SKIPPED, "d", List.of()).isPassed());
    }

    @Test
    void historyShouldBeDefensivelyCopied() {
        var sample = new AssertionSample(Instant.now(), 1.0, "ms", Map.of());
        var mutableList = new ArrayList<AssertionSample>();
        mutableList.add(sample);

        var summary = summary(AssertionStatus.PASSED, "d", mutableList);
        mutableList.add(new AssertionSample(Instant.now(), 2.0, "ms", Map.of()));

        assertEquals(1, summary.history().size());
    }

    @Test
    void checksShouldBeImmutable() {
        var metric = MetricId.of("injection.latency.p95");
        var window = TimeWindow.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        var observation = Observation.of(metric, 120, "ms", window, "test");
        var check = Check.passed(observation, Expectation.atMost(500, Severity.CRITICAL), "p95");

        var summary = new AssertionSummary(ID, AssertionStatus.PASSED, "d", List.of(),
                DURATION, NOW, AssertionFamily.PERFORMANCE_THRESHOLDS,
                new SloStatement("t", "sli", "slo", "why"), List.of(check), Rollup.all());

        assertEquals(1, summary.checks().size());
        assertThrows(UnsupportedOperationException.class,
                () -> summary.checks().add(check));
    }
}
