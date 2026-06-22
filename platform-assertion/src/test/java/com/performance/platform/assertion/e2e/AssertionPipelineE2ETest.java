package com.performance.platform.assertion.e2e;

import com.performance.platform.assertion.DefaultAssertionExecutorRegistry;
import com.performance.platform.assertion.UnsupportedAssertionNameException;
import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.AssertionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests E2E du pipeline d'assertion multi-executor.
 * <p>
 * Scenarios realistes simulant le flux complet d'assertion :
 * - Enregistrement de multiples AssertionExecutor
 * - Evaluation coordonnee de plusieurs assertions
 * - Propagation du contexte entre execution et assertion
 * - Gestion des cas d'erreur (nom inconnu, echec)
 */
@DisplayName("Assertion Pipeline E2E")
class AssertionPipelineE2ETest {

    private DefaultAssertionExecutorRegistry registry;
    private ExecutionContext context;
    private static final ExecutionId EXECUTION_ID = ExecutionId.of("assert-e2e");
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("assert-scenario");

    @BeforeEach
    void setUp() {
        registry = new DefaultAssertionExecutorRegistry(List.of());
        context = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID);
    }

    // ========================================================================
    // Multi-assertion pipeline
    // ========================================================================

    @Nested
    @DisplayName("Multi-assertion pipeline")
    class MultiAssertionPipeline {

        @Test
        @DisplayName("E2E-AS-01: Multiple assertion types evaluated in sequence")
        void multipleAssertionTypes() {
            var gatlingCalls = new AtomicInteger(0);
            var dbCalls = new AtomicInteger(0);
            var fileCalls = new AtomicInteger(0);

            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    gatlingCalls.incrementAndGet();
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "p95=320ms OK", null, Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "gatling-metric"; }
            });

            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    dbCalls.incrementAndGet();
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "row count OK", null, Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "database"; }
            });

            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    fileCalls.incrementAndGet();
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "file exists", null, Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "file"; }
            });

            // Execute all three assertion types
            var result1 = registry.getFor("gatling-metric")
                    .evaluate(context, buildStep("assert-1", "gatling-metric"));
            var result2 = registry.getFor("database")
                    .evaluate(context, buildStep("assert-2", "database"));
            var result3 = registry.getFor("file")
                    .evaluate(context, buildStep("assert-3", "file"));

            assertEquals(1, gatlingCalls.get());
            assertEquals(1, dbCalls.get());
            assertEquals(1, fileCalls.get());
            assertEquals(AssertionStatus.PASSED, result1.status());
            assertEquals(AssertionStatus.PASSED, result2.status());
            assertEquals(AssertionStatus.PASSED, result3.status());
        }

        @Test
        @DisplayName("E2E-AS-02: Assertion with Evidence is produced correctly")
        void assertionWithEvidence() {
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    var evidence = new Evidence(
                            320, 500, AssertionOperator.LT,
                            "ms", Map.of("percentile", "p95", "sample", 10000));
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "p95 within threshold", evidence,
                            Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "latency-check"; }
            });

            AssertionResult result = registry.getFor("latency-check")
                    .evaluate(context, buildStep("lat-1", "latency-check"));

            assertEquals(AssertionStatus.PASSED, result.status());
            assertNotNull(result.evidence());
            assertEquals(AssertionOperator.LT, result.evidence().operator());
            assertEquals(320, result.evidence().actualValue());
            assertEquals(500, result.evidence().expectedValue());
        }

        @Test
        @DisplayName("E2E-AS-03: FAILED assertion with descriptive message")
        void failedAssertionWithMessage() {
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    return new AssertionResult(step.id(), AssertionStatus.FAILED,
                            "errorRate=0.05 exceeds threshold=0.01",
                            null, Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "error-rate"; }
            });

            AssertionResult result = registry.getFor("error-rate")
                    .evaluate(context, buildStep("err-1", "error-rate"));

            assertEquals(AssertionStatus.FAILED, result.status());
            assertTrue(result.description().contains("errorRate"));
            assertFalse(result.isPassed());
        }

        @Test
        @DisplayName("E2E-AS-04: Unknown assertion name throws UnsupportedAssertionNameException")
        void unknownAssertionNameThrows() {
            assertThrows(UnsupportedAssertionNameException.class,
                    () -> registry.getFor("non-existent-assertion-xyz"));
        }
    }

    // ========================================================================
    // Realistic assertion scenarios
    // ========================================================================

    @Nested
    @DisplayName("Realistic assertion scenarios")
    class RealisticScenarios {

        @Test
        @DisplayName("E2E-AS-10: Gatling performance SLA check (p95 + error rate + throughput)")
        void gatlingSlaCheck() {
            List<AssertionResult> results = new ArrayList<>();

            // Register SLA assertion executors
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    double p95 = Double.parseDouble(step.parameters().get("p95Ms").toString());
                    double threshold = Double.parseDouble(step.parameters().get("threshold").toString());
                    boolean passed = p95 <= threshold;
                    return new AssertionResult(step.id(),
                            passed ? AssertionStatus.PASSED : AssertionStatus.FAILED,
                            String.format("p95=%.0fms, threshold=%.0fms -> %s", p95, threshold,
                                    passed ? "PASS" : "FAIL"),
                            new Evidence(p95, threshold, AssertionOperator.LT, "ms", Map.of()),
                            Duration.ofMillis(1), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "p95-check"; }
            });

            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    double errRate = Double.parseDouble(step.parameters().get("errorRate").toString());
                    double threshold = Double.parseDouble(step.parameters().get("threshold").toString());
                    boolean passed = errRate <= threshold;
                    return new AssertionResult(step.id(),
                            passed ? AssertionStatus.PASSED : AssertionStatus.FAILED,
                            String.format("errorRate=%.4f, threshold=%.4f -> %s", errRate, threshold,
                                    passed ? "PASS" : "FAIL"),
                            null, Duration.ofMillis(1), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "error-rate-check"; }
            });

            // Run SLA checks with realistic values
            AssertionResult p95Result = registry.getFor("p95-check")
                    .evaluate(context, buildStep("sla-p95", "p95-check",
                            Map.of("p95Ms", 320.0, "threshold", 500.0)));
            results.add(p95Result);
            assertEquals(AssertionStatus.PASSED, p95Result.status());

            AssertionResult errResult = registry.getFor("error-rate-check")
                    .evaluate(context, buildStep("sla-err", "error-rate-check",
                            Map.of("errorRate", 0.002, "threshold", 0.01)));
            results.add(errResult);
            assertEquals(AssertionStatus.PASSED, errResult.status());

            // Test failing case
            AssertionResult failingP95 = registry.getFor("p95-check")
                    .evaluate(context, buildStep("sla-p95-fail", "p95-check",
                            Map.of("p95Ms", 750.0, "threshold", 500.0)));
            results.add(failingP95);
            assertEquals(AssertionStatus.FAILED, failingP95.status());

            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("E2E-AS-11: Multiple assertions all pass -> overall verdict is PASS")
        void allAssertionsPass() {
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "OK", null, Duration.ofMillis(1), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "generic-pass"; }
            });

            boolean allPassed = true;
            for (int i = 0; i < 5; i++) {
                AssertionResult r = registry.getFor("generic-pass")
                        .evaluate(context, buildStep("pass-" + i, "generic-pass"));
                allPassed &= r.isPassed();
            }

            assertTrue(allPassed, "All assertions should pass");
        }

        @Test
        @DisplayName("E2E-AS-12: Single assertion fails -> overall verdict is FAIL")
        void singleAssertionFails() {
            var callCount = new AtomicInteger(0);
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    int call = callCount.incrementAndGet();
                    // Third call fails
                    boolean passed = call != 3;
                    return new AssertionResult(step.id(),
                            passed ? AssertionStatus.PASSED : AssertionStatus.FAILED,
                            "check " + call, null, Duration.ofMillis(1), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "parity-check"; }
            });

            List<AssertionResult> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(registry.getFor("parity-check")
                        .evaluate(context, buildStep("parity-" + i, "parity-check")));
            }

            assertEquals(5, results.size());
            long passedCount = results.stream().filter(AssertionResult::isPassed).count();
            long failedCount = results.stream().filter(r -> r.status() == AssertionStatus.FAILED).count();

            assertEquals(4, passedCount);
            assertEquals(1, failedCount);
        }
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("E2E-AS-20: Duplicate assertion name registration — last wins")
        void duplicateRegistrationLastWins() {
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    return new AssertionResult(step.id(), AssertionStatus.FAILED,
                            "first", null, Duration.ZERO, Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "dup"; }
            });

            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "second", null, Duration.ZERO, Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "dup"; }
            });

            AssertionResult result = registry.getFor("dup")
                    .evaluate(context, buildStep("dup-1", "dup"));
            assertEquals("second", result.description());
        }

        @Test
        @DisplayName("E2E-AS-21: Many concurrent assertion calls work correctly")
        void concurrentAssertions() throws Exception {
            var callCount = new AtomicInteger(0);
            registry.register(new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
                    callCount.incrementAndGet();
                    return new AssertionResult(step.id(), AssertionStatus.PASSED,
                            "ok", null, Duration.ofMillis(1), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "concurrent"; }
            });

            int threads = 10;
            var threadArray = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                threadArray[i] = new Thread(() -> {
                    registry.getFor("concurrent").evaluate(context,
                            buildStep("conc-" + idx, "concurrent"));
                });
                threadArray[i].start();
            }

            for (Thread t : threadArray) {
                t.join(5000);
            }

            assertEquals(threads, callCount.get());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static StepDefinition buildStep(String id, String assertionName) {
        return new StepDefinition(TaskId.of(id), assertionName, Phase.ASSERTION,
                Map.of(), List.of(), List.of(), Duration.ofSeconds(30), null);
    }

    private static StepDefinition buildStep(String id, String assertionName,
                                            Map<String, Object> params) {
        return new StepDefinition(TaskId.of(id), assertionName, Phase.ASSERTION,
                params, List.of(), List.of(), Duration.ofSeconds(30), null);
    }
}
