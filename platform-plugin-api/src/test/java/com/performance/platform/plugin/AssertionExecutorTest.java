package com.performance.platform.plugin;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Evidence;
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
 * Tests pour l'interface {@link AssertionExecutor} etendue de {@link TaskExecutor}.
 * Verifie les default methods {@code execute()}, {@code getSupportedTaskName()},
 * et la conversion {@code AssertionResult → TaskResult} avec {@link AssertionSummary}.
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

    // --- execute() appelle evaluate() et retourne TaskResult ---

    @Test
    @DisplayName("execute() should call evaluate() and return TaskResult with AssertionSummary")
    void executeShouldCallEvaluateAndReturnTaskResult() {
        var now = Instant.now();
        var duration = Duration.ofMillis(42);
        var evidence = new Evidence(100.0, 200.0,
                com.performance.platform.domain.assertion.AssertionOperator.GT,
                "ms",
                Map.of("metric", "responseTime"));

        var assertionResult = new AssertionResult(
                ASSERTION_ID, AssertionStatus.PASSED, "Test passed",
                evidence, duration, now);

        AssertionExecutor executor = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return assertionResult;
            }

            @Override
            public String getSupportedAssertionName() {
                return ASSERTION_NAME;
            }
        };

        TaskResult result = executor.execute(CONTEXT, STEP);

        // --- TaskResult assertions ---
        assertEquals(ASSERTION_ID, result.taskId());
        assertEquals(ASSERTION_NAME, result.taskName());
        assertEquals(TaskStatus.SUCCESS, result.status());
        assertEquals(duration, result.duration());
        assertEquals(now, result.completedAt());
        assertNull(result.errorMessage());
        assertNull(result.cause());
        assertNotNull(result.outputs());
        assertTrue(result.outputs().containsKey("assertion"),
                "outputs should contain key 'assertion'");

        // --- AssertionSummary assertions ---
        AssertionSummary summary = (AssertionSummary) result.outputs().get("assertion");
        assertNotNull(summary, "AssertionSummary must not be null");
        assertEquals(ASSERTION_ID, summary.assertionId());
        assertEquals(AssertionStatus.PASSED, summary.verdict());
        assertEquals("Test passed", summary.description());
        assertEquals(duration, summary.evaluationDuration());
        assertEquals(now, summary.evaluatedAt());
        assertTrue(summary.collectedData().containsKey("metric"),
                "collectedData should contain evidence details");
        assertEquals("responseTime", summary.collectedData().get("metric"));
    }

    @Test
    @DisplayName("execute() should handle FAILED assertion and map to TaskStatus.FAILED")
    void executeShouldHandleFailedAssertion() {
        var now = Instant.now();
        var duration = Duration.ofMillis(10);

        var assertionResult = new AssertionResult(
                ASSERTION_ID, AssertionStatus.FAILED, "Expected >= 100, got 50",
                null, duration, now);

        AssertionExecutor executor = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return assertionResult;
            }

            @Override
            public String getSupportedAssertionName() {
                return ASSERTION_NAME;
            }
        };

        TaskResult result = executor.execute(CONTEXT, STEP);

        assertEquals(TaskStatus.FAILED, result.status());
        assertEquals("Expected >= 100, got 50", result.errorMessage());

        AssertionSummary summary = (AssertionSummary) result.outputs().get("assertion");
        assertEquals(AssertionStatus.FAILED, summary.verdict());
    }

    @Test
    @DisplayName("execute() should handle SKIPPED assertion and map to TaskStatus.SKIPPED")
    void executeShouldHandleSkippedAssertion() {
        var now = Instant.now();

        var assertionResult = new AssertionResult(
                ASSERTION_ID, AssertionStatus.SKIPPED, "No data to evaluate",
                null, Duration.ZERO, now);

        AssertionExecutor executor = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return assertionResult;
            }

            @Override
            public String getSupportedAssertionName() {
                return ASSERTION_NAME;
            }
        };

        TaskResult result = executor.execute(CONTEXT, STEP);

        assertEquals(TaskStatus.SKIPPED, result.status());
        assertNull(result.errorMessage());

        AssertionSummary summary = (AssertionSummary) result.outputs().get("assertion");
        assertEquals(AssertionStatus.SKIPPED, summary.verdict());
    }

    @Test
    @DisplayName("execute() should handle ERROR assertion and map to TaskStatus.FAILED")
    void executeShouldHandleErrorAssertion() {
        var now = Instant.now();
        var duration = Duration.ofMillis(5);

        var assertionResult = new AssertionResult(
                ASSERTION_ID, AssertionStatus.ERROR, "Connection refused",
                null, duration, now);

        AssertionExecutor executor = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return assertionResult;
            }

            @Override
            public String getSupportedAssertionName() {
                return ASSERTION_NAME;
            }
        };

        TaskResult result = executor.execute(CONTEXT, STEP);

        assertEquals(TaskStatus.FAILED, result.status());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("Connection refused"));

        AssertionSummary summary = (AssertionSummary) result.outputs().get("assertion");
        assertEquals(AssertionStatus.ERROR, summary.verdict());
    }

    // --- getSupportedTaskName() delegue ---

    @Test
    @DisplayName("getSupportedTaskName() should delegate to getSupportedAssertionName()")
    void getSupportedTaskNameShouldDelegate() {
        AssertionExecutor executor = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return null;
            }

            @Override
            public String getSupportedAssertionName() {
                return "custom-assertion";
            }
        };

        assertEquals("custom-assertion", executor.getSupportedTaskName());
    }

    // --- Backward compatibility ---

    @Test
    @DisplayName("stub implementing only evaluate() and getSupportedAssertionName() should inherit execute()")
    void stubImplementingOnlyAssertionMethodsShouldInheritExecute() {
        var now = Instant.now();

        // Stub: implemente uniquement les methodes d'AssertionExecutor, pas execute() ni getSupportedTaskName()
        AssertionExecutor stub = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return new AssertionResult(
                        step.id(),
                        AssertionStatus.PASSED,
                        "Backward-compatible stub",
                        null,
                        Duration.ofMillis(1),
                        now);
            }

            @Override
            public String getSupportedAssertionName() {
                return "stub";
            }
        };

        // La methode execute() heritee de la default method doit fonctionner
        TaskResult result = stub.execute(CONTEXT, STEP);

        assertEquals(ASSERTION_ID, result.taskId());
        assertEquals("stub", result.taskName());
        assertEquals(TaskStatus.SUCCESS, result.status());

        AssertionSummary summary = (AssertionSummary) result.outputs().get("assertion");
        assertEquals("Backward-compatible stub", summary.description());
    }

    @Test
    @DisplayName("backward compat: stub with only evaluate() + getSupportedAssertionName() compiles and inherits getSupportedTaskName()")
    void backwardCompatGetSupportedTaskNameInherited() {
        AssertionExecutor stub = new AssertionExecutor() {
            @Override
            public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
                return null;
            }

            @Override
            public String getSupportedAssertionName() {
                return "compat-stub";
            }
        };

        assertEquals("compat-stub", stub.getSupportedTaskName());
    }
}
