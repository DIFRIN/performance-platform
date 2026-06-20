package com.performance.platform.assertion.kafka;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.performance.platform.domain.scenario.Phase.ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KafkaAssertionExecutor")
class KafkaAssertionExecutorTest {

    private KafkaAssertionExecutor executor;

    private static final TaskId CONSUMER_TASK_ID = new TaskId("consumer-task-001");
    private static final TaskId PRODUCER_TASK_ID = new TaskId("producer-task-001");
    private static final TaskId ASSERTION_ID = new TaskId("assertion-001");
    private static final ExecutionId EXEC_ID = new ExecutionId("exec-001");
    private static final ScenarioId SCENARIO_ID = new ScenarioId("scenario-001");

    // --- Kafka output fixtures ---

    private static TaskResult consumerResult(int consumedCount, long lag) {
        return TaskResult.success(CONSUMER_TASK_ID, "kafka-consumer",
                Duration.ofSeconds(5),
                Map.of("messagesConsumed", consumedCount, "lag", lag));
    }

    private static TaskResult producerResult(int producedCount, int failedCount) {
        return TaskResult.success(PRODUCER_TASK_ID, "kafka-producer",
                Duration.ofSeconds(5),
                Map.of("messagesProduced", producedCount, "messagesFailed", failedCount));
    }

    private static ExecutionContext contextWithResult(String taskId, TaskResult result) {
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID)
                .with(taskId, "agent-1", result);
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID);
    }

    private static StepDefinition step(Map<String, Object> params) {
        return new StepDefinition(ASSERTION_ID, "kafka", ASSERTION,
                params, null, null, null, null);
    }

    // --- Param builders ---

    private static Map<String, Object> consumedCountParams(String refTaskId,
                                                            String operator, double value) {
        return Map.of("metric", "consumedCount", "operator", operator,
                "value", value, "refTaskId", refTaskId);
    }

    private static Map<String, Object> producedCountParams(String refTaskId,
                                                            String operator, double value) {
        return Map.of("metric", "producedCount", "operator", operator,
                "value", value, "refTaskId", refTaskId);
    }

    private static Map<String, Object> lagParams(String refTaskId,
                                                  String operator, double value) {
        return Map.of("metric", "lag", "operator", operator,
                "value", value, "refTaskId", refTaskId);
    }

    @BeforeEach
    void setUp() {
        executor = new KafkaAssertionExecutor();
    }

    // ==================== Nominal Cases ====================

    @Nested
    @DisplayName("consumedCount assertions")
    class ConsumedCount {

        @Test
        @DisplayName("should pass when consumedCount exceeds threshold (GT)")
        void shouldPassConsumedCountGt() {
            TaskResult result = consumerResult(150, 0);
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), result);
            StepDefinition step = step(consumedCountParams(
                    CONSUMER_TASK_ID.value(), "GT", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.isPassed()).isTrue();
            assertThat(ar.evidence().actualValue()).isEqualTo(150.0);
            assertThat(ar.evidence().expectedValue()).isEqualTo(100.0);
            assertThat(ar.evidence().operator()).isEqualTo(AssertionOperator.GT);
            assertThat(ar.description()).contains("PASSED", "consumedCount", "150.00", ">", "100.00");
        }

        @Test
        @DisplayName("should pass when consumedCount equals threshold (GTE)")
        void shouldPassConsumedCountGte() {
            TaskResult result = consumerResult(100, 0);
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), result);
            StepDefinition step = step(consumedCountParams(
                    CONSUMER_TASK_ID.value(), "GTE", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("should fail when consumedCount below threshold")
        void shouldFailConsumedCountBelow() {
            TaskResult result = consumerResult(50, 0);
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), result);
            StepDefinition step = step(consumedCountParams(
                    CONSUMER_TASK_ID.value(), "GT", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.isPassed()).isFalse();
            assertThat(ar.description()).contains("FAILED");
        }
    }

    @Nested
    @DisplayName("producedCount assertions")
    class ProducedCount {

        @Test
        @DisplayName("should pass when producedCount meets threshold (GTE)")
        void shouldPassProducedCountGte() {
            TaskResult result = producerResult(500, 3);
            ExecutionContext ctx = contextWithResult(
                    PRODUCER_TASK_ID.value(), result);
            StepDefinition step = step(producedCountParams(
                    PRODUCER_TASK_ID.value(), "GTE", 500));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.evidence().actualValue()).isEqualTo(500.0);
            assertThat(ar.evidence().operator()).isEqualTo(AssertionOperator.GTE);
        }

        @Test
        @DisplayName("should fail when producedCount below threshold")
        void shouldFailProducedCountBelow() {
            TaskResult result = producerResult(200, 0);
            ExecutionContext ctx = contextWithResult(
                    PRODUCER_TASK_ID.value(), result);
            StepDefinition step = step(producedCountParams(
                    PRODUCER_TASK_ID.value(), "GTE", 500));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.evidence().actualValue()).isEqualTo(200.0);
            assertThat(ar.evidence().expectedValue()).isEqualTo(500.0);
            assertThat(ar.evidence().operator()).isEqualTo(AssertionOperator.GTE);
        }
    }

    @Nested
    @DisplayName("lag assertions")
    class Lag {

        @Test
        @DisplayName("should pass when lag below threshold (LT)")
        void shouldPassLagBelowThreshold() {
            TaskResult result = consumerResult(1000, 5);
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), result);
            StepDefinition step = step(lagParams(
                    CONSUMER_TASK_ID.value(), "LT", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.evidence().actualValue()).isEqualTo(5.0);
            assertThat(ar.evidence().expectedValue()).isEqualTo(100.0);
            assertThat(ar.evidence().operator()).isEqualTo(AssertionOperator.LT);
        }

        @Test
        @DisplayName("should fail when lag exceeds threshold")
        void shouldFailLagExceedsThreshold() {
            TaskResult result = consumerResult(1000, 500);
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), result);
            StepDefinition step = step(lagParams(
                    CONSUMER_TASK_ID.value(), "LT", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.evidence().actualValue()).isEqualTo(500.0);
            assertThat(ar.evidence().expectedValue()).isEqualTo(100.0);
            assertThat(ar.evidence().operator()).isEqualTo(AssertionOperator.LT);
        }
    }

    @Nested
    @DisplayName("all operators")
    class AllOperators {

        @Test
        @DisplayName("LT: 50 < 100 → PASSED")
        void ltPassed() {
            assertEvaluate(consumerResult(50, 0), "LT", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("LTE: 100 <= 100 → PASSED")
        void ltePassed() {
            assertEvaluate(consumerResult(100, 0), "LTE", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("GT: 150 > 100 → PASSED")
        void gtPassed() {
            assertEvaluate(consumerResult(150, 0), "GT", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("GTE: 100 >= 100 → PASSED")
        void gtePassed() {
            assertEvaluate(consumerResult(100, 0), "GTE", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("EQ: 100 == 100 → PASSED")
        void eqPassed() {
            assertEvaluate(consumerResult(100, 0), "EQ", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("NEQ: 150 != 100 → PASSED")
        void neqPassed() {
            assertEvaluate(consumerResult(150, 0), "NEQ", 100, AssertionStatus.PASSED);
        }

        private void assertEvaluate(TaskResult result, String operator,
                                     double expected, AssertionStatus expectedStatus) {
            ExecutionContext ctx = contextWithResult(CONSUMER_TASK_ID.value(), result);
            Map<String, Object> params = Map.of(
                    "metric", "consumedCount",
                    "operator", operator,
                    "value", expected,
                    "refTaskId", CONSUMER_TASK_ID.value());
            StepDefinition step = step(params);

            AssertionResult ar = executor.evaluate(ctx, step);
            assertThat(ar.status()).isEqualTo(expectedStatus);
        }
    }

    // ==================== Error Cases ====================

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return ERROR for unsupported metric")
        void shouldErrorOnUnsupportedMetric() {
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), consumerResult(100, 0));
            Map<String, Object> params = Map.of(
                    "metric", "unknownMetric",
                    "operator", "GT",
                    "value", 50,
                    "refTaskId", CONSUMER_TASK_ID.value());
            StepDefinition step = step(params);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Unsupported metric");
        }

        @Test
        @DisplayName("should return ERROR for unsupported operator")
        void shouldErrorOnUnsupportedOperator() {
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), consumerResult(100, 0));
            Map<String, Object> params = Map.of(
                    "metric", "consumedCount",
                    "operator", "INVALID",
                    "value", 50,
                    "refTaskId", CONSUMER_TASK_ID.value());
            StepDefinition step = step(params);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Unsupported operator");
        }

        @Test
        @DisplayName("should return ERROR when refTaskId not found in context")
        void shouldErrorOnMissingRefTaskId() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(consumedCountParams(
                    "non-existent-task", "GT", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("No results found", "non-existent-task");
        }

        @Test
        @DisplayName("should return ERROR when output key not found")
        void shouldErrorOnMissingOutputKey() {
            // Producer result has messagesProduced, not messagesConsumed
            TaskResult result = producerResult(500, 0);
            ExecutionContext ctx = contextWithResult(PRODUCER_TASK_ID.value(), result);
            // Asking for consumedCount from a producer result
            StepDefinition step = step(consumedCountParams(
                    PRODUCER_TASK_ID.value(), "GT", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("not found");
        }

        @Test
        @DisplayName("should return ERROR for missing required parameter metric")
        void shouldErrorOnMissingMetric() {
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), consumerResult(100, 0));
            Map<String, Object> params = Map.of(
                    "operator", "GT",
                    "value", 50,
                    "refTaskId", CONSUMER_TASK_ID.value());
            StepDefinition step = step(params);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "metric");
        }

        @Test
        @DisplayName("should return ERROR for missing required parameter refTaskId")
        void shouldErrorOnMissingRefTaskIdParam() {
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), consumerResult(100, 0));
            Map<String, Object> params = Map.of(
                    "metric", "consumedCount",
                    "operator", "GT",
                    "value", 50);
            StepDefinition step = step(params);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "refTaskId");
        }

        @Test
        @DisplayName("should return ERROR for empty refTaskId parameter")
        void shouldErrorOnEmptyRefTaskId() {
            ExecutionContext ctx = contextWithResult(
                    CONSUMER_TASK_ID.value(), consumerResult(100, 0));
            Map<String, Object> params = Map.of(
                    "metric", "consumedCount",
                    "operator", "GT",
                    "value", 50,
                    "refTaskId", "");
            StepDefinition step = step(params);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("must be a non-empty string");
        }
    }

    // ==================== Null safety ====================

    @Test
    @DisplayName("should throw NPE on null context")
    void shouldThrowOnNullContext() {
        StepDefinition step = step(consumedCountParams(
                CONSUMER_TASK_ID.value(), "GT", 100));
        assertThatThrownBy(() -> executor.evaluate(null, step))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NPE on null step")
    void shouldThrowOnNullStep() {
        ExecutionContext ctx = contextWithResult(
                CONSUMER_TASK_ID.value(), consumerResult(100, 0));
        assertThatThrownBy(() -> executor.evaluate(ctx, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================== getSupportedAssertionName ====================

    @Test
    @DisplayName("should return 'kafka' as supported assertion name")
    void shouldReturnKafkaAsSupportedName() {
        assertThat(executor.getSupportedAssertionName()).isEqualTo("kafka");
    }

    // ==================== Evidence structure ====================

    @Test
    @DisplayName("should include unit and details in evidence")
    void shouldIncludeUnitAndDetailsInEvidence() {
        TaskResult result = consumerResult(150, 10);
        ExecutionContext ctx = contextWithResult(CONSUMER_TASK_ID.value(), result);
        Map<String, Object> params = Map.of(
                "metric", "consumedCount",
                "operator", "GT",
                "value", 100,
                "refTaskId", CONSUMER_TASK_ID.value(),
                "topic", "my-topic",
                "groupId", "my-group");
        StepDefinition step = step(params);

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.evidence().unit()).isEqualTo("messages");
        assertThat(ar.evidence().details())
                .containsEntry("metric", "consumedCount")
                .containsEntry("refTaskId", CONSUMER_TASK_ID.value())
                .containsEntry("topic", "my-topic")
                .containsEntry("groupId", "my-group");
    }

    @Test
    @DisplayName("should use 'offset' unit for lag metric")
    void shouldUseOffsetUnitForLag() {
        TaskResult result = consumerResult(1000, 50);
        ExecutionContext ctx = contextWithResult(CONSUMER_TASK_ID.value(), result);
        StepDefinition step = step(lagParams(CONSUMER_TASK_ID.value(), "LT", 100));

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.evidence().unit()).isEqualTo("offset");
    }

    @Test
    @DisplayName("should include evaluation timing metadata")
    void shouldIncludeTimingMetadata() {
        TaskResult result = consumerResult(100, 0);
        ExecutionContext ctx = contextWithResult(CONSUMER_TASK_ID.value(), result);
        StepDefinition step = step(consumedCountParams(
                CONSUMER_TASK_ID.value(), "EQ", 100));

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.assertionId()).isEqualTo(ASSERTION_ID);
        assertThat(ar.evaluationDuration()).isNotNull();
        assertThat(ar.evaluatedAt()).isNotNull();
    }
}
