package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionSummaryTest {

    private static final TaskId ID = new TaskId("my-asst");
    private static final Duration DURATION = Duration.ofMillis(150);
    private static final Instant NOW = Instant.now();

    @Test
    void shouldConstructWithAllFields() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.PASSED, "ok",
                Map.of("metric", 42), List.of(), DURATION, NOW);

        assertEquals(ID, summary.assertionId());
        assertEquals(AssertionStatus.PASSED, summary.verdict());
        assertEquals("ok", summary.description());
        assertEquals(Map.of("metric", 42), summary.collectedData());
        assertTrue(summary.history().isEmpty());
        assertEquals(DURATION, summary.evaluationDuration());
        assertEquals(NOW, summary.evaluatedAt());
    }

    @Test
    void shouldPreserveHistoryElements() {
        var sample = new AssertionSample(Instant.now(), 3.14, "ms", Map.of());
        var summary = new AssertionSummary(
                ID, AssertionStatus.FAILED, "fail",
                Map.of("val", 7), List.of(sample), DURATION, NOW);

        assertEquals(1, summary.history().size());
        assertEquals(sample, summary.history().get(0));
    }

    @Test
    void shouldReplaceNullCollectedDataWithEmptyMap() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", null, List.of(), DURATION, NOW);

        assertNotNull(summary.collectedData());
        assertTrue(summary.collectedData().isEmpty());
    }

    @Test
    void shouldReplaceNullHistoryWithEmptyList() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", Map.of(), null, DURATION, NOW);

        assertNotNull(summary.history());
        assertTrue(summary.history().isEmpty());
    }

    @Test
    void shouldRejectNullAssertionId() {
        var ex = assertThrows(NullPointerException.class, () -> new AssertionSummary(
                null, AssertionStatus.PASSED, "desc", Map.of(), List.of(), DURATION, NOW));
        assertTrue(ex.getMessage().contains("assertionId"));
    }

    @Test
    void shouldRejectNullVerdict() {
        var ex = assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, null, "desc", Map.of(), List.of(), DURATION, NOW));
        assertTrue(ex.getMessage().contains("verdict"));
    }

    @Test
    void shouldRejectNullDescription() {
        var ex = assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, AssertionStatus.PASSED, null, Map.of(), List.of(), DURATION, NOW));
        assertTrue(ex.getMessage().contains("description"));
    }

    @Test
    void shouldRejectNullEvaluationDuration() {
        var ex = assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", Map.of(), List.of(), null, NOW));
        assertTrue(ex.getMessage().contains("evaluationDuration"));
    }

    @Test
    void shouldRejectNullEvaluatedAt() {
        var ex = assertThrows(NullPointerException.class, () -> new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", Map.of(), List.of(), DURATION, null));
        assertTrue(ex.getMessage().contains("evaluatedAt"));
    }

    @Test
    void isPassedShouldReturnTrueForPassed() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", Map.of(), List.of(), DURATION, NOW);
        assertTrue(summary.isPassed());
    }

    @Test
    void isPassedShouldReturnFalseForFailed() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.FAILED, "desc", Map.of(), List.of(), DURATION, NOW);
        assertFalse(summary.isPassed());
    }

    @Test
    void isPassedShouldReturnFalseForSkipped() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.SKIPPED, "desc", Map.of(), List.of(), DURATION, NOW);
        assertFalse(summary.isPassed());
    }

    @Test
    void isPassedShouldReturnFalseForError() {
        var summary = new AssertionSummary(
                ID, AssertionStatus.ERROR, "desc", Map.of(), List.of(), DURATION, NOW);
        assertFalse(summary.isPassed());
    }

    @Test
    void factoryOfShouldCreateWithEmptyHistory() {
        var summary = AssertionSummary.of(ID, AssertionStatus.PASSED, "factory",
                Collections.singletonMap("key", "val"), DURATION, NOW);

        assertEquals(ID, summary.assertionId());
        assertEquals(AssertionStatus.PASSED, summary.verdict());
        assertEquals("factory", summary.description());
        assertEquals(Collections.singletonMap("key", "val"), summary.collectedData());
        assertTrue(summary.history().isEmpty());
        assertEquals(DURATION, summary.evaluationDuration());
        assertEquals(NOW, summary.evaluatedAt());
    }

    @Test
    void collectedDataShouldBeDefensivelyCopied() {
        var mutableMap = new HashMap<String, Object>();
        mutableMap.put("x", 1);
        var summary = new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", mutableMap, List.of(), DURATION, NOW);

        mutableMap.put("y", 2);
        assertFalse(summary.collectedData().containsKey("y"));
    }

    @Test
    void historyShouldBeDefensivelyCopied() {
        var sample = new AssertionSample(Instant.now(), 1.0, "ms", Map.of());
        var mutableList = new ArrayList<AssertionSample>();
        mutableList.add(sample);
        var summary = new AssertionSummary(
                ID, AssertionStatus.PASSED, "desc", Map.of(), mutableList, DURATION, NOW);

        mutableList.add(new AssertionSample(Instant.now(), 2.0, "ms", Map.of()));
        assertEquals(1, summary.history().size());
    }
}
