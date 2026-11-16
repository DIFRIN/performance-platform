package com.performance.platform.engine.local;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.out.ExecutionPlanBuilder;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.campaign.ExecutionEngine;
import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummaries;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.TimeWindow;
import com.performance.platform.domain.event.PhaseCompleted;
import com.performance.platform.domain.event.PhaseStarted;
import com.performance.platform.domain.event.ScenarioCancelled;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.ScenarioStarted;
import com.performance.platform.domain.event.TaskCompleted;
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
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import com.performance.platform.engine.shared.DagPhaseExecutor;
import com.performance.platform.engine.shared.StepDispatchResult;
import com.performance.platform.engine.support.StubStepDispatcher;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du moteur d'execution unique en mode LOCAL.
 * <p>
 * Depuis ADR-059, le moteur ne connait plus d'executeur ni de transport : il
 * delegue chaque step a un {@link com.performance.platform.engine.shared.StepDispatcher}.
 * Les tests fournissent un {@link StubStepDispatcher} qui execute les
 * {@link TaskExecutor} enregistres, ce qui preserve l'intention des scenarios
 * sans dependre de l'agent embarque.
 */
@DisplayName("LocalExecutionEngine")
class LocalExecutionEngineTest {

    private List<Object> publishedEvents;
    private ExecutionState lastSavedState;
    private Map<String, ExecutionState> persistedStates;

    private final ExecutionRepository executionRepository = new StubExecutionRepository();
    private final ApplicationEventPublisher eventPublisher = event -> publishedEvents.add(event);
    private final Map<String, ExecutionPlan> planMap = new HashMap<>();
    private final ExecutionPlanBuilder planBuilder = scenario -> planMap.get(scenario.id().value());
    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();
    private final ExecutionLifecycleDispatcher lifecycleDispatcher = signal -> { /* no-op */ };

    private StubStepDispatcher stepDispatcher;
    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        publishedEvents = new CopyOnWriteArrayList<>();
        lastSavedState = null;
        persistedStates = new ConcurrentHashMap<>();
        planMap.clear();
        executors.clear();
        stepDispatcher = new StubStepDispatcher(this::executeRegisteredStep);
        engine = new ExecutionEngine(
                planBuilder, executionRepository,
                eventPublisher, lifecycleDispatcher,
                stepDispatcher);
    }

    private List<StepDispatchResult> executeRegisteredStep(ExecutionStep execStep,
                                                           ExecutionContext context) {
        TaskExecutor executor = executors.get(execStep.step().taskName());
        if (executor == null) {
            return StubStepDispatcher.failure(execStep,
                    "no executor for " + execStep.step().taskName());
        }
        TaskResult result = executor.execute(context, execStep.step());
        return StubStepDispatcher.result(execStep, result);
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
                Map<Phase, PhaseStatus> updated = new EnumMap<>(state.phaseStatuses());
                updated.put(phase, status);
                var newState = new ExecutionState(
                        state.id(), state.scenarioId(), state.status(),
                        updated, state.context(), state.startedAt(), Instant.now(),
                        state.plannedTaskCount(), state.tasks(), state.scenarioYaml(),
                        state.scenarioDetails());
                persistedStates.put(id.value(), newState);
                lastSavedState = newState;
            }
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
            // Les resultats dispatches sont conserves dans le contexte de l'etat.
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

        @Override
        public Map<TaskId, Map<AgentId, TaskResult>> findAllTaskResults(ExecutionId id) {
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // Stub: TaskExecutor / AssertionExecutor
    // -------------------------------------------------------------------------

    static class StubTaskExecutor implements TaskExecutor {
        private final String taskName;
        final TaskResult result;
        final AtomicInteger callCount = new AtomicInteger(0);
        private final CountDownLatch startLatch;
        private final CountDownLatch blockLatch;

        StubTaskExecutor(String taskName, TaskResult result) {
            this(taskName, result, null, null);
        }

        StubTaskExecutor(String taskName, TaskResult result,
                         CountDownLatch startLatch, CountDownLatch blockLatch) {
            this.taskName = taskName;
            this.result = result;
            this.startLatch = startLatch;
            this.blockLatch = blockLatch;
        }

        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            callCount.incrementAndGet();
            if (startLatch != null) {
                startLatch.countDown();
            }
            if (blockLatch != null) {
                try {
                    blockLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        }

        @Override
        public String getSupportedTaskName() {
            return taskName;
        }

        int getCallCount() {
            return callCount.get();
        }
    }

    static class StubAssertionExecutor implements AssertionExecutor {
        private final String assertionName;
        private final AssertionSummary result;
        private final AtomicInteger callCount = new AtomicInteger(0);

        StubAssertionExecutor(String assertionName, AssertionSummary result) {
            this.assertionName = assertionName;
            this.result = result;
        }

        @Override
        public AssertionSummary evaluate(ExecutionContext context, StepDefinition step) {
            callCount.incrementAndGet();
            return result;
        }

        @Override
        public String getSupportedAssertionName() {
            return assertionName;
        }

        int getCallCount() {
            return callCount.get();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TaskId t(String id) {
        return TaskId.of(id);
    }

    private static ScenarioId sId(String id) {
        return ScenarioId.of(id);
    }

    private static StepDefinition step(String id, String taskName, Phase phase) {
        return step(id, taskName, phase, List.of(), null);
    }

    private static StepDefinition step(String id, String taskName, Phase phase, List<TaskId> dependsOn) {
        return step(id, taskName, phase, dependsOn, null);
    }

    private static StepDefinition step(String id, String taskName, Phase phase,
                                       List<TaskId> dependsOn, RetryPolicy retryPolicy) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                dependsOn == null ? List.of() : dependsOn,
                List.of(), Duration.ofSeconds(30), retryPolicy);
    }

    private static ScenarioDefinition scenario(String id, List<StepDefinition> steps) {
        return new ScenarioDefinition(sId(id), "scenario-" + id, "1.0", List.of(), Map.of(),
                steps, null, null);
    }

    private ExecutionPlan buildPlan(ScenarioDefinition s, List<ExecutionStep> prep,
                                    List<ExecutionStep> injection, List<ExecutionStep> assertion) {
        var eid = ExecutionId.generate();
        return new ExecutionPlan(eid, s.id(), prep, injection, assertion, List.of(), List.of(),
                ExecutionContext.initial(eid, s.id()));
    }

    private ExecutionStep execStep(StepDefinition stepDef, List<TaskId> deps, int dagLevel) {
        return new ExecutionStep(stepDef, deps == null ? List.of() : deps, dagLevel, Set.of());
    }

    private void setUpPlan(ScenarioDefinition scenario, List<ExecutionStep> prep,
                           List<ExecutionStep> injection, List<ExecutionStep> assertion) {
        planMap.put(scenario.id().value(), buildPlan(scenario, prep, injection, assertion));
    }

    @SuppressWarnings("unchecked")
    private <T> T findEvent(Class<T> eventType) {
        return (T) publishedEvents.stream()
                .filter(eventType::isInstance)
                .findFirst()
                .orElse(null);
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

            ScenarioDefinition scenario = scenario("sc-1", List.of(prepStep, injStep, assertStep));

            setUpPlan(scenario,
                    List.of(execStep(prepStep, List.of(), 0)),
                    List.of(execStep(injStep, List.of(), 0)),
                    List.of(execStep(assertStep, List.of(), 0)));

            executors.put("db-init", new StubTaskExecutor("db-init",
                    TaskResult.success(t("prep-1"), "db-init", Duration.ofMillis(10), Map.of())));
            executors.put("http-get", new StubTaskExecutor("http-get",
                    TaskResult.success(t("inj-1"), "http-get", Duration.ofMillis(20), Map.of())));
            executors.put("p99-check", new StubAssertionExecutor("p99-check",
                    assertionSummary(t("assert-1"), AssertionStatus.PASSED, "OK", Duration.ofMillis(5))));

            ExecutionId executionId = engine.execute(scenario);

            assertNotNull(executionId);

            assertEquals(1, publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioStarted).count());
            assertEquals(1, publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished).count());

            ScenarioFinished finished = (ScenarioFinished) publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished).findFirst().orElseThrow();
            assertEquals(Verdict.SUCCESS, finished.verdict());

            List<Phase> startedPhases = publishedEvents.stream()
                    .filter(e -> e instanceof PhaseStarted)
                    .map(e -> ((PhaseStarted) e).phase())
                    .toList();
            assertEquals(List.of(Phase.PREPARATION, Phase.INJECTION, Phase.ASSERTION), startedPhases);

            assertNotNull(lastSavedState);
            assertEquals(3, stepDispatcher.dispatchedSteps().size());
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
            assertThrows(Exception.class, () -> new ScenarioDefinition(null, "bad", "1",
                    List.of(), Map.of(), List.of(), null, null));
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
            setUpPlan(s, List.of(execStep(prepStep, List.of(), 0)), List.of(), List.of());

            executors.put("init", new StubTaskExecutor("init",
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
            setUpPlan(s, List.of(execStep(prepStep, List.of(), 0)), List.of(), List.of());

            executors.put("init", new StubTaskExecutor("init",
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

            executors.put("load", new StubTaskExecutor("load",
                    TaskResult.failed(t("inj-1"), "load", Duration.ofMillis(5), "timeout", null)));
            executors.put("check", new StubAssertionExecutor("check",
                    assertionSummary(t("assert-1"), AssertionStatus.PASSED, "OK", Duration.ofMillis(2))));

            engine.execute(s);

            boolean assertionStarted = publishedEvents.stream()
                    .anyMatch(e -> e instanceof PhaseStarted ps && ps.phase() == Phase.ASSERTION);
            assertTrue(assertionStarted);

            assertTrue(stepDispatcher.dispatchedTaskIds().contains("assert-1"));

            ScenarioFinished finished = findEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.FAILED, finished.verdict());
        }
    }

    // -------------------------------------------------------------------------
    // Nested: submit() asynchronous execution
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("submit() asynchronous execution")
    class SubmitExecution {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void submitReturnsBeforeInjectionCompletesAndPersistsAllPlannedTasks() throws Exception {
            var injectionStarted = new CountDownLatch(1);
            var releaseInjection = new CountDownLatch(1);
            var finished = new CountDownLatch(1);
            engine = new ExecutionEngine(planBuilder, executionRepository, event -> {
                publishedEvents.add(event);
                if (event instanceof ScenarioFinished) {
                    finished.countDown();
                }
            }, lifecycleDispatcher, stepDispatcher);

            var prep = step("prep", "prepare", Phase.PREPARATION);
            var injection = new StepDefinition(t("load"), "inject", Phase.INJECTION,
                    Map.of("protocol", "http", "loadModel", "campaign"), List.of(), List.of(),
                    Duration.ofSeconds(30), RetryPolicy.defaults());
            var assertion = step("verify", "assert", Phase.ASSERTION);
            var scenario = scenario("sc-async", List.of(prep, injection, assertion));
            setUpPlan(scenario, List.of(execStep(prep, List.of(), 0)),
                    List.of(execStep(injection, List.of(), 0)),
                    List.of(execStep(assertion, List.of(), 0)));

            executors.put("prepare", new StubTaskExecutor("prepare",
                    TaskResult.success(t("prep"), "prepare", Duration.ZERO, Map.of())));
            executors.put("inject", new StubTaskExecutor("inject",
                    TaskResult.success(t("load"), "inject", Duration.ZERO, Map.of()),
                    injectionStarted, releaseInjection));
            executors.put("assert", new StubTaskExecutor("assert",
                    TaskResult.success(t("verify"), "assert", Duration.ZERO, Map.of())));

            try {
                var executionId = engine.submit(scenario);
                assertTrue(injectionStarted.await(5, TimeUnit.SECONDS));
                var persisted = executionRepository.findById(executionId).orElseThrow();
                assertEquals(3, persisted.tasks().size());
                assertEquals(scenario.name(), persisted.scenarioDetails().name());
                assertEquals(injection.parameters(),
                        persisted.tasks().get(1).configuration().get("parameters"));
                assertEquals("PT30S", persisted.tasks().get(1).configuration().get("timeout"));
                assertEquals("ONE", persisted.tasks().get(1).configuration().get("fanOut"));
                assertTrue(persisted.tasks().get(1).configuration().containsKey("retryPolicy"));
                assertEquals(PhaseStatus.RUNNING, persisted.phaseStatuses().get(Phase.INJECTION));
                assertEquals(PhaseStatus.PENDING, persisted.phaseStatuses().get(Phase.ASSERTION));
                assertEquals(1L, finished.getCount());
            } finally {
                releaseInjection.countDown();
                assertTrue(finished.await(5, TimeUnit.SECONDS));
            }
            assertFalse(stepDispatcher.dispatchedTaskIds().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Parallel execution (Virtual Threads)
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

            executors.put("task-a", new StubTaskExecutor("task-a",
                    TaskResult.success(t("A"), "task-a", Duration.ofMillis(50), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("A-start");
                    latch.countDown();
                    try {
                        assertTrue(latch.await(5, TimeUnit.SECONDS),
                                "Both steps must start concurrently");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while waiting for both steps", e);
                    }
                    executionOrder.add("A-end");
                    return super.execute(context, step);
                }
            });
            executors.put("task-b", new StubTaskExecutor("task-b",
                    TaskResult.success(t("B"), "task-b", Duration.ofMillis(50), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("B-start");
                    latch.countDown();
                    try {
                        assertTrue(latch.await(5, TimeUnit.SECONDS),
                                "Both steps must start concurrently");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while waiting for both steps", e);
                    }
                    executionOrder.add("B-end");
                    return super.execute(context, step);
                }
            });

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
            int aEnd = executionOrder.indexOf("A-end");
            int bEnd = executionOrder.indexOf("B-end");
            assertTrue(aEnd >= 0, "Step A must complete");
            assertTrue(bEnd >= 0, "Step B must complete");
            assertTrue(lastStart < Math.min(aEnd, bEnd),
                    "Both starts should happen before any end");
        }

        @Test
        @DisplayName("Steps at different dagLevel execute sequentially (level 1 waits for level 0)")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void differentDagLevel_executedSequentially() {
            List<String> executionOrder = new CopyOnWriteArrayList<>();

            StepDefinition stepA = step("A", "task-a", Phase.PREPARATION);
            StepDefinition stepB = step("B", "task-b", Phase.PREPARATION, List.of(t("A")));

            executors.put("task-a", new StubTaskExecutor("task-a",
                    TaskResult.success(t("A"), "task-a", Duration.ofMillis(10), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("A");
                    return super.execute(context, step);
                }
            });
            executors.put("task-b", new StubTaskExecutor("task-b",
                    TaskResult.success(t("B"), "task-b", Duration.ofMillis(10), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    executionOrder.add("B");
                    return super.execute(context, step);
                }
            });

            ScenarioDefinition s = scenario("sc-seq", List.of(stepA, stepB));
            setUpPlan(s,
                    List.of(execStep(stepA, List.of(), 0), execStep(stepB, List.of(t("A")), 1)),
                    List.of(), List.of());

            engine.execute(s);

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

            executors.put("task-a", new StubTaskExecutor("task-a",
                    TaskResult.failed(t("A"), "task-a", Duration.ofMillis(5), "error", null)));

            var execB = new StubTaskExecutor("task-b",
                    TaskResult.success(t("B"), "task-b", Duration.ofMillis(1), Map.of()));
            executors.put("task-b", execB);

            engine.execute(s);

            boolean bSkipped = publishedEvents.stream()
                    .filter(e -> e instanceof TaskCompleted)
                    .map(e -> (TaskCompleted) e)
                    .anyMatch(tc -> tc.taskId().value().equals("B")
                            && tc.result().status() == TaskStatus.SKIPPED);
            assertTrue(bSkipped);

            assertEquals(0, execB.getCallCount());
            assertFalse(stepDispatcher.dispatchedTaskIds().contains("B"));
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
                    List.of(), List.of(), List.of(), List.of(),
                    ExecutionContext.initial(knownId, s.id()));
            planMap.put(s.id().value(), plan);

            var blockLatch = new CountDownLatch(1);
            var executeLatch = new CountDownLatch(1);

            executors.put("init", new StubTaskExecutor("init",
                    TaskResult.success(t("prep-1"), "init", Duration.ofMillis(5), Map.of()),
                    executeLatch, blockLatch));

            var executionId = new AtomicReference<ExecutionId>();
            var execThread = new Thread(() -> executionId.set(engine.execute(s)));
            execThread.start();

            assertTrue(executeLatch.await(5, TimeUnit.SECONDS));
            Thread.sleep(100);

            engine.cancel(knownId);
            blockLatch.countDown();
            execThread.join(5000);

            assertFalse(execThread.isAlive());
            assertEquals(ExecutionStatus.CANCELLED,
                    executionRepository.findById(knownId).orElseThrow().status());
            assertNotNull(findEvent(ScenarioCancelled.class));
        }

        @Test
        @DisplayName("getStatus returns persisted status for completed execution")
        void getStatus_persisted_returnsStatus() {
            StepDefinition step = step("s1", "task", Phase.PREPARATION);
            ScenarioDefinition s = scenario("sc-status", List.of(step));
            setUpPlan(s, List.of(execStep(step, List.of(), 0)), List.of(), List.of());

            executors.put("task", new StubTaskExecutor("task",
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
    // Nested: Service steps (ADR-054)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Service steps")
    class ServiceSteps {

        @Test
        @DisplayName("STOP result of a service step lands in the execution context")
        void serviceStopResultStoredInContext() {
            var serviceDef = new StepDefinition(t("mock-server"), "mock-server", null,
                    Map.of(), List.of(), null,
                    com.performance.platform.domain.scenario.ServiceScope.INJECTION,
                    Duration.ZERO, com.performance.platform.domain.scenario.FanOut.ONE,
                    List.of(), Duration.ofSeconds(30), null);
            var inject = step("load", "gatling", Phase.INJECTION);

            // Un service n'est pas une ExecutionTask : il vit dans le plan, pas
            // dans les taches planifiees du scenario.
            ScenarioDefinition s = scenario("sc-service", List.of(inject));
            var eid = ExecutionId.generate();
            planMap.put(s.id().value(), new ExecutionPlan(eid, s.id(),
                    List.of(), List.of(execStep(inject, List.of(), 0)), List.of(),
                    List.of(execStep(serviceDef, List.of(), 0)), List.of(),
                    ExecutionContext.initial(eid, s.id())));

            executors.put("gatling", new StubTaskExecutor("gatling",
                    TaskResult.success(t("load"), "gatling", Duration.ofMillis(5), Map.of())));
            stepDispatcher.onStopService((execStep, context) -> StubStepDispatcher.success(
                    execStep, Map.of("mocks", 3)));

            engine.execute(s);

            assertEquals(1, stepDispatcher.startedServices().size());
            assertEquals(1, stepDispatcher.stoppedServices().size());
            var stored = lastSavedState.context().store().get("mock-server");
            assertNotNull(stored);
            assertEquals(3, stored.get(StubStepDispatcher.AGENT.value()).outputs().get("mocks"));
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

            executors.put("task", new StubTaskExecutor("task",
                    TaskResult.success(t("s1"), "task", Duration.ofMillis(1), Map.of())));

            engine.execute(s);

            assertNotNull(findEvent(ScenarioStarted.class));
            assertNotNull(findEvent(ScenarioFinished.class));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseStarted));
            assertTrue(publishedEvents.stream().anyMatch(e -> e instanceof PhaseCompleted));
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
            var dpe = createDagPhaseExecutor();
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(), ctx, Phase.PREPARATION,
                    new AtomicBoolean(false));

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

            var dpe = createDagPhaseExecutor();
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            executors.put("ta", new StubTaskExecutor("ta",
                    TaskResult.success(t("A"), "ta", Duration.ofMillis(5), Map.of())));
            executors.put("tb", new StubTaskExecutor("tb",
                    TaskResult.success(t("B"), "tb", Duration.ofMillis(5), Map.of())));

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION,
                    new AtomicBoolean(false));

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

            var dpe = createDagPhaseExecutor();
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var cancelled = new AtomicBoolean(false);

            executors.put("ta", new StubTaskExecutor("ta",
                    TaskResult.success(t("A"), "ta", Duration.ofMillis(5), Map.of())) {
                @Override
                public TaskResult execute(ExecutionContext context, StepDefinition step) {
                    cancelled.set(true);
                    return super.execute(context, step);
                }
            });

            var execB = new StubTaskExecutor("tb",
                    TaskResult.success(t("B"), "tb", Duration.ofMillis(5), Map.of()));
            executors.put("tb", execB);

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION, cancelled);

            assertEquals(0, execB.getCallCount());
            var aResults = result.updatedContext().store().get("A");
            assertNotNull(aResults);
            assertTrue(aResults.containsKey(StubStepDispatcher.AGENT.value()));
        }

        @Test
        @DisplayName("Assertion phase converts AssertionSummary to TaskResult")
        void assertionPhase_convertsToTaskResult() {
            StepDefinition assertStep = step("as-1", "check", Phase.ASSERTION);
            ExecutionStep es = execStep(assertStep, List.of(), 0);

            var dpe = createDagPhaseExecutor();
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var assertionSummary = assertionWithCheck(t("as-1"), AssertionStatus.PASSED,
                    "p99 < 100ms", Duration.ofMillis(5));

            executors.put("check", new StubAssertionExecutor("check", assertionSummary));

            var result = dpe.executePhase(List.of(es), ctx, Phase.ASSERTION,
                    new AtomicBoolean(false));

            var stored = result.updatedContext().store().get("as-1");
            assertNotNull(stored);
            var taskResult = stored.get(StubStepDispatcher.AGENT.value());
            assertNotNull(taskResult);
            assertEquals(TaskStatus.SUCCESS, taskResult.status());
            AssertionSummary summary = AssertionSummaries.extractSummary(taskResult);
            assertNotNull(summary);
            assertEquals(1, summary.checks().size());
            assertEquals(95.0, summary.checks().get(0).actualValue());
        }
    }

    private DagPhaseExecutor createDagPhaseExecutor() {
        return new DagPhaseExecutor(stepDispatcher, lifecycleDispatcher);
    }

    // -------------------------------------------------------------------------
    // Assertion fixtures
    // -------------------------------------------------------------------------

    /** Resume sans check detaille. */
    private static AssertionSummary assertionSummary(TaskId id, AssertionStatus status,
                                                     String description, Duration duration) {
        return new AssertionSummary(id, status, description, List.of(),
                duration, Instant.now(), AssertionFamily.CUSTOM, null, List.of(), Rollup.all());
    }

    /** Resume avec un check quantile passe (p99 95ms <= 100ms). */
    private static AssertionSummary assertionWithCheck(TaskId id, AssertionStatus status,
                                                       String description, Duration duration) {
        var metric = MetricId.of("injection.latency.p99");
        var observation = Observation.of(metric, 95.0, "ms",
                TimeWindow.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(60)), "test");
        var check = Check.passed(observation,
                Expectation.atMost(100, Severity.CRITICAL), "p99");
        return new AssertionSummary(id, status, description,
                List.of(), duration, Instant.now(),
                AssertionFamily.PERFORMANCE_THRESHOLDS, null, List.of(check), Rollup.all());
    }
}
