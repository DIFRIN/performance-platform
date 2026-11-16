package com.performance.platform.assertion.e2e;

import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.infrastructure.executor.DefaultTaskExecutorRegistry;
import com.performance.platform.infrastructure.executor.UnsupportedTaskNameException;
import com.performance.platform.plugin.AssertionExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pipeline d'assertion de bout en bout : resolution par le registre unifie,
 * verdict par check, conversion unique en {@link TaskResult}.
 */
@DisplayName("Assertion pipeline")
class AssertionPipelineE2ETest {

    private static final ExecutionId EXECUTION_ID = ExecutionId.of("assert-e2e");
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("assert-scenario");

    private DefaultTaskExecutorRegistry registry;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        registry = new DefaultTaskExecutorRegistry(List.of());
        context = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID);
    }

    @Test
    @DisplayName("an assertion yields a TaskResult whose summary holds the checks")
    void executesAsTask() {
        registry.register(passing("latency-check", "injection.latency.p95", 320, 500));

        TaskResult result = registry.getFor("latency-check")
                .execute(context, step("lat-1", "latency-check"));

        assertEquals(TaskStatus.SUCCESS, result.status());
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);
        assertEquals(AssertionStatus.PASSED, summary.verdict());
        assertEquals(1, summary.checks().size());
        assertEquals(320.0, summary.checks().get(0).actualValue());
        assertEquals("injection.latency.p95", summary.checks().get(0).metric().toString());
    }

    @Test
    @DisplayName("a failed check maps to a FAILED task result")
    void failingCheckMapsToFailedTask() {
        registry.register(passing("error-rate", "injection.error.rate", 4, 1));

        TaskResult result = registry.getFor("error-rate")
                .execute(context, step("err-1", "error-rate"));

        assertEquals(TaskStatus.FAILED, result.status());
        assertTrue(result.errorMessage().contains("injection.error.rate"));
    }

    @Test
    @DisplayName("a missing measurement is INCONCLUSIVE and stays a SUCCESS task")
    void missingMeasurementIsInconclusive() {
        registry.register(new StubAssertion("no-data", step -> new AssertionSummary(
                step.id(), AssertionStatus.INCONCLUSIVE, "no data", List.of(),
                Duration.ZERO, Instant.now(), AssertionFamily.DIRECT, null,
                List.of(Check.inconclusive(MetricId.of("database.scalar"), "rows", "no rows")),
                Rollup.all())));

        TaskResult result = registry.getFor("no-data").execute(context, step("db-1", "no-data"));

        assertEquals(TaskStatus.SUCCESS, result.status());
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);
        assertEquals(AssertionStatus.INCONCLUSIVE, summary.verdict());
    }

    @Test
    @DisplayName("multiple executors coexist in the registry")
    void multipleExecutorsCoexist() {
        registry.register(passing("a-check", "injection.latency.p50", 10, 100));
        registry.register(passing("b-check", "injection.latency.p99", 200, 900));

        TaskResult a = registry.getFor("a-check").execute(context, step("a", "a-check"));
        TaskResult b = registry.getFor("b-check").execute(context, step("b", "b-check"));

        assertEquals(TaskStatus.SUCCESS, a.status());
        assertEquals(TaskStatus.SUCCESS, b.status());
    }

    @Test
    @DisplayName("an unknown assertion name is rejected")
    void unknownNameRejected() {
        assertThrows(UnsupportedTaskNameException.class, () -> registry.getFor("missing"));
    }

    @Test
    @DisplayName("concurrent evaluations do not interfere")
    void concurrentEvaluations() {
        registry.register(passing("concurrent", "injection.latency.p95", 100, 500));
        AssertionExecutor executor = (AssertionExecutor) registry.getFor("concurrent");
        AtomicInteger failures = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int index = i;
            threads.add(Thread.ofVirtual().start(() -> {
                TaskResult result = executor.execute(context, step("c-" + index, "concurrent"));
                if (result.status() != TaskStatus.SUCCESS) {
                    failures.incrementAndGet();
                }
            }));
        }
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertEquals(0, failures.get());
    }

    // ------------------------------------------------------------------

    private static AssertionExecutor passing(String name, String metric, double actual, double threshold) {
        return new StubAssertion(name, step -> {
            var observation = Observation.of(MetricId.of(metric), actual, "",
                    com.performance.platform.domain.assertion.TimeWindow.of(
                            Instant.EPOCH, Instant.EPOCH.plusSeconds(60)), "test");
            var expectation = Expectation.atMost(threshold, Severity.CRITICAL);
            var check = actual <= threshold
                    ? Check.passed(observation, expectation, metric)
                    : Check.failed(observation, expectation, metric, "above threshold");
            return new AssertionSummary(step.id(), check.status(), metric + " " + check.status(),
                    List.of(), Duration.ZERO, Instant.now(),
                    AssertionFamily.PERFORMANCE_THRESHOLDS, null, List.of(check), Rollup.all());
        });
    }

    private static StepDefinition step(String id, String taskName) {
        return new StepDefinition(TaskId.of(id), taskName, Phase.ASSERTION,
                Map.of(), List.of(), List.of(), Duration.ofSeconds(30), null);
    }

    /** Assertion executor minimal sans dependance framework. */
    private static final class StubAssertion implements AssertionExecutor {

        private final String name;
        private final java.util.function.Function<StepDefinition, AssertionSummary> evaluator;

        private StubAssertion(String name,
                              java.util.function.Function<StepDefinition, AssertionSummary> evaluator) {
            this.name = name;
            this.evaluator = evaluator;
        }

        @Override
        public AssertionSummary evaluate(ExecutionContext context, StepDefinition step) {
            return evaluator.apply(step);
        }

        @Override
        public String getSupportedAssertionName() {
            return name;
        }
    }
}
