package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionSample;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionResultMapperTest {

    private static final TaskId ASSERTION_ID = TaskId.of("assert-001");
    private static final String TASK_NAME = "gatling-metric";
    private static final Duration EVAL_DURATION = Duration.ofMillis(42);
    private static final Instant EVAL_TIME = Instant.now();

    private static final Evidence SAMPLE_EVIDENCE = new Evidence(
            95.0,
            100.0,
            AssertionOperator.LT,
            "ms",
            Map.of("percentile", "p99")
    );

    private static StepDefinition step() {
        return new StepDefinition(
                ASSERTION_ID,
                TASK_NAME,
                Phase.ASSERTION,
                Map.of("metric", "responseTimeMs"),
                List.of(),
                List.of(),
                Duration.ofMinutes(1),
                null
        );
    }

    private static StepDefinition step(String taskName) {
        return new StepDefinition(
                ASSERTION_ID,
                taskName,
                Phase.ASSERTION,
                Map.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }

    // ============================================================
    // toTaskResult sans history
    // ============================================================

    @Test
    @DisplayName("toTaskResult PASSED -> SUCCESS with AssertionSummary in outputs")
    void passedMapsToSuccessWithSummary() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "Response time 95ms < 100ms", SAMPLE_EVIDENCE,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        assertThat(taskResult.taskId()).isEqualTo(ASSERTION_ID);
        assertThat(taskResult.taskName()).isEqualTo(TASK_NAME);
        assertThat(taskResult.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskResult.duration()).isEqualTo(EVAL_DURATION);
        assertThat(taskResult.completedAt()).isEqualTo(EVAL_TIME);
        assertThat(taskResult.errorMessage()).isNull();
        assertThat(taskResult.cause()).isNull();

        assertThat(taskResult.outputs()).containsKey("assertion");
        Object summaryObj = taskResult.outputs().get("assertion");
        assertThat(summaryObj).isInstanceOf(AssertionSummary.class);

        AssertionSummary summary = (AssertionSummary) summaryObj;
        assertThat(summary.assertionId()).isEqualTo(ASSERTION_ID);
        assertThat(summary.verdict()).isEqualTo(AssertionStatus.PASSED);
        assertThat(summary.description()).isEqualTo("Response time 95ms < 100ms");
        assertThat(summary.evaluationDuration()).isEqualTo(EVAL_DURATION);
        assertThat(summary.evaluatedAt()).isEqualTo(EVAL_TIME);
        assertThat(summary.history()).isEmpty();
        assertThat(summary.collectedData()).containsEntry("actual", 95.0);
        assertThat(summary.collectedData()).containsEntry("expected", 100.0);
        assertThat(summary.collectedData()).containsEntry("operator", "LT");
        assertThat(summary.collectedData()).containsEntry("unit", "ms");
    }

    @Test
    @DisplayName("toTaskResult FAILED -> FAILED, errorMessage = description")
    void failedMapsToFailedWithErrorMessage() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.FAILED,
                "Response time 120ms > 100ms", SAMPLE_EVIDENCE,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        assertThat(taskResult.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskResult.errorMessage()).isEqualTo("Response time 120ms > 100ms");

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        assertThat(summary.verdict()).isEqualTo(AssertionStatus.FAILED);
    }

    @Test
    @DisplayName("toTaskResult SKIPPED -> SKIPPED")
    void skippedMapsToSkipped() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.SKIPPED,
                "Skipped due to missing dependency", null,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        assertThat(taskResult.status()).isEqualTo(TaskStatus.SKIPPED);
        assertThat(taskResult.errorMessage()).isNull();

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        assertThat(summary.verdict()).isEqualTo(AssertionStatus.SKIPPED);
    }

    @Test
    @DisplayName("toTaskResult ERROR -> FAILED")
    void errorMapsToFailed() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.ERROR,
                "Connection timeout", null,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        assertThat(taskResult.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskResult.errorMessage()).isEqualTo("Connection timeout");

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        assertThat(summary.verdict()).isEqualTo(AssertionStatus.ERROR);
    }

    // ============================================================
    // toTaskResult avec history
    // ============================================================

    @Test
    @DisplayName("toTaskResult with explicit history preserves samples in AssertionSummary")
    void withHistoryPreservesHistory() {
        List<AssertionSample> history = List.of(
                new AssertionSample(Instant.now(), 95.0, "ms", Map.of()),
                new AssertionSample(Instant.now(), 97.0, "ms", Map.of())
        );

        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "OK", SAMPLE_EVIDENCE,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step(), history);

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        assertThat(summary.history()).hasSize(2);
        assertThat(summary.history().get(0).observedValue()).isEqualTo(95.0);
        assertThat(summary.history().get(1).observedValue()).isEqualTo(97.0);
    }

    @Test
    @DisplayName("toTaskResult sans history appel toTaskResult avec history vide")
    void withoutHistoryDelegatesWithEmptyHistory() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "OK", SAMPLE_EVIDENCE,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        assertThat(summary.history()).isEmpty();
    }

    // ============================================================
    // extractSummary
    // ============================================================

    @Test
    @DisplayName("extractSummary returns AssertionSummary when key 'assertion' is present")
    void extractSummaryWithValidTaskResult() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "OK", SAMPLE_EVIDENCE,
                EVAL_DURATION, EVAL_TIME);
        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        AssertionSummary extracted = AssertionResultMapper.extractSummary(taskResult);

        assertThat(extracted).isNotNull();
        assertThat(extracted.assertionId()).isEqualTo(ASSERTION_ID);
        assertThat(extracted.verdict()).isEqualTo(AssertionStatus.PASSED);
    }

    @Test
    @DisplayName("extractSummary returns null when key 'assertion' is absent")
    void extractSummaryWithoutAssertionKey() {
        TaskResult taskResult = new TaskResult(
                ASSERTION_ID, TASK_NAME, TaskStatus.SUCCESS,
                Duration.ZERO, Map.of("other", "value"),
                null, null, Instant.now());

        AssertionSummary extracted = AssertionResultMapper.extractSummary(taskResult);

        assertThat(extracted).isNull();
    }

    @Test
    @DisplayName("extractSummary returns null when taskResult is null")
    void extractSummaryWithNullTaskResult() {
        assertThat(AssertionResultMapper.extractSummary(null)).isNull();
    }

    @Test
    @DisplayName("extractSummary returns null when outputs is null (edge case)")
    void extractSummaryWithNullOutputs() {
        TaskResult taskResult = new TaskResult(
                ASSERTION_ID, TASK_NAME, TaskStatus.SUCCESS,
                Duration.ZERO, null,
                null, null, Instant.now());

        AssertionSummary extracted = AssertionResultMapper.extractSummary(taskResult);

        assertThat(extracted).isNull();
    }

    @Test
    @DisplayName("toTaskResult with null evidence produces empty collectedData")
    void nullEvidenceProducesEmptyCollectedData() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "No evidence collected", null,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        assertThat(summary.collectedData()).isEmpty();
    }

    @Test
    @DisplayName("toTaskResult skips null actualValue and expectedValue in collectedData")
    void nullActualAndExpectedInEvidence() {
        Evidence evidence = new Evidence(null, null, AssertionOperator.GT, null, Map.of());
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "Null values OK", evidence,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step());

        AssertionSummary summary = (AssertionSummary) taskResult.outputs().get("assertion");
        // null values are skipped to respect Map.copyOf() contract
        assertThat(summary.collectedData()).doesNotContainKey("actual");
        assertThat(summary.collectedData()).doesNotContainKey("expected");
        assertThat(summary.collectedData()).containsEntry("operator", "GT");
        assertThat(summary.collectedData()).doesNotContainKey("unit");
    }

    @Test
    @DisplayName("extractSummary returns null when 'assertion' value is wrong type")
    void extractSummaryWithWrongType() {
        TaskResult taskResult = new TaskResult(
                ASSERTION_ID, TASK_NAME, TaskStatus.SUCCESS,
                Duration.ZERO, Map.of("assertion", "not a summary"),
                null, null, Instant.now());

        AssertionSummary extracted = AssertionResultMapper.extractSummary(taskResult);

        assertThat(extracted).isNull();
    }

    @Test
    @DisplayName("toTaskResult with different taskName from step")
    void differentTaskNameFromStep() {
        AssertionResult result = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED,
                "OK", SAMPLE_EVIDENCE,
                EVAL_DURATION, EVAL_TIME);

        TaskResult taskResult = AssertionResultMapper.toTaskResult(result, step("custom-task"));

        assertThat(taskResult.taskName()).isEqualTo("custom-task");
        assertThat(taskResult.taskId()).isEqualTo(ASSERTION_ID);
    }

    // ============================================================
    // Constructor is private (utility class)
    // ============================================================

    @Nested
    @DisplayName("Utility class invariants")
    class UtilityClass {

        @Test
        @DisplayName("AssertionResultMapper has private constructor")
        void privateConstructor() throws Exception {
            var ctor = AssertionResultMapper.class.getDeclaredConstructor();
            assertThat(ctor.canAccess(null)).isFalse();
            ctor.setAccessible(true);
            var instance = ctor.newInstance();
            assertThat(instance).isNotNull();
        }
    }
}
