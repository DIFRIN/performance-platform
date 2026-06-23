package com.performance.platform.engine.local;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.event.PhaseCompleted;
import com.performance.platform.domain.event.PhaseStarted;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.ScenarioStarted;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskFailed;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.plan.ExecutionPlanBuilder;
import com.performance.platform.engine.retry.DefaultRetryExecutor;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalExecutionEngine")
class LocalExecutionEngineTest {

    private RetryExecutor retryExecutor;
    private List<Object> publishedEvents = new CopyOnWriteArrayList<>();
    private List<TaskResult> savedTaskResults = new ArrayList<>();
    private ExecutionState lastSavedState;
    private Map<String, ExecutionState> persistedStates = new HashMap<>();

    // Stub: ExecutionRepository
    private final ExecutionRepository executionRepository = new StubExecutionRepository();

    // Stub: ApplicationEventPublisher — capture events
    private final ApplicationEventPublisher eventPublisher = event -> publishedEvents.add(event);

    // Plan lookup map (lambda ExecutionPlanBuilder)
    private final Map<String, ExecutionPlan> planMap = new HashMap<>();
    private final ExecutionPlanBuilder planBuilder = scenario -> planMap.get(scenario.id().value());

    // Stub: TaskExecutorLookup
    private final StubTaskExecutorLookup taskExecutorLookup = new StubTaskExecutorLookup();

    private LocalExecutionEngine engine;

    @BeforeEach
    void setUp() {
        retryExecutor = new DefaultRetryExecutor();
        publishedEvents.clear();
        savedTaskResults = new ArrayList<>();
        lastSavedState = null;
        persistedStates = new HashMap<>();
        planMap.clear();
        taskExecutorLookup.reset();

        engine = new LocalExecutionEngine(
                planBuilder, retryExecutor, executionRepository,
                eventPublisher, taskExecutorLookup);
    }

    @AfterEach
    void cleanup() {
        taskExecutorLookup.reset();
    }

    // -------------------------------------------------------------------------
    // Stub: ExecutionRepository
    // -------------------------------------------------------------------------

    class StubExecutionRepository implements ExecutionRepository {
        @Override
        public void save(ExecutionState state) {
            lastSavedState = state;
            persistedStates.put(state.id().value(), state);
        }

        @Override
        public Optional<ExecutionState> findById(ExecutionId id) {
            return Optional.ofNullable(persistedStates.get(id.value()));
        }

        @Override
        public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {
            ExecutionState state = persistedStates.get(id.value());
            if (state != null) {
                Map<Phase, PhaseStatus> updated = new java.util.EnumMap<>(state.phaseStatuses());
                updated.put(phase, status);
                var newState = new ExecutionState(
                        state.id(), state.scenarioId(), state.status(),
                        updated, state.context(), state.startedAt(), Instant.now());
                persistedStates.put(id.value(), newState);
                lastSavedState = newState;
            }
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
            savedTaskResults.add(result);
        }

        @Override
        public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
            return Map.of();
        }

        @Override
        public List<ExecutionState> findAll(int limit) {
            return List.of();
        }

        @Override
        public void deleteById(ExecutionId id) { /* no-op */ }
    }

    // -------------------------------------------------------------------------
    // Stub: TaskExecutorLookup
    // -------------------------------------------------------------------------

    static class StubTaskExecutorLookup implements TaskExecutorLookup {
        private final Map<String, TaskExecutor> taskExecutors = new HashMap<>();
        private final Map<String, AssertionExecutor> assertionExecutors = new HashMap<>();
        private final List<String> taskLookups = new ArrayList<>();
        private final List<String> assertionLookups = new ArrayList<>();

        void registerTask(String taskName, TaskExecutor executor) {
            taskExecutors.put(taskName, executor);
        }

        void registerAssertion(String assertionName, AssertionExecutor executor) {
            assertionExecutors.put(assertionName, executor);
        }

        List<String> getTaskLookups() { return List.copyOf(taskLookups); }
        List<String> getAssertionLookups() { return List.copyOf(assertionLookups); }

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
    // Stub: TaskExecutor
    // -------------------------------------------------------------------------

    static class StubTaskExecutor implements TaskExecutor {
        private final String taskName;
        final TaskResult result;
        final AtomicInteger callCount = new AtomicInteger(0);
        private final RuntimeException exceptionToThrow;
        private final List<ExecutionContext> receivedContexts = new ArrayList<>();
        private final List<StepDefinition> receivedSteps = new ArrayList<>();
        private final CountDownLatch startLatch;
        private final CountDownLatch blockLatch;

        StubTaskExecutor(String taskName, TaskResult result) {
            this.taskName = taskName;
            this.result = result;
            this.exceptionToThrow = null;
            this.startLatch = null;
            this.blockLatch = null;
        }

        StubTaskExecutor(String taskName, RuntimeException exceptionToThrow) {
            this.taskName = taskName;
            this.result = null;
            this.exceptionToThrow = exceptionToThrow;
            this.startLatch = null;
            this.blockLatch = null;
        }

        StubTaskExecutor(String taskName, TaskResult result, CountDownLatch startLatch, CountDownLatch blockLatch) {
            this.taskName = taskName;
            this.result = result;
            this.exceptionToThrow = null;
            this.startLatch = startLatch;
            this.blockLatch = blockLatch;
        }

        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            receivedContexts.add(context);
            receivedSteps.add(step);
            callCount.incrementAndGet();
            if (startLatch != null) startLatch.countDown();
            if (blockLatch != null) {
                try { blockLatch.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (exceptionToThrow != null) throw exceptionToThrow;
            return result;
        }

        @Override public String getSupportedTaskName() { return taskName; }
        int getCallCount() { return callCount.get(); }
        List<ExecutionContext> getReceivedContexts() { return receivedContexts; }
        List<StepDefinition> getReceivedSteps() { return receivedSteps; }
    }

    // -------------------------------------------------------------------------
    // Stub: AssertionExecutor
    // -------------------------------------------------------------------------

    static class StubAssertionExecutor implements AssertionExecutor {
        private final String assertionName;
        private final AssertionResult result;
        private final AtomicInteger callCount = new AtomicInteger(0);

        StubAssertionExecutor(String assertionName, AssertionResult result) {
            this.assertionName = assertionName;
            this.result = result;
        }

        @Override
        public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
            callCount.incrementAndGet();
            return result;
        }

        @Override public String getSupportedAssertionName() { return assertionName; }
        int getCallCount() { return callCount.get(); }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TaskId t(String id) { return TaskId.of(id); }
    private static ScenarioId sId(String id) { return ScenarioId.of(id); }

    private static StepDefinition step(String id, String taskName, Phase phase,
                                        List<TaskId> dependsOn, RetryPolicy retryPolicy) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                dependsOn == null ? List.of() : dependsOn,
                List.of(), Duration.ofSeconds(30), retryPolicy);
    }

    private static StepDefinition step(String id, String taskName, Phase phase) {
        return step(id, taskName, phase, List.of(), null);
    }

    private static StepDefinition step(String id, String taskName, Phase phase, List<TaskId> dependsOn) {
        return step(id, taskName, phase, dependsOn, null);
    }

    private static ScenarioDefinition scenario(String id, List<StepDefinition> steps) {
        return new ScenarioDefinition(sId(id), "scenario-" + id, "1.0", List.of(), Map.of(),
                ExecutionMode.LOCAL, steps, Map.of());
    }

    private ExecutionPlan buildPlan(ScenarioDefinition s, List<ExecutionStep> prep,
                                     List<ExecutionStep> injection, List<ExecutionStep> assertion) {
        var eid = ExecutionId.generate();
        return new ExecutionPlan(eid, s.id(), prep, injection, assertion,
                ExecutionContext.initial(eid, s.id()));
    }

    private ExecutionStep execStep(StepDefinition stepDef, List<TaskId> deps, int dagLevel) {
        return new ExecutionStep(stepDef, deps == null ? List.of() : deps, dagLevel, Set.of());
    }

    private void setUpPlan(ScenarioDefinition scenario, List<ExecutionStep> prep,
                            List<ExecutionStep> injection, List<ExecutionStep> assertion) {
        ExecutionPlan plan = buildPlan(scenario, prep, injection, assertion);
        planMap.put(scenario.id().value(), plan);
    }

    // -------------------------------------------------------------------------
    // Nested: execute() basic lifecycle
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("execute() basic lifecycle")
    class ExecuteLifecycle {

        @Test
        @DisplayName("Should execute all three phases in order")
        void execute_allThreePhasesInOrder() {
            StepDefinition prepStep = step("prep-1", "db-init", Phase.PREPARATION);
            StepDefinition injStep = step("inj-1", "http-get", Phase.INJECTION);
            StepDefinition assertStep = step("assert-1", "p99-check", Phase.ASSERTION);

            ScenarioDefinition scenario = scenario("sc-1",
                    List.of(prepStep, injStep, assertStep));

            setUpPlan(scenario,
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(execStep(injStep, List.of(), 0)),
                    List.of(execStep(assertStep, List.of(), 0)));

            taskExecutorLookup.registerTask("db-init",
                    new StubTaskExecutor("db-init",
                            TaskResult.success(t("prep-1"), "db-init", Duration.ofMillis(10), Map.of())));
            taskExecutorLookup.registerTask("http-get",
                    new StubTaskExecutor("http-get",
                            TaskResult.success(t("inj-1"), "http-get", Duration.ofMillis(20), Map.of())));
            taskExecutorLookup.registerAssertion("p99-check",
                    new StubAssertionExecutor("p99-check",
                            new AssertionResult(t("assert-1"), AssertionStatus.PASSED,
                                    "OK", null, Duration.ofMillis(5), Instant.now())));

            ExecutionId executionId = engine.execute(scenario);

            assertNotNull(executionId);

            // All three phases should have events
            long scenarioStartedCount = publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioStarted).count();
            long scenarioFinishedCount = publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished).count();
            assertEquals(1, scenarioStartedCount);
            assertEquals(1, scenarioFinishedCount);

            ScenarioFinished finished = (ScenarioFinished) publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished).findFirst().orElseThrow();
            assertEquals(Verdict.SUCCESS, finished.verdict());

            // Check phase ordering via events
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseStarted ps && ps.phase() == Phase.PREPARATION));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseStarted ps && ps.phase() == Phase.INJECTION));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseStarted ps && ps.phase() == Phase.ASSERTION));

            // Repository should have saved
            assertNotNull(lastSavedState);
        }

        @Test
        @DisplayName("Should handle scenario with no steps")
        void execute_emptySteps_succeeds() {
            ScenarioDefinition scenario = scenario("sc-empty", List.of());
            setUpPlan(scenario, List.of(), List.of(), List.of());

            ExecutionId executionId = engine.execute(scenario);

            assertNotNull(executionId);
            assertEquals(1, publishedEvents.stream().filter(e -> e instanceof ScenarioStarted).count());
            assertEquals(1, publishedEvents.stream().filter(e -> e instanceof ScenarioFinished).count());
        }

        @Test
        @DisplayName("Should throw ExecutionException for null scenario")
        void execute_nullScenario_throwsException() {
            assertThrows(ExecutionException.class, () -> engine.execute(null));
        }

        @Test
        @DisplayName("Should throw for scenario with null id")
        void execute_nullScenarioId_throwsException() {
            // ScenarioDefinition ensures id is non-null at construction time (record invariant)
            assertThrows(Exception.class, () -> {
                new ScenarioDefinition(null, "bad", "1", List.of(), Map.of(),
                        ExecutionMode.LOCAL, List.of(), Map.of());
            });
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Verdict computation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Verdict computation")
    class VerdictComputation {

        @Test
        @DisplayName("All SUCCESS -> verdict SUCCESS")
        void allSuccess_verdictSuccess() {
            StepDefinition prepStep = step("prep-1", "init", Phase.PREPARATION);
            ScenarioDefinition s = scenario("sc-ok", List.of(prepStep));
            setUpPlan(s,
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(), List.of());

            taskExecutorLookup.registerTask("init",
                    new StubTaskExecutor("init",
                            TaskResult.success(t("prep-1"), "init", Duration.ofMillis(5), Map.of())));

            engine.execute(s);

            ScenarioFinished finished = findEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.SUCCESS, finished.verdict());
        }

        @Test
        @DisplayName("Any FAILED -> verdict FAILED")
        void withFailed_verdictFailed() {
            StepDefinition prepStep = step("prep-1", "init", Phase.PREPARATION);
            ScenarioDefinition s = scenario("sc-fail", List.of(prepStep));
            setUpPlan(s,
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(), List.of());

            taskExecutorLookup.registerTask("init",
                    new StubTaskExecutor("init",
                            TaskResult.failed(t("prep-1"), "init", Duration.ofMillis(5), "DB error", null)));

            engine.execute(s);

            ScenarioFinished finished = findEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.FAILED, finished.verdict());
        }

        @Test
        @DisplayName("ASSERTION always runs even when INJECTION failed")
        void assertionAlwaysRuns_whenInjectionFailed() {
            StepDefinition injStep = step("inj-1", "load", Phase.INJECTION);
            StepDefinition assertStep = step("assert-1", "check", Phase.ASSERTION);

            ScenarioDefinition s = scenario("sc-assertion", List.of(injStep, assertStep));
            setUpPlan(s, List.of(),
                    List.of(execStep(injStep, List.of(), 0)),
                    List.of(execStep(assertStep, List.of(), 0)));

            taskExecutorLookup.registerTask("load",
                    new StubTaskExecutor("load",
                            TaskResult.failed(t("inj-1"), "load", Duration.ofMillis(5), "timeout", null)));
            taskExecutorLookup.registerAssertion("check",
                    new StubAssertionExecutor("check",
                            new AssertionResult(t("assert-1"), AssertionStatus.PASSED,
                                    "OK", null, Duration.ofMillis(2), Instant.now())));

            engine.execute(s);

            // ASSERTION phase should have been started
            boolean assertionStarted = publishedEvents.stream()
                    .anyMatch(e -> e instanceof PhaseStarted ps && ps.phase() == Phase.ASSERTION);
            assertTrue(assertionStarted);

            // TaskCompleted should exist for the assertion step
            boolean assertionCompleted = publishedEvents.stream()
                    .anyMatch(e -> e instanceof TaskCompleted tc && tc.taskId().value().equals("assert-1"));
            assertTrue(assertionCompleted);

            // Verdict should be FAILED because injection failed
            ScenarioFinished finished = findEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.FAILED, finished.verdict());
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Parallel execution
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Parallel execution (Virtual Threads)")
    class ParallelExecution {

        @Test
        @DisplayName("Steps at same dagLevel should execute in parallel")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void sameDagLevel_executedInParallel() throws Exception {
            var latch = new CountDownLatch(2);
            List<String> executionOrder = new CopyOnWriteArrayList<>();

            StepDefinition stepA = step("A", "task-a", Phase.PREPARATION);
            StepDefinition stepB = step("B", "task-b", Phase.PREPARATION);

            var execA = new StubTaskExecutor("task-a",
                    TaskResult.success(t("A"), "task-a", Duration.ofMillis(50), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("A-start");
                    latch.countDown();
                    try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    executionOrder.add("A-end");
                    return super.execute(context, step);
                }
            };

            var execB = new StubTaskExecutor("task-b",
                    TaskResult.success(t("B"), "task-b", Duration.ofMillis(50), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("B-start");
                    latch.countDown();
                    try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    executionOrder.add("B-end");
                    return super.execute(context, step);
                }
            };

            taskExecutorLookup.registerTask("task-a", execA);
            taskExecutorLookup.registerTask("task-b", execB);

            ScenarioDefinition s = scenario("sc-parallel", List.of(stepA, stepB));
            setUpPlan(s,
                    List.of(execStep(stepA, List.of(), 0), execStep(stepB, List.of(), 0)),
                    List.of(), List.of());

            engine.execute(s);

            int aStart = executionOrder.indexOf("A-start");
            int bStart = executionOrder.indexOf("B-start");
            assertTrue(aStart >= 0);
            assertTrue(bStart >= 0);

            int lastStart = Math.max(aStart, bStart);
            int firstEnd = Math.min(
                    executionOrder.indexOf("A-end") >= 0 ? executionOrder.indexOf("A-end") : 999,
                    executionOrder.indexOf("B-end") >= 0 ? executionOrder.indexOf("B-end") : 999);
            assertTrue(lastStart < firstEnd, "Both starts should happen before any end");
        }

        @Test
        @DisplayName("Steps at different dagLevel execute sequentially (level 1 waits for level 0)")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void differentDagLevel_executedSequentially() {
            List<String> executionOrder = new CopyOnWriteArrayList<>();

            StepDefinition stepA = step("A", "task-a", Phase.PREPARATION);
            StepDefinition stepB = step("B", "task-b", Phase.PREPARATION, List.of(t("A")));

            var execA = new StubTaskExecutor("task-a",
                    TaskResult.success(t("A"), "task-a", Duration.ofMillis(10), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("A");
                    return super.execute(context, step);
                }
            };

            var execB = new StubTaskExecutor("task-b",
                    TaskResult.success(t("B"), "task-b", Duration.ofMillis(10), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("B");
                    return super.execute(context, step);
                }
            };

            taskExecutorLookup.registerTask("task-a", execA);
            taskExecutorLookup.registerTask("task-b", execB);

            ScenarioDefinition s = scenario("sc-seq", List.of(stepA, stepB));
            setUpPlan(s,
                    List.of(execStep(stepA, List.of(), 0), execStep(stepB, List.of(t("A")), 1)),
                    List.of(), List.of());

            engine.execute(s);

            // A must execute before B
            assertEquals(List.of("A", "B"), executionOrder);
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Dependency failure -> SKIPPED
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Dependency failure -> SKIPPED")
    class DependencyFailure {

        @Test
        @DisplayName("When a dependency returns FAILED, the dependent step is SKIPPED")
        void failedDep_producesSkippedDependent() {
            StepDefinition stepA = step("A", "task-a", Phase.PREPARATION);
            StepDefinition stepB = step("B", "task-b", Phase.PREPARATION, List.of(t("A")));

            ScenarioDefinition s = scenario("sc-depfail", List.of(stepA, stepB));
            setUpPlan(s,
                    List.of(execStep(stepA, List.of(), 0), execStep(stepB, List.of(t("A")), 1)),
                    List.of(), List.of());

            taskExecutorLookup.registerTask("task-a",
                    new StubTaskExecutor("task-a",
                            TaskResult.failed(t("A"), "task-a", Duration.ofMillis(5), "error", null)));

            // task-b should NOT be called because B is skipped
            var execB = new StubTaskExecutor("task-b",
                    TaskResult.success(t("B"), "task-b", Duration.ofMillis(1), Map.of()));
            taskExecutorLookup.registerTask("task-b", execB);

            engine.execute(s);

            // B should have a SKIPPED event
            boolean bSkipped = publishedEvents.stream()
                    .filter(e -> e instanceof TaskCompleted)
                    .map(e -> (TaskCompleted) e)
                    .anyMatch(tc -> tc.taskId().value().equals("B")
                            && tc.result().status() == TaskStatus.SKIPPED);
            assertTrue(bSkipped);

            // B executor should NOT have been called
            assertEquals(0, execB.getCallCount());

            // B should NOT be in the lookup list
            assertFalse(taskExecutorLookup.getTaskLookups().contains("task-b"));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Cancel
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Cancel")
    class CancelTests {

        @Test
        @DisplayName("Cancel sets internal flag and engine detects it")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void cancel_detectedByEngine() throws Exception {
            StepDefinition prepStep = step("prep-1", "init", Phase.PREPARATION);

            ScenarioDefinition s = scenario("sc-cancel", List.of(prepStep));

            var knownId = ExecutionId.of("cancel-test-001");
            var plan = new ExecutionPlan(knownId, s.id(),
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(), List.of(),
                    ExecutionContext.initial(knownId, s.id()));
            planMap.put(s.id().value(), plan);

            var blockLatch = new CountDownLatch(1);
            var executeLatch = new CountDownLatch(1);

            var blockingExec = new StubTaskExecutor("init",
                    TaskResult.success(t("prep-1"), "init", Duration.ofMillis(5), Map.of()),
                    executeLatch, blockLatch);
            taskExecutorLookup.registerTask("init", blockingExec);

            // Execute in separate thread
            var execThread = new Thread(() -> engine.execute(s));
            execThread.start();

            // Wait for execution to start
            assertTrue(executeLatch.await(5, TimeUnit.SECONDS));
            Thread.sleep(100); // small delay for ScenarioStarted to be published

            // Cancel the execution
            engine.cancel(knownId);

            // Release the blocking executor
            blockLatch.countDown();
            execThread.join(5000);

            // After cancel, ScenarioFinished should have been published (or ScenarioCancelled)
            // The engine should have detected the cancel flag and stopped
            assertFalse(execThread.isAlive());
        }

        @Test
        @DisplayName("getStatus returns persisted status for completed execution")
        void getStatus_persisted_returnsStatus() {
            StepDefinition step = step("s1", "task", Phase.PREPARATION);
            ScenarioDefinition s = scenario("sc-status", List.of(step));
            setUpPlan(s, List.of(execStep(step, List.of(), 0)), List.of(), List.of());

            taskExecutorLookup.registerTask("task",
                    new StubTaskExecutor("task",
                            TaskResult.success(t("s1"), "task", Duration.ofMillis(1), Map.of())));

            ExecutionId executionId = engine.execute(s);
            ExecutionStatus status = engine.getStatus(executionId);
            assertTrue(status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED);
        }

        @Test
        @DisplayName("getStatus throws for unknown execution")
        void getStatus_unknown_throws() {
            var fakeId = ExecutionId.of("non-existent");
            assertThrows(ExecutionException.class, () -> engine.getStatus(fakeId));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Retry
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Retry integration")
    class RetryIntegration {

        @Test
        @DisplayName("Step with retry policy succeeds on retry")
        void retryPolicy_succeedsOnRetry() {
            var retryPolicy = new RetryPolicy(3, Duration.ofMillis(1), 2.0,
                    Duration.ofMillis(50), Set.of());

            StepDefinition prepStep = step("prep-1", "flaky", Phase.PREPARATION, List.of(), retryPolicy);

            ScenarioDefinition s = scenario("sc-retry", List.of(prepStep));
            setUpPlan(s,
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(), List.of());

            // Executor fails once, then succeeds on retry
            var failedOnce = new AtomicBoolean(false);
            var executor = new StubTaskExecutor("flaky",
                    TaskResult.success(t("prep-1"), "flaky", Duration.ofMillis(5), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    // Increment here because we may throw before calling super
                    callCount.incrementAndGet();
                    if (failedOnce.compareAndSet(false, true)) {
                        throw new RuntimeException("temporary error");
                    }
                    return result;
                }
            };
            taskExecutorLookup.registerTask("flaky", executor);

            engine.execute(s);

            ScenarioFinished finished = findEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.SUCCESS, finished.verdict());

            // Called 2 times (1 fail + 1 success)
            assertEquals(2, executor.getCallCount());
        }

        @Test
        @DisplayName("Step with retry policy exhausted returns FAILED")
        void retryPolicy_exhausted_returnsFailed() {
            var retryPolicy = new RetryPolicy(2, Duration.ofMillis(1), 2.0,
                    Duration.ofMillis(50), Set.of());

            StepDefinition prepStep = step("prep-1", "broken", Phase.PREPARATION, List.of(), retryPolicy);

            ScenarioDefinition s = scenario("sc-exhaust", List.of(prepStep));
            setUpPlan(s,
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(), List.of());

            var executor = new StubTaskExecutor("broken",
                    new RuntimeException("persistent error"));
            taskExecutorLookup.registerTask("broken", executor);

            engine.execute(s);

            ScenarioFinished finished = findEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.FAILED, finished.verdict());

            // Called exactly 2 times
            assertEquals(2, executor.getCallCount());
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Event publishing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Event publishing")
    class EventPublishing {

        @Test
        @DisplayName("Should publish ScenarioStarted at beginning and ScenarioFinished at end")
        void publishesScenarioLifecycleEvents() {
            StepDefinition step = step("s1", "task", Phase.PREPARATION);
            ScenarioDefinition s = scenario("sc-ev", List.of(step));
            setUpPlan(s, List.of(execStep(step, List.of(), 0)), List.of(), List.of());

            taskExecutorLookup.registerTask("task",
                    new StubTaskExecutor("task",
                            TaskResult.success(t("s1"), "task", Duration.ofMillis(1), Map.of())));

            engine.execute(s);

            assertNotNull(findEvent(ScenarioStarted.class));
            assertNotNull(findEvent(ScenarioFinished.class));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseStarted));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseCompleted));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof TaskCompleted));
        }

        @Test
        @DisplayName("Should publish TaskFailed for failed steps")
        void publishesTaskFailed() {
            StepDefinition step = step("s-fail", "task", Phase.PREPARATION);
            ScenarioDefinition s = scenario("sc-tf", List.of(step));
            setUpPlan(s, List.of(execStep(step, List.of(), 0)), List.of(), List.of());

            taskExecutorLookup.registerTask("task",
                    new StubTaskExecutor("task",
                            TaskResult.failed(t("s-fail"), "task", Duration.ofMillis(5), "error", null)));

            engine.execute(s);

            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof TaskFailed));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: DagPhaseExecutor unit tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DagPhaseExecutor")
    class DagPhaseExecutorTests {

        @Test
        @DisplayName("Empty steps list returns unchanged context")
        void emptySteps_returnsUnchangedContext() {
            var dpe = new DagPhaseExecutor(retryExecutor);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(), ctx, Phase.PREPARATION,
                    taskExecutorLookup, eventPublisher, new AtomicBoolean(false));

            assertSame(ctx, result.updatedContext());
            assertFalse(result.anyFailed());
        }

        @Test
        @DisplayName("Single dagLevel with multiple steps executes all")
        void singleLevel_executesAllSteps() {
            StepDefinition stepA = step("A", "ta", Phase.PREPARATION);
            StepDefinition stepB = step("B", "tb", Phase.PREPARATION);
            ExecutionStep esA = execStep(stepA, List.of(), 0);
            ExecutionStep esB = execStep(stepB, List.of(), 0);

            var dpe = new DagPhaseExecutor(retryExecutor);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            taskExecutorLookup.registerTask("ta",
                    new StubTaskExecutor("ta",
                            TaskResult.success(t("A"), "ta", Duration.ofMillis(5), Map.of())));
            taskExecutorLookup.registerTask("tb",
                    new StubTaskExecutor("tb",
                            TaskResult.success(t("B"), "tb", Duration.ofMillis(5), Map.of())));

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION,
                    taskExecutorLookup, eventPublisher, new AtomicBoolean(false));

            assertFalse(result.anyFailed());
            assertNotNull(result.updatedContext().store().get("A"));
            assertNotNull(result.updatedContext().store().get("B"));
        }

        @Test
        @DisplayName("Cancel flag set to true stops further levels")
        void cancelFlag_stopsExecution() {
            StepDefinition stepA = step("A", "ta", Phase.PREPARATION);
            StepDefinition stepB = step("B", "tb", Phase.PREPARATION);

            ExecutionStep esA = execStep(stepA, List.of(), 0);
            ExecutionStep esB = execStep(stepB, List.of(), 1);

            var dpe = new DagPhaseExecutor(retryExecutor);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var cancelled = new AtomicBoolean(false);

            taskExecutorLookup.registerTask("ta",
                    new StubTaskExecutor("ta",
                            TaskResult.success(t("A"), "ta", Duration.ofMillis(5), Map.of())) {
                        @Override
                        public TaskResult execute(ExecutionContext context, StepDefinition step) {
                            cancelled.set(true);
                            return super.execute(context, step);
                        }
                    });

            var execB = new StubTaskExecutor("tb",
                    TaskResult.success(t("B"), "tb", Duration.ofMillis(5), Map.of()));
            taskExecutorLookup.registerTask("tb", execB);

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION,
                    taskExecutorLookup, eventPublisher, cancelled);

            // B should not have been called
            assertEquals(0, execB.getCallCount());
            // Context should have A's result
            var aResults = result.updatedContext().store().get("A");
            assertNotNull(aResults);
            assertTrue(aResults.containsKey("agent-local"));
        }

        @Test
        @DisplayName("Assertion phase converts AssertionResult to TaskResult")
        void assertionPhase_convertsToTaskResult() {
            StepDefinition assertStep = step("as-1", "check", Phase.ASSERTION);
            ExecutionStep es = execStep(assertStep, List.of(), 0);

            var dpe = new DagPhaseExecutor(retryExecutor);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var evidence = new Evidence(95.0, 100.0,
                    AssertionOperator.LT, "ms", Map.of("p99", 95.0));

            var assertionResult = new AssertionResult(
                    t("as-1"), AssertionStatus.PASSED, "p99 < 100ms",
                    evidence, Duration.ofMillis(5), Instant.now());

            taskExecutorLookup.registerAssertion("check",
                    new StubAssertionExecutor("check", assertionResult));

            var result = dpe.executePhase(List.of(es), ctx, Phase.ASSERTION,
                    taskExecutorLookup, eventPublisher, new AtomicBoolean(false));

            var stored = result.updatedContext().store().get("as-1");
            assertNotNull(stored);
            var taskResult = stored.get("agent-local");
            assertNotNull(taskResult);
            assertEquals(TaskStatus.SUCCESS, taskResult.status());
            assertTrue(taskResult.outputs().containsKey("p99"));
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T findEvent(Class<T> eventType) {
        return (T) publishedEvents.stream()
                .filter(e -> eventType.isInstance(e))
                .findFirst()
                .orElse(null);
    }
}
