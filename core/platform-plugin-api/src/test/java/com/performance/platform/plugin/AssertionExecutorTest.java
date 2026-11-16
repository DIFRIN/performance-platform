package com.performance.platform.plugin;

import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.TimeWindow;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie le contrat {@link AssertionExecutor} : une implementation ne produit
 * qu'un {@link AssertionSummary}, la conversion en {@link TaskResult} est
 * unique et fournie par le core.
 */
@DisplayName("AssertionExecutor extends TaskExecutor")
class AssertionExecutorTest {

    private static final TaskId ASSERTION_ID = new TaskId("assertion-1");
    private static final String ASSERTION_NAME = "test-assertion";
    private static final ExecutionContext CONTEXT =
            ExecutionContext.initial(new ExecutionId("exec-1"), new ScenarioId("scenario-1"));
    private static final StepDefinition STEP =
            new StepDefinition(ASSERTION_ID, ASSERTION_NAME, Phase.ASSERTION,
                    Map.of(), List.of(), List.of(), null, null);
    private static final Instant NOW = Instant.now();
    private static final Duration DURATION = Duration.ofMillis(42);

    private static AssertionSummary summary(AssertionStatus status, String description,
                                            List<Check> checks) {
        return new AssertionSummary(ASSERTION_ID, status, description,
                List.of(), DURATION, NOW,
                AssertionFamily.DIRECT, null, checks, Rollup.all());
    }

    private static Check passingCheck() {
        var metric = MetricId.of("injection.latency.p95");
        var observation = Observation.of(metric, 320, "ms",
                TimeWindow.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(60)), "test");
        return Check.passed(observation, Expectation.atMost(500, Severity.CRITICAL), "p95");
    }

    private static AssertionExecutor executor(AssertionStatus status) {
        return new AssertionExecutor() {
            @Override
            public AssertionSummary evaluate(ExecutionContext context, StepDefinition step) {
                return summary(status, status + " outcome", List.of(passingCheck()));
            }

            @Override
            public String getSupportedAssertionName() {
                return ASSERTION_NAME;
            }
        };
    }

    @Test
    @DisplayName("execute() converts the summary into a TaskResult with the canonical output key")
    void executeShouldConvertSummary() {
        TaskResult result = executor(AssertionStatus.PASSED).execute(CONTEXT, STEP);

        assertEquals(ASSERTION_ID, result.taskId());
        assertEquals(ASSERTION_NAME, result.taskName());
        assertEquals(TaskStatus.SUCCESS, result.status());
        assertEquals(DURATION, result.duration());
        assertEquals(NOW, result.completedAt());
        assertNull(result.errorMessage());
        assertNull(result.cause());

        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);
        assertNotNull(summary);
        assertEquals(AssertionStatus.PASSED, summary.verdict());
        assertEquals(1, summary.checks().size());
        assertEquals(320.0, summary.checks().get(0).actualValue());
    }

    @Test
    @DisplayName("FAILED and ERROR map to a FAILED task with the description as error message")
    void failedAndErrorMapToFailedTask() {
        TaskResult failed = executor(AssertionStatus.FAILED).execute(CONTEXT, STEP);
        assertEquals(TaskStatus.FAILED, failed.status());
        assertTrue(failed.errorMessage().contains("FAILED outcome"));

        TaskResult error = executor(AssertionStatus.ERROR).execute(CONTEXT, STEP);
        assertEquals(TaskStatus.FAILED, error.status());
        assertTrue(error.errorMessage().contains("ERROR outcome"));
    }

    @Test
    @DisplayName("SKIPPED maps to SKIPPED, INCONCLUSIVE stays a successful task")
    void skippedAndInconclusive() {
        assertEquals(TaskStatus.SKIPPED, executor(AssertionStatus.SKIPPED).execute(CONTEXT, STEP).status());
        assertEquals(TaskStatus.SUCCESS,
                executor(AssertionStatus.INCONCLUSIVE).execute(CONTEXT, STEP).status());
    }

    @Test
    @DisplayName("getSupportedTaskName() delegates to the assertion name")
    void taskNameDelegates() {
        var custom = new AssertionExecutor() {
            @Override
            public AssertionSummary evaluate(ExecutionContext context, StepDefinition step) {
                return summary(AssertionStatus.PASSED, "ok", List.of());
            }

            @Override
            public String getSupportedAssertionName() {
                return "custom-assertion";
            }
        };
        assertEquals("custom-assertion", custom.getSupportedTaskName());
    }
}
