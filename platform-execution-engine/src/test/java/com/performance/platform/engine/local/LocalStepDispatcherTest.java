package com.performance.platform.engine.local;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.retry.DefaultRetryExecutor;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalStepDispatcher")
class LocalStepDispatcherTest {

    private StubTaskExecutorLookup lookup;
    private RetryExecutor retryExecutor;
    private LocalStepDispatcher dispatcher;

    private static TaskId t(String id) { return TaskId.of(id); }
    private static ScenarioId sId(String id) { return ScenarioId.of(id); }

    private static StepDefinition step(String id, String taskName, Phase phase) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                List.of(), List.of(), Duration.ofSeconds(30), null);
    }

    private static StepDefinition stepWithRetry(String id, String taskName, Phase phase, RetryPolicy retry) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                List.of(), List.of(), Duration.ofSeconds(30), retry);
    }

    private static ExecutionStep execStep(StepDefinition stepDef) {
        return new ExecutionStep(stepDef, List.of(), 0, Set.of());
    }

    @BeforeEach
    void setUp() {
        lookup = new StubTaskExecutorLookup();
        retryExecutor = new DefaultRetryExecutor();
        dispatcher = new LocalStepDispatcher(lookup, retryExecutor);
    }

    // -------------------------------------------------------------------------
    // Stub Lookup
    // -------------------------------------------------------------------------

    static class StubTaskExecutorLookup implements TaskExecutorLookup {
        private final Map<String, TaskExecutor> taskExecutors = new HashMap<>();
        private final Map<String, AssertionExecutor> assertionExecutors = new HashMap<>();
        final List<String> taskLookups = new ArrayList<>();
        final List<String> assertionLookups = new ArrayList<>();

        void registerTask(String taskName, TaskExecutor executor) {
            taskExecutors.put(taskName, executor);
        }

        void registerAssertion(String assertionName, AssertionExecutor executor) {
            assertionExecutors.put(assertionName, executor);
        }

        void reset() {
            taskExecutors.clear();
            assertionExecutors.clear();
            taskLookups.clear();
            assertionLookups.clear();
        }

        @Override
        public TaskExecutor findTaskExecutor(String taskName) {
            taskLookups.add(taskName);
            return taskExecutors.get(taskName);
        }

        @Override
        public AssertionExecutor findAssertionExecutor(String assertionName) {
            assertionLookups.add(assertionName);
            return assertionExecutors.get(assertionName);
        }
    }

    // -------------------------------------------------------------------------
    // Stub TaskExecutor
    // -------------------------------------------------------------------------

    static class StubTaskExecutor implements TaskExecutor {
        final String taskName;
        final TaskResult result;
        private final RuntimeException exceptionToThrow;
        final AtomicInteger callCount = new AtomicInteger(0);

        StubTaskExecutor(String taskName, TaskResult result) {
            this.taskName = taskName;
            this.result = result;
            this.exceptionToThrow = null;
        }

        StubTaskExecutor(String taskName, RuntimeException exceptionToThrow) {
            this.taskName = taskName;
            this.result = null;
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            callCount.incrementAndGet();
            if (exceptionToThrow != null) throw exceptionToThrow;
            return result;
        }

        @Override
        public String getSupportedTaskName() { return taskName; }
    }

    // -------------------------------------------------------------------------
    // Stub AssertionExecutor
    // -------------------------------------------------------------------------

    static class StubAssertionExecutor implements AssertionExecutor {
        private final String name;
        private final AssertionResult result;
        final AtomicInteger callCount = new AtomicInteger(0);

        StubAssertionExecutor(String name, AssertionResult result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
            callCount.incrementAndGet();
            return result;
        }

        @Override
        public String getSupportedAssertionName() { return name; }
    }

    // -------------------------------------------------------------------------
    // Nested: dispatch to TaskExecutor
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("dispatch → TaskExecutor (PREPARATION / INJECTION)")
    class DispatchToTaskExecutor {

        @Test
        @DisplayName("Should return SUCCESS when executor succeeds")
        void taskExecutor_success() {
            var stepDef = step("s1", "my-task", Phase.PREPARATION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            lookup.registerTask("my-task", new StubTaskExecutor("my-task",
                    TaskResult.success(t("s1"), "my-task", Duration.ofMillis(10), Map.of("k", "v"))));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.PREPARATION);

            assertEquals(TaskStatus.SUCCESS, result.status());
            assertEquals(t("s1"), result.taskId());
            assertEquals("my-task", result.taskName());
            assertTrue(result.outputs().containsKey("k"));
        }

        @Test
        @DisplayName("Should return FAILED when executor returns failure")
        void taskExecutor_failure() {
            var stepDef = step("s1", "my-task", Phase.PREPARATION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            lookup.registerTask("my-task", new StubTaskExecutor("my-task",
                    TaskResult.failed(t("s1"), "my-task", Duration.ofMillis(10), "DB error", null)));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.PREPARATION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertEquals("DB error", result.errorMessage());
        }

        @Test
        @DisplayName("Should return FAILED when executor throws")
        void taskExecutor_throwsException() {
            var stepDef = step("s1", "my-task", Phase.PREPARATION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            lookup.registerTask("my-task", new StubTaskExecutor("my-task",
                    new RuntimeException("BOOM")));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.PREPARATION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertTrue(result.errorMessage().contains("BOOM"));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: dispatch → unknown executor
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("dispatch → unknown executor")
    class UnknownExecutor {

        @Test
        @DisplayName("Should return FAILED when no TaskExecutor found")
        void unknownTaskExecutor_returnsFailed() {
            var stepDef = step("s1", "unknown-task", Phase.PREPARATION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.PREPARATION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertTrue(result.errorMessage().contains("No TaskExecutor found"));
            assertTrue(result.errorMessage().contains("unknown-task"));
        }

        @Test
        @DisplayName("Should return FAILED when no AssertionExecutor found")
        void unknownAssertionExecutor_returnsFailed() {
            var stepDef = step("s1", "unknown-assertion", Phase.ASSERTION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.ASSERTION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertTrue(result.errorMessage().contains("No AssertionExecutor found"));
            assertTrue(result.errorMessage().contains("unknown-assertion"));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: dispatch to AssertionExecutor
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("dispatch → AssertionExecutor (ASSERTION)")
    class DispatchToAssertionExecutor {

        @Test
        @DisplayName("Should convert PASSED assertion to SUCCESS TaskResult")
        void assertion_passed() {
            var stepDef = step("as-1", "check-p99", Phase.ASSERTION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            var evidence = new Evidence(95.0, 100.0,
                    com.performance.platform.domain.assertion.AssertionOperator.LT,
                    "ms", Map.of("p99", 95.0));

            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.PASSED, "p99 < 100ms",
                    evidence, Duration.ofMillis(5), Instant.now());

            lookup.registerAssertion("check-p99", new StubAssertionExecutor("check-p99", assertionResult));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.ASSERTION);

            assertEquals(TaskStatus.SUCCESS, result.status());
            assertTrue(result.outputs().containsKey("p99"));
        }

        @Test
        @DisplayName("Should convert FAILED assertion to FAILED TaskResult")
        void assertion_failed() {
            var stepDef = step("as-1", "check", Phase.ASSERTION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.FAILED, "p99 > 100ms",
                    null, Duration.ofMillis(5), Instant.now());

            lookup.registerAssertion("check", new StubAssertionExecutor("check", assertionResult));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.ASSERTION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertTrue(result.errorMessage().contains("p99 > 100ms"));
        }

        @Test
        @DisplayName("Should convert SKIPPED assertion to SKIPPED TaskResult")
        void assertion_skipped() {
            var stepDef = step("as-1", "check", Phase.ASSERTION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.SKIPPED, "No data",
                    null, Duration.ofMillis(5), Instant.now());

            lookup.registerAssertion("check", new StubAssertionExecutor("check", assertionResult));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.ASSERTION);

            assertEquals(TaskStatus.SKIPPED, result.status());
            assertEquals("No data", result.errorMessage());
        }

        @Test
        @DisplayName("Should convert ERROR assertion to FAILED TaskResult")
        void assertion_error() {
            var stepDef = step("as-1", "check", Phase.ASSERTION);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.ERROR, "Evaluation error",
                    null, Duration.ofMillis(5), Instant.now());

            lookup.registerAssertion("check", new StubAssertionExecutor("check", assertionResult));

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.ASSERTION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertTrue(result.errorMessage().contains("Evaluation error"));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Retry
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Retry")
    class RetryTests {

        @Test
        @DisplayName("Should retry and succeed after transient failure")
        void retry_succeedsOnRetry() {
            var retryPolicy = new RetryPolicy(3, Duration.ofMillis(1), 2.0,
                    Duration.ofMillis(50), Set.of());
            var stepDef = stepWithRetry("s1", "flaky", Phase.PREPARATION, retryPolicy);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            var failedOnce = new AtomicInteger(0);
            var executor = new StubTaskExecutor("flaky",
                    TaskResult.success(t("s1"), "flaky", Duration.ofMillis(1), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    callCount.incrementAndGet();
                    if (failedOnce.incrementAndGet() == 1) {
                        throw new RuntimeException("temporary error");
                    }
                    return result;
                }
            };

            lookup.registerTask("flaky", executor);

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.PREPARATION);

            assertEquals(TaskStatus.SUCCESS, result.status());
            assertEquals(2, executor.callCount.get());
        }

        @Test
        @DisplayName("Should return FAILED when retries exhausted")
        void retry_exhausted() {
            var retryPolicy = new RetryPolicy(2, Duration.ofMillis(1), 2.0,
                    Duration.ofMillis(50), Set.of());
            var stepDef = stepWithRetry("s1", "broken", Phase.PREPARATION, retryPolicy);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("sc1"));

            var executor = new StubTaskExecutor("broken",
                    new RuntimeException("persistent error"));
            lookup.registerTask("broken", executor);

            TaskResult result = dispatcher.dispatch(execStep(stepDef), ctx, Phase.PREPARATION);

            assertEquals(TaskStatus.FAILED, result.status());
            assertTrue(result.errorMessage().contains("persistent error"));
            assertEquals(2, executor.callCount.get());
        }
    }

    // -------------------------------------------------------------------------
    // Nested: assertionResultToTaskResult
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("assertionResultToTaskResult")
    class AssertionResultConversion {

        @Test
        @DisplayName("PASSED → TaskResult SUCCESS with evidence outputs")
        void passed_toSuccess() {
            var evidence = new Evidence(95.0, 100.0,
                    com.performance.platform.domain.assertion.AssertionOperator.LT,
                    "ms", Map.of("p99", 95.0, "p95", 50.0));
            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.PASSED, "OK", evidence,
                    Duration.ofMillis(10), Instant.now());

            TaskResult result = LocalStepDispatcher.assertionResultToTaskResult(
                    assertionResult, step("as-1", "check", Phase.ASSERTION));

            assertEquals(TaskStatus.SUCCESS, result.status());
            assertEquals(95.0, result.outputs().get("p99"));
            assertEquals(50.0, result.outputs().get("p95"));
            assertEquals(Duration.ofMillis(10), result.duration());
        }

        @Test
        @DisplayName("FAILED → TaskResult FAILED with description as error")
        void failed_toFailed() {
            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.FAILED, "p99 > 100ms",
                    null, Duration.ofMillis(5), Instant.now());

            TaskResult result = LocalStepDispatcher.assertionResultToTaskResult(
                    assertionResult, step("as-1", "check", Phase.ASSERTION));

            assertEquals(TaskStatus.FAILED, result.status());
            assertEquals("p99 > 100ms", result.errorMessage());
        }

        @Test
        @DisplayName("SKIPPED → TaskResult SKIPPED")
        void skipped_toSkipped() {
            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.SKIPPED, "No data available",
                    null, Duration.ofMillis(1), Instant.now());

            TaskResult result = LocalStepDispatcher.assertionResultToTaskResult(
                    assertionResult, step("as-1", "check", Phase.ASSERTION));

            assertEquals(TaskStatus.SKIPPED, result.status());
            assertEquals("No data available", result.errorMessage());
        }

        @Test
        @DisplayName("ERROR → TaskResult FAILED with description as error")
        void error_toFailed() {
            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.ERROR, "NPE during evaluation",
                    null, Duration.ofMillis(2), Instant.now());

            TaskResult result = LocalStepDispatcher.assertionResultToTaskResult(
                    assertionResult, step("as-1", "check", Phase.ASSERTION));

            assertEquals(TaskStatus.FAILED, result.status());
            assertEquals("NPE during evaluation", result.errorMessage());
        }

        @Test
        @DisplayName("Null evidence → empty outputs map")
        void nullEvidence_emptyOutputs() {
            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.PASSED, "OK", null,
                    Duration.ofMillis(5), Instant.now());

            TaskResult result = LocalStepDispatcher.assertionResultToTaskResult(
                    assertionResult, step("as-1", "check", Phase.ASSERTION));

            assertEquals(TaskStatus.SUCCESS, result.status());
            assertTrue(result.outputs().isEmpty());
        }
    }
}
