package com.performance.platform.assertion.gatling;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static com.performance.platform.domain.scenario.Phase.ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GatlingMetricAssertionExecutor")
class GatlingMetricAssertionExecutorTest {

    private GatlingMetricAssertionExecutor executor;

    // --- Fixtures ---

    private static final TaskId TASK_ID = new TaskId("task-001");
    private static final TaskId ASSERTION_ID = new TaskId("assertion-001");
    private static final ExecutionId EXEC_ID = new ExecutionId("exec-001");
    private static final ScenarioId SCENARIO_ID = new ScenarioId("scenario-001");

    private static InjectionResult sampleResult() {
        return new InjectionResult(
                TASK_ID,
                "com.example.MySimulation",
                Duration.ofSeconds(120),
                10_000L,           // totalRequests
                9_900L,            // successfulRequests
                100L,              // failedRequests
                1.0,               // errorRate (%)
                83.3,              // throughput (req/s)
                45L,               // p50Ms
                85L,               // p75Ms
                150L,              // p90Ms
                200L,              // p95Ms
                350L,              // p99Ms
                500L,              // maxMs
                10L,               // minMs
                52.5,              // meanMs
                Path.of("/tmp/gatling/report"),
                Map.of("simulation", "MySimulation")
        );
    }

    private static ExecutionContext contextWithResult(InjectionResult result) {
        TaskResult taskResult = TaskResult.success(
                TASK_ID, "gatling", Duration.ofSeconds(120),
                Map.of("injectionResult", result));
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID)
                .with(TASK_ID.value(), "agent-1", taskResult);
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID);
    }

    private static StepDefinition step(Map<String, Object> params) {
        return new StepDefinition(ASSERTION_ID, "gatling-metric", ASSERTION,
                params, null, null, null, null);
    }

    private static Map<String, Object> basicParams() {
        return Map.of("metric", "p95", "operator", "LT", "value", 500);
    }

    @BeforeEach
    void setUp() {
        executor = new GatlingMetricAssertionExecutor();
    }

    // --- Nominal ---

    @Test
    @DisplayName("should return PASSED when p95 is below threshold")
    void shouldPassWhenP95BelowThreshold() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "p95", "operator", "LT", "value", 500));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.description()).contains("PASSED", "p95", "200", "<", "500");
        assertThat(result.evidence().actualValue()).isEqualTo(200.0);
        assertThat(result.evidence().expectedValue()).isEqualTo(500.0);
        assertThat(result.evidence().operator()).isEqualTo(AssertionOperator.LT);
        assertThat(result.evidence().unit()).isEqualTo("ms");
        assertThat(result.evidence().details()).containsKey("simulationClass");
    }

    @Test
    @DisplayName("should return FAILED when errorRate exceeds threshold")
    void shouldFailWhenErrorRateExceedsThreshold() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "errorRate", "operator", "LTE", "value", 0.5));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.FAILED);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.description()).contains("FAILED", "errorRate", "<=");
        assertThat(result.evidence().actualValue()).isEqualTo(1.0);
        assertThat(result.evidence().unit()).isEqualTo("%");
    }

    @Test
    @DisplayName("should pass throughput GTE comparison")
    void shouldPassThroughputGte() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "throughput", "operator", "GTE", "value", 80.0));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().actualValue()).isEqualTo(83.3);
    }

    @Test
    @DisplayName("should pass max LTE comparison")
    void shouldPassMaxLte() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "max", "operator", "LTE", "value", 500));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().actualValue()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("should support all metric names")
    void shouldSupportAllMetrics() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        String[] metrics = {"p50", "p75", "p90", "p95", "p99", "max", "min",
                            "mean", "errorRate", "throughput", "totalRequests",
                            "failedRequests"};
        for (String metric : metrics) {
            StepDefinition step = step(Map.of(
                    "metric", metric, "operator", "GTE", "value", 0));
            AssertionResult result = executor.evaluate(ctx, step);
            assertThat(result.status()).isIn(AssertionStatus.PASSED, AssertionStatus.FAILED);
        }
    }

    @Test
    @DisplayName("should pass EQ when actual equals expected")
    void shouldPassEq() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "totalRequests", "operator", "EQ", "value", 10000));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().actualValue()).isEqualTo(10000.0);
    }

    @Test
    @DisplayName("should fail NEQ when actual differs from expected")
    void shouldFailNeq() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "failedRequests", "operator", "NEQ", "value", 100));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.FAILED);
    }

    @Test
    @DisplayName("should resolve InjectionResult by refTaskId")
    void shouldResolveByRefTaskId() {
        TaskId refTaskId = new TaskId("injection-042");
        InjectionResult refResult = new InjectionResult(
                refTaskId, "com.example.OtherSim",
                Duration.ofSeconds(60), 5000, 4900, 100,
                2.0, 83.3, 30L, 60L, 100L, 180L, 300L, 450L, 5L, 45.0,
                Path.of("/tmp/report"), Map.of());
        TaskResult taskResult = TaskResult.success(
                refTaskId, "gatling", Duration.ofSeconds(60),
                Map.of("injectionResult", refResult));
        ExecutionContext ctx = emptyContext()
                .with(refTaskId.value(), "agent-1", taskResult);
        StepDefinition step = step(Map.of(
                "metric", "p95", "operator", "LT", "value", 500,
                "refTaskId", refTaskId.value()));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().details().get("refTaskId"))
                .isEqualTo(refTaskId.value());
    }

    @Test
    @DisplayName("should get supported assertion name")
    void shouldReturnSupportedAssertionName() {
        assertThat(executor.getSupportedAssertionName())
                .isEqualTo("gatling-metric");
    }

    // --- Error cases ---

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should return ERROR when metric parameter missing")
        void shouldErrorOnMissingMetric() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of("operator", "LT", "value", 500));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("metric");
        }

        @Test
        @DisplayName("should return ERROR when operator parameter missing")
        void shouldErrorOnMissingOperator() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of("metric", "p95", "value", 500));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("operator");
        }

        @Test
        @DisplayName("should return ERROR when value parameter missing")
        void shouldErrorOnMissingValue() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of("metric", "p95", "operator", "LT"));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("value");
        }

        @Test
        @DisplayName("should return ERROR for unsupported metric name")
        void shouldErrorOnUnsupportedMetric() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of(
                    "metric", "stddev", "operator", "LT", "value", 100));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("stddev");
        }

        @Test
        @DisplayName("should return ERROR for unsupported operator")
        void shouldErrorOnUnsupportedOperator() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of(
                    "metric", "p95", "operator", "WITHIN", "value", 500));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("WITHIN");
        }

        @Test
        @DisplayName("should return ERROR when no InjectionResult in context")
        void shouldErrorOnMissingInjectionResult() {
            StepDefinition step = step(basicParams());

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("InjectionResult");
        }

        @Test
        @DisplayName("should return ERROR when refTaskId points to unknown task")
        void shouldErrorOnUnknownRefTaskId() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of(
                    "metric", "p95", "operator", "LT", "value", 500,
                    "refTaskId", "nonexistent-task"));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("InjectionResult");
        }

        @Test
        @DisplayName("should handle context with no InjectionResult-typed outputs")
        void shouldErrorWhenNoInjectionResultType() {
            TaskResult unrelatedResult = TaskResult.success(
                    TASK_ID, "some-task", Duration.ofSeconds(10),
                    Map.of("someKey", "someStringValue"));
            ExecutionContext ctx = emptyContext()
                    .with(TASK_ID.value(), "agent-1", unrelatedResult);
            StepDefinition step = step(basicParams());

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
        }
    }

    // --- Null safety ---

    @Nested
    @DisplayName("Null safety")
    class NullSafety {

        @Test
        @DisplayName("should throw NPE when context is null")
        void shouldThrowNpeOnNullContext() {
            StepDefinition step = step(basicParams());

            assertThatThrownBy(() -> executor.evaluate(null, step))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when step is null")
        void shouldThrowNpeOnNullStep() {
            assertThatThrownBy(() -> executor.evaluate(
                    emptyContext(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // --- Evidence details ---

    @Nested
    @DisplayName("Evidence details")
    class EvidenceDetails {

        @Test
        @DisplayName("should include simulationClass in evidence details")
        void shouldIncludeSimulationClass() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(basicParams());

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.evidence().details().get("simulationClass"))
                    .isEqualTo("com.example.MySimulation");
        }

        @Test
        @DisplayName("should include totalRequests in evidence details")
        void shouldIncludeTotalRequests() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(basicParams());

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.evidence().details().get("totalRequests"))
                    .isEqualTo(10_000L);
        }

        @Test
        @DisplayName("should include metric name in evidence details")
        void shouldIncludeMetricNameInDetails() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of(
                    "metric", "throughput", "operator", "GT", "value", 1.0));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.evidence().details().get("metric"))
                    .isEqualTo("throughput");
        }

        @Test
        @DisplayName("should have immutable details map")
        void shouldHaveImmutableDetailsMap() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(basicParams());

            AssertionResult result = executor.evaluate(ctx, step);

            Map<String, Object> details = result.evidence().details();
            assertThatThrownBy(() -> details.put("newKey", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should use custom unit when provided")
        void shouldUseCustomUnit() {
            ExecutionContext ctx = contextWithResult(sampleResult());
            StepDefinition step = step(Map.of(
                    "metric", "p95", "operator", "LT", "value", 500,
                    "unit", "milliseconds"));

            AssertionResult result = executor.evaluate(ctx, step);

            assertThat(result.evidence().unit()).isEqualTo("milliseconds");
        }
    }

    // --- Operator case insensitivity ---

    @Test
    @DisplayName("should accept lowercase operator")
    void shouldAcceptLowercaseOperator() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "p95", "operator", "lt", "value", 500));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
    }

    @Test
    @DisplayName("should accept mixed-case operator")
    void shouldAcceptMixedCaseOperator() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "p95", "operator", "Lt", "value", 500));

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
    }

    // --- Value as integer ---

    @Test
    @DisplayName("should accept integer value parameter")
    void shouldAcceptIntegerValue() {
        ExecutionContext ctx = contextWithResult(sampleResult());
        StepDefinition step = step(Map.of(
                "metric", "p95", "operator", "LT", "value", 500)); // Integer

        AssertionResult result = executor.evaluate(ctx, step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().expectedValue()).isEqualTo(500.0);
    }
}
