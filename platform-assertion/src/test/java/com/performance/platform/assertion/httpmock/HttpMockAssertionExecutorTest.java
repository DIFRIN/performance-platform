package com.performance.platform.assertion.httpmock;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

import com.performance.platform.infrastructure.executor.http.HttpTargetProperties;
import com.performance.platform.infrastructure.executor.http.HttpTargetRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static com.performance.platform.domain.scenario.Phase.ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HttpMockAssertionExecutor")
class HttpMockAssertionExecutorTest {

    /**
     * Sous-classe de test qui stubbe l'appel HTTP WireMock.
     * Evite la dependance jdk.httpserver (non accessible en Java 25 sans --add-modules).
     */
    private static final class TestableHttpMockAssertionExecutor
            extends HttpMockAssertionExecutor {

        private Supplier<Double> countSupplier = () -> {
            throw new IllegalArgumentException("No count configured");
        };

        TestableHttpMockAssertionExecutor(HttpTargetRegistry registry) {
            super(registry);
        }

        void setCount(double count) {
            this.countSupplier = () -> count;
        }

        void setError(String message) {
            this.countSupplier = () -> {
                throw new IllegalArgumentException(message);
            };
        }

        @Override
        double fetchRequestCount(String baseUrl) {
            return countSupplier.get();
        }

        @Override
        double fetchRequestCountViaRestClient(RestClient restClient) {
            return countSupplier.get();
        }
    }

    private TestableHttpMockAssertionExecutor executor;

    private static final TaskId MOCK_TASK_ID = new TaskId("mock-task-001");
    private static final TaskId ASSERTION_ID = new TaskId("assertion-001");
    private static final ExecutionId EXEC_ID = new ExecutionId("exec-001");
    private static final ScenarioId SCENARIO_ID = new ScenarioId("scenario-001");

    private static HttpTargetRegistry defaultRegistry() {
        HttpTargetProperties props = new HttpTargetProperties(
                "http://localhost:8090",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Map.of(),
                Map.of());
        return new HttpTargetRegistry(
                Map.of("wiremock", props),
                RestClient.builder());
    }

    @BeforeEach
    void setUp() {
        executor = new TestableHttpMockAssertionExecutor(defaultRegistry());
    }

    // --- Context helpers ---

    private ExecutionContext contextWithMockUrl(String url) {
        TaskResult result = TaskResult.success(MOCK_TASK_ID, "mock-server",
                Duration.ofSeconds(5),
                Map.of("url", url, "port", 8090));
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID)
                .with(MOCK_TASK_ID.value(), "agent-1", result);
    }

    private ExecutionContext emptyContext() {
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID);
    }

    private ExecutionContext contextWithoutUrl() {
        TaskResult result = TaskResult.success(MOCK_TASK_ID, "mock-server",
                Duration.ofSeconds(5),
                Map.of("port", 8090)); // no "url" key
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID)
                .with(MOCK_TASK_ID.value(), "agent-1", result);
    }

    private static StepDefinition step(Map<String, Object> params) {
        return new StepDefinition(ASSERTION_ID, "http-mock", ASSERTION,
                params, null, null, null, null);
    }

    private Map<String, Object> params(String metric, String operator,
                                        double value, String refTaskId) {
        return Map.of("metric", metric, "operator", operator,
                "value", value, "refTaskId", refTaskId);
    }

    // ==================== Nominal Cases ====================

    @Nested
    @DisplayName("receivedCalls assertions")
    class ReceivedCalls {

        @Test
        @DisplayName("should pass when receivedCalls above threshold (GT)")
        void shouldPassReceivedCallsGt() {
            executor.setCount(150);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            StepDefinition step = step(params(
                    "receivedCalls", "GT", 100, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.isPassed()).isTrue();
            assertThat(ar.evidence().actualValue()).isEqualTo(150.0);
            assertThat(ar.evidence().expectedValue()).isEqualTo(100.0);
            assertThat(ar.evidence().operator()).isEqualTo(AssertionOperator.GT);
            assertThat(ar.description()).contains("PASSED", "receivedCalls", ">");
        }

        @Test
        @DisplayName("should fail when receivedCalls below threshold")
        void shouldFailReceivedCallsBelow() {
            executor.setCount(30);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            StepDefinition step = step(params(
                    "receivedCalls", "GT", 100, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.isPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("matchedCalls assertions")
    class MatchedCalls {

        @Test
        @DisplayName("should pass when matchedCalls meets threshold (GTE)")
        void shouldPassMatchedCallsGte() {
            executor.setCount(200);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            StepDefinition step = step(params(
                    "matchedCalls", "GTE", 200, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
        }
    }

    @Nested
    @DisplayName("unmatchedCalls assertions")
    class UnmatchedCalls {

        @Test
        @DisplayName("should pass when unmatchedCalls below threshold (LT)")
        void shouldPassUnmatchedCallsLt() {
            executor.setCount(2);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            StepDefinition step = step(params(
                    "unmatchedCalls", "LT", 5, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.evidence().actualValue()).isEqualTo(2.0);
        }
    }

    // ==================== All Operators ====================

    @Nested
    @DisplayName("all operators")
    class AllOperators {

        @Test
        @DisplayName("LT: 50 < 100 → PASSED")
        void ltPassed() {
            assertEvaluate(50, "LT", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("LTE: 100 <= 100 → PASSED")
        void ltePassed() {
            assertEvaluate(100, "LTE", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("GT: 150 > 100 → PASSED")
        void gtPassed() {
            assertEvaluate(150, "GT", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("GTE: 100 >= 100 → PASSED")
        void gtePassed() {
            assertEvaluate(100, "GTE", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("EQ: 100 == 100 → PASSED")
        void eqPassed() {
            assertEvaluate(100, "EQ", 100, AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("NEQ: 150 != 100 → PASSED")
        void neqPassed() {
            assertEvaluate(150, "NEQ", 100, AssertionStatus.PASSED);
        }

        private void assertEvaluate(int actualCount, String operator,
                                     double expected, AssertionStatus expectedStatus) {
            executor.setCount(actualCount);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            Map<String, Object> p = Map.of(
                    "metric", "receivedCalls",
                    "operator", operator,
                    "value", expected,
                    "refTaskId", MOCK_TASK_ID.value());
            StepDefinition step = step(p);

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
            executor.setCount(10);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            Map<String, Object> p = Map.of(
                    "metric", "unknownMetric",
                    "operator", "GT",
                    "value", 5,
                    "refTaskId", MOCK_TASK_ID.value());
            StepDefinition step = step(p);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Unsupported metric");
        }

        @Test
        @DisplayName("should return ERROR for unsupported operator")
        void shouldErrorOnUnsupportedOperator() {
            executor.setCount(10);
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            Map<String, Object> p = Map.of(
                    "metric", "receivedCalls",
                    "operator", "INVALID",
                    "value", 5,
                    "refTaskId", MOCK_TASK_ID.value());
            StepDefinition step = step(p);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Unsupported operator");
        }

        @Test
        @DisplayName("should return ERROR when refTaskId not found in context")
        void shouldErrorOnMissingRefTaskId() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(params(
                    "receivedCalls", "GT", 100, "non-existent-task"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Mock URL not found");
        }

        @Test
        @DisplayName("should return ERROR when URL missing from outputs")
        void shouldErrorOnMissingUrl() {
            ExecutionContext ctx = contextWithoutUrl();
            StepDefinition step = step(params(
                    "receivedCalls", "GT", 100, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Mock URL not found");
        }

        @Test
        @DisplayName("should return ERROR when fetchRequestCount throws")
        void shouldErrorOnFetchFailure() {
            executor.setError("WireMock admin API returned HTTP 500 for: http://localhost:8090/__admin/requests/count");
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            StepDefinition step = step(params(
                    "receivedCalls", "GT", 100, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("HTTP 500");
        }

        @Test
        @DisplayName("should return ERROR when count extraction fails")
        void shouldErrorOnBadJson() {
            executor.setError("Could not extract 'count' from WireMock response: {}");
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            StepDefinition step = step(params(
                    "receivedCalls", "GT", 100, MOCK_TASK_ID.value()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Could not extract 'count'");
        }

        @Test
        @DisplayName("should return ERROR for missing required parameter metric")
        void shouldErrorOnMissingMetric() {
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            Map<String, Object> p = Map.of(
                    "operator", "GT",
                    "value", 50,
                    "refTaskId", MOCK_TASK_ID.value());
            StepDefinition step = step(p);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "metric");
        }

        @Test
        @DisplayName("should return ERROR for missing required parameter refTaskId")
        void shouldErrorOnMissingRefTaskIdParam() {
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            Map<String, Object> p = Map.of(
                    "metric", "receivedCalls",
                    "operator", "GT",
                    "value", 50);
            StepDefinition step = step(p);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("No HTTP source configured");
        }

        @Test
        @DisplayName("should return ERROR for empty refTaskId")
        void shouldErrorOnEmptyRefTaskId() {
            ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
            Map<String, Object> p = Map.of(
                    "metric", "receivedCalls",
                    "operator", "GT",
                    "value", 50,
                    "refTaskId", "");
            StepDefinition step = step(p);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("No HTTP source configured");
        }
    }

    // ==================== v2: target flow (HttpTargetRegistry) ====================

    @Nested
    @DisplayName("v2 target flow")
    class TargetFlow {

        @Test
        @DisplayName("should pass using target param via HttpTargetRegistry")
        void shouldPassUsingTargetParam() {
            executor.setCount(150);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "metric", "receivedCalls",
                    "operator", "GT",
                    "value", 100,
                    "target", "wiremock"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.isPassed()).isTrue();
            assertThat(ar.evidence().actualValue()).isEqualTo(150.0);
            assertThat(ar.evidence().details()).containsEntry("target", "wiremock");
        }

        @Test
        @DisplayName("should return ERROR when target not found in registry")
        void shouldErrorOnUnknownTarget() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "metric", "receivedCalls",
                    "operator", "GT",
                    "value", 100,
                    "target", "nonexistent"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Unknown http-target");
        }

        @Test
        @DisplayName("should include target and baseUrl in evidence details")
        void shouldIncludeTargetInEvidence() {
            executor.setCount(42);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "metric", "receivedCalls",
                    "operator", "EQ",
                    "value", 42,
                    "target", "wiremock"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.evidence().details())
                    .containsEntry("target", "wiremock")
                    .containsEntry("mockUrl", "http://localhost:8090");
        }
    }

    // ==================== v2: wiremockUrl legacy flow ====================

    @Nested
    @DisplayName("wiremockUrl legacy flow")
    class WiremockUrlFlow {

        @Test
        @DisplayName("should use wiremockUrl when no target provided")
        void shouldUseWiremockUrlDirectly() {
            executor.setCount(75);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "metric", "receivedCalls",
                    "operator", "GT",
                    "value", 50,
                    "wiremockUrl", "http://legacy-wiremock:8090"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.evidence().actualValue()).isEqualTo(75.0);
            assertThat(ar.evidence().details())
                    .containsEntry("mockUrl", "http://legacy-wiremock:8090");
        }

        @Test
        @DisplayName("should prefer target over wiremockUrl when both present")
        void shouldPreferTargetOverWiremockUrl() {
            executor.setCount(100);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "metric", "receivedCalls",
                    "operator", "GT",
                    "value", 50,
                    "target", "wiremock",
                    "wiremockUrl", "http://legacy-wiremock:8090"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.evidence().details())
                    .containsEntry("target", "wiremock")
                    .containsEntry("mockUrl", "http://localhost:8090");
        }
    }

    // ==================== Error: no source ====================

    @Test
    @DisplayName("should return ERROR when no source configured")
    void shouldErrorWhenNoSourceConfigured() {
        ExecutionContext ctx = emptyContext();
        StepDefinition step = step(Map.of(
                "metric", "receivedCalls",
                "operator", "GT",
                "value", 100));

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
        assertThat(ar.description()).contains("No HTTP source configured");
    }

    // ==================== Null safety ====================

    @Test
    @DisplayName("should throw NPE on null context")
    void shouldThrowOnNullContext() {
        StepDefinition step = step(params(
                "receivedCalls", "GT", 100, MOCK_TASK_ID.value()));
        assertThatThrownBy(() -> executor.evaluate(null, step))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NPE on null step")
    void shouldThrowOnNullStep() {
        ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
        assertThatThrownBy(() -> executor.evaluate(ctx, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================== getSupportedAssertionName ====================

    @Test
    @DisplayName("should return 'http-mock' as supported assertion name")
    void shouldReturnHttpMockAsSupportedName() {
        assertThat(executor.getSupportedAssertionName()).isEqualTo("http-mock");
    }

    // ==================== Evidence structure ====================

    @Test
    @DisplayName("should include unit and details in evidence")
    void shouldIncludeUnitAndDetailsInEvidence() {
        executor.setCount(42);
        ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
        Map<String, Object> p = Map.of(
                "metric", "receivedCalls",
                "operator", "GT",
                "value", 10,
                "refTaskId", MOCK_TASK_ID.value(),
                "endpoint", "/api/test");
        StepDefinition step = step(p);

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.evidence().unit()).isEqualTo("calls");
        assertThat(ar.evidence().details())
                .containsEntry("metric", "receivedCalls")
                .containsEntry("refTaskId", MOCK_TASK_ID.value())
                .containsEntry("endpoint", "/api/test");
    }

    @Test
    @DisplayName("should include evaluation timing metadata")
    void shouldIncludeTimingMetadata() {
        executor.setCount(100);
        ExecutionContext ctx = contextWithMockUrl("http://localhost:8090");
        StepDefinition step = step(params(
                "receivedCalls", "EQ", 100, MOCK_TASK_ID.value()));

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.assertionId()).isEqualTo(ASSERTION_ID);
        assertThat(ar.evaluationDuration()).isNotNull();
        assertThat(ar.evaluatedAt()).isNotNull();
    }
}
