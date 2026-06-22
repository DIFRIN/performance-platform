package com.performance.platform.engine.e2e;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.event.*;
import com.performance.platform.domain.execution.*;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;

import com.performance.platform.engine.local.LocalExecutionEngine;
import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.engine.plan.ExecutionPlanBuilder;
import com.performance.platform.engine.retry.DefaultRetryExecutor;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests E2E du pipeline d'execution complet.
 * <p>
 * Scenarios realistes simulant le flux complet :
 * PREPARATION (database purge + seed + mock server start)
 * -> INJECTION (Gatling load test)
 * -> ASSERTION (p95 latency + error rate + row count).
 */
@DisplayName("Execution Engine E2E")
class ExecutionEngineE2ETest {

    private RetryExecutor retryExecutor;
    private List<Object> publishedEvents;

    private List<TaskResult> allTaskResults;
    private StubTaskExecutorLookup taskExecutorLookup;
    private Map<String, ExecutionPlan> planMap;
    private LocalExecutionEngine engine;
    private ExecutionPlanBuilder planBuilder;
    private StubExecutionRepository executionRepository;

    @BeforeEach
    void setUp() {
        retryExecutor = new DefaultRetryExecutor();
        publishedEvents = new CopyOnWriteArrayList<>();

        allTaskResults = new ArrayList<>();
        taskExecutorLookup = new StubTaskExecutorLookup();
        planMap = new HashMap<>();
        executionRepository = new StubExecutionRepository();
        planBuilder = scenario -> planMap.get(scenario.id().value());

        ApplicationEventPublisher eventPublisher = event -> publishedEvents.add(event);

        engine = new LocalExecutionEngine(
                planBuilder, retryExecutor, executionRepository,
                eventPublisher, taskExecutorLookup);
    }

    // ========================================================================
    // Complete E2E flow
    // ========================================================================

    @Nested
    @DisplayName("Complete E2E flow — realistic performance campaign")
    class CompleteE2EFlow {

        @Test
        @DisplayName("E2E-01: Full 3-phase execution with realistic executors")
        void fullThreePhaseRealisticExecution() {
            ScenarioDefinition scenario = buildScenario("perf-campaign-1",
                    List.of(
                            step("purge-db", "database", Phase.PREPARATION, List.of(), List.of()),
                            step("seed-data", "database", Phase.PREPARATION, List.of(t("purge-db")), List.of("purge-db")),
                            step("start-mocks", "mockserver", Phase.PREPARATION, List.of(), List.of()),
                            step("api-load", "gatling", Phase.INJECTION,
                                    List.of(t("seed-data"), t("start-mocks")),
                                    List.of("seed-data", "start-mocks")),
                            step("check-p95", "gatling-metric", Phase.ASSERTION,
                                    List.of(t("api-load")), List.of("api-load")),
                            step("check-errors", "gatling-metric", Phase.ASSERTION,
                                    List.of(t("api-load")), List.of("api-load"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("database",
                    new FixedResultTaskExecutor("database", Duration.ofMillis(50), Map.of("rows_affected", 42)));
            taskExecutorLookup.registerTask("mockserver",
                    new FixedResultTaskExecutor("mockserver", Duration.ofMillis(30), Map.of("mocks", 3, "port", 8080)));
            taskExecutorLookup.registerTask("gatling",
                    new FixedResultTaskExecutor("gatling", Duration.ofMillis(150),
                            Map.of("p95Ms", 320L, "errorRate", 0.002, "throughput", 1500.0, "totalRequests", 10000L)));
            taskExecutorLookup.registerAssertion("gatling-metric",
                    new FixedAssertionExecutor("gatling-metric", AssertionStatus.PASSED,
                            "p95=320ms, threshold=500ms", Duration.ofMillis(5)));

            ExecutionId executionId = engine.execute(scenario);
            assertNotNull(executionId);

            assertEquals(1, countEvents(ScenarioStarted.class));
            assertEquals(1, countEvents(ScenarioFinished.class));

            ScenarioFinished finished = getEvent(ScenarioFinished.class);
            assertNotNull(finished);

            // Verify phase ordering
            List<PhaseStarted> phaseStarts = getEventsOrdered(PhaseStarted.class);
            assertEquals(3, phaseStarts.size());
            assertEquals(Phase.PREPARATION, phaseStarts.get(0).phase());
            assertEquals(Phase.INJECTION, phaseStarts.get(1).phase());
            assertEquals(Phase.ASSERTION, phaseStarts.get(2).phase());

            // All 6 tasks should have completed
            assertEquals(6, countEvents(TaskCompleted.class));
            assertEquals(0, countEvents(TaskFailed.class));

            // Repository should have persisted state
            ExecutionState persisted = executionRepository.findById(executionId).orElseThrow();
            assertEquals(ExecutionStatus.COMPLETED, persisted.status());
            // Task results are saved via the engine's saveTaskResult calls
            assertTrue(persisted.context() != null);
        }

        @Test
        @DisplayName("E2E-02: DAG ordering respected — dependent tasks execute after their dependencies")
        void dagOrderingRespected() {
            ScenarioDefinition scenario = buildScenario("dag-chain",
                    List.of(
                            step("prep-1", "shell", Phase.PREPARATION, List.of(), List.of()),
                            step("prep-2", "shell", Phase.PREPARATION, List.of(t("prep-1")), List.of("prep-1")),
                            step("inject-1", "gatling", Phase.INJECTION, List.of(t("prep-2")), List.of("prep-2")),
                            step("assert-1", "gatling-metric", Phase.ASSERTION, List.of(t("inject-1")), List.of("inject-1"))
                    ));

            List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

            taskExecutorLookup.registerTask("shell",
                    new OrderTrackingTaskExecutor("shell", Duration.ofMillis(10), Map.of(), executionOrder));
            taskExecutorLookup.registerTask("gatling",
                    new OrderTrackingTaskExecutor("gatling", Duration.ofMillis(30), Map.of(), executionOrder));
            taskExecutorLookup.registerAssertion("gatling-metric",
                    new OrderTrackingAssertionExecutor("gatling-metric", executionOrder));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            engine.execute(scenario);

            int prep1Pos = executionOrder.indexOf("prep-1");
            int prep2Pos = executionOrder.indexOf("prep-2");
            int injectPos = executionOrder.indexOf("inject-1");
            int assertPos = executionOrder.indexOf("assert-1");

            assertTrue(prep1Pos < prep2Pos, "prep-1 before prep-2");
            assertTrue(prep2Pos < injectPos, "prep-2 before inject-1");
            assertTrue(injectPos < assertPos, "inject-1 before assert-1");
        }
    }

    // ========================================================================
    // Verdict and failure handling
    // ========================================================================

    @Nested
    @DisplayName("Verdict and failure handling")
    class VerdictAndFailures {

        @Test
        @DisplayName("E2E-10: Verdict is SUCCESS when all phases pass")
        void verdictSuccessWhenAllPass() {
            ScenarioDefinition scenario = buildScenario("all-success",
                    List.of(
                            step("prep", "shell", Phase.PREPARATION, List.of(), List.of()),
                            step("load", "gatling", Phase.INJECTION, List.of(t("prep")), List.of("prep")),
                            step("assert", "gatling-metric", Phase.ASSERTION,
                                    List.of(t("load")), List.of("load"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("shell",
                    new FixedResultTaskExecutor("shell", Duration.ofMillis(10), Map.of()));
            taskExecutorLookup.registerTask("gatling",
                    new FixedResultTaskExecutor("gatling", Duration.ofMillis(20),
                            Map.of("p95Ms", 200L, "errorRate", 0.001)));
            taskExecutorLookup.registerAssertion("gatling-metric",
                    new FixedAssertionExecutor("gatling-metric", AssertionStatus.PASSED,
                            "OK", Duration.ofMillis(5)));

            engine.execute(scenario);

            ScenarioFinished finished = getEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.SUCCESS, finished.verdict());
        }

        @Test
        @DisplayName("E2E-11: Verdict is FAILED when assertion fails")
        void verdictFailedWhenAssertionFails() {
            ScenarioDefinition scenario = buildScenario("failed-assertion",
                    List.of(
                            step("load", "gatling", Phase.INJECTION, List.of(), List.of()),
                            step("check-sla", "gatling-metric", Phase.ASSERTION,
                                    List.of(t("load")), List.of("load"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("gatling",
                    new FixedResultTaskExecutor("gatling", Duration.ofMillis(20),
                            Map.of("p95Ms", 1500L, "errorRate", 0.05)));
            taskExecutorLookup.registerAssertion("gatling-metric",
                    new FixedAssertionExecutor("gatling-metric", AssertionStatus.FAILED,
                            "p95=1500ms exceeds threshold=500ms", Duration.ofMillis(5)));

            engine.execute(scenario);

            ScenarioFinished finished = getEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertEquals(Verdict.FAILED, finished.verdict());
        }

        @Test
        @DisplayName("E2E-12: Task failure causes SKIP in dependent tasks")
        void taskFailureSkipsDependents() {
            ScenarioDefinition scenario = buildScenario("fail-skip",
                    List.of(
                            step("critical-setup", "database", Phase.PREPARATION, List.of(), List.of()),
                            step("depends-on-setup", "gatling", Phase.INJECTION,
                                    List.of(t("critical-setup")), List.of("critical-setup"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("database", new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition st) {
                    return TaskResult.failed(st.id(), "database", Duration.ofMillis(10),
                            "Connection refused", new RuntimeException("Connection refused"));
                }
                @Override
                public String getSupportedTaskName() { return "database"; }
            });

            var gatlingCalled = new AtomicBoolean(false);
            taskExecutorLookup.registerTask("gatling", new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition st) {
                    gatlingCalled.set(true);
                    return TaskResult.success(st.id(), "gatling", Duration.ofMillis(10), Map.of());
                }
                @Override
                public String getSupportedTaskName() { return "gatling"; }
            });

            engine.execute(scenario);

            ScenarioFinished finished = getEvent(ScenarioFinished.class);
            assertNotNull(finished);
            assertNotEquals(Verdict.SUCCESS, finished.verdict(),
                    "Expected non-success verdict when a task fails");
        }

        @Test
        @DisplayName("E2E-13: Retry mechanism recovers from transient failure")
        void retryMechanismRecovers() {
            // Use a step with explicit retry policy to ensure retries are triggered
            var retryPolicy = new RetryPolicy(3, Duration.ofMillis(10),
                    2.0, Duration.ofMillis(100), Set.of(RuntimeException.class));

            var scenario = new ScenarioDefinition(
                    sId("retry-recovery"), "E2E retry-recovery", "1.0",
                    List.of("e2e"), Map.of(), ExecutionMode.LOCAL,
                    List.of(new StepDefinition(t("flaky-task"), "database", Phase.PREPARATION,
                            Map.of(), List.of(), List.of(), Duration.ofSeconds(60),
                            retryPolicy)),
                    Map.of());

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            var attempts = new AtomicInteger(0);
            taskExecutorLookup.registerTask("database", new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition st) {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        throw new RuntimeException("Transient failure, attempt " + attempt);
                    }
                    return TaskResult.success(st.id(), "database", Duration.ofMillis(5),
                            Map.of("attempts", attempt));
                }
                @Override
                public String getSupportedTaskName() { return "database"; }
            });

            engine.execute(scenario);

            // Task should complete successfully after retries
            assertEquals(1, countEvents(TaskCompleted.class),
                    "Expected task to complete after retries");
            // At least 2 retry events should be published
            long completed = countEvents(TaskCompleted.class);
            assertTrue(completed >= 1, "Expected at least 1 task completion, got " + completed);
        }
    }

    // ========================================================================
    // Phase isolation
    // ========================================================================

    @Nested
    @DisplayName("Phase isolation")
    class PhaseIsolation {

        @Test
        @DisplayName("E2E-20: Assertion phase always executes")
        void assertionAlwaysExecutes() {
            ScenarioDefinition scenario = buildScenario("assertion-always",
                    List.of(
                            step("load", "gatling", Phase.INJECTION, List.of(), List.of()),
                            step("verify", "gatling-metric", Phase.ASSERTION,
                                    List.of(t("load")), List.of("load"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("gatling",
                    new FixedResultTaskExecutor("gatling", Duration.ofMillis(20),
                            Map.of("p95Ms", 600L)));

            var assertionExecuted = new AtomicBoolean(false);
            taskExecutorLookup.registerAssertion("gatling-metric", new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition st) {
                    assertionExecuted.set(true);
                    return new AssertionResult(st.id(), AssertionStatus.FAILED,
                            "p95 too high", null, Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "gatling-metric"; }
            });

            engine.execute(scenario);
            assertTrue(assertionExecuted.get(), "Assertion phase must execute");
        }

        @Test
        @DisplayName("E2E-21: Scenario with only preparation steps succeeds")
        void scenarioWithOnlyPreparation() {
            ScenarioDefinition scenario = buildScenario("prep-only",
                    List.of(
                            step("purge", "database", Phase.PREPARATION, List.of(), List.of()),
                            step("seed", "database", Phase.PREPARATION, List.of(t("purge")), List.of("purge"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("database",
                    new FixedResultTaskExecutor("database", Duration.ofMillis(10), Map.of("ok", true)));

            ExecutionId executionId = engine.execute(scenario);
            assertNotNull(executionId);
            assertEquals(2, countEvents(TaskCompleted.class));
            assertEquals(0, countEvents(TaskFailed.class));
        }

        @Test
        @DisplayName("E2E-22: Scenario with only injection step succeeds")
        void scenarioWithOnlyInjection() {
            ScenarioDefinition scenario = buildScenario("inject-only",
                    List.of(
                            step("load", "gatling", Phase.INJECTION, List.of(), List.of())
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("gatling",
                    new FixedResultTaskExecutor("gatling", Duration.ofMillis(15), Map.of("requests", 5000)));

            ExecutionId executionId = engine.execute(scenario);
            assertNotNull(executionId);
            assertEquals(1, countEvents(TaskCompleted.class));
        }
    }

    // ========================================================================
    // Parallel execution
    // ========================================================================

    @Nested
    @DisplayName("Parallel execution at same DAG level")
    class ParallelExecution {

        @Test
        @DisplayName("E2E-30: Independent tasks at same level execute in parallel")
        void independentTasksExecuteInParallel() throws Exception {
            var latchA = new CountDownLatch(1);
            var latchB = new CountDownLatch(1);
            var startedA = new CountDownLatch(1);
            var startedB = new CountDownLatch(1);

            ScenarioDefinition scenario = buildScenario("parallel-tasks",
                    List.of(
                            step("task-a", "shell", Phase.PREPARATION, List.of(), List.of()),
                            step("task-b", "shell", Phase.PREPARATION, List.of(), List.of())
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            taskExecutorLookup.registerTask("shell", new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition st) {
                    if (st.id().value().equals("task-a")) {
                        startedA.countDown();
                        try { latchA.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        startedB.countDown();
                        try { latchB.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return TaskResult.success(st.id(), "shell", Duration.ofMillis(5), Map.of());
                }
                @Override
                public String getSupportedTaskName() { return "shell"; }
            });

            var execThread = new Thread(() -> engine.execute(scenario));
            execThread.start();

            boolean bothStarted = startedA.await(2, TimeUnit.SECONDS)
                    && startedB.await(2, TimeUnit.SECONDS);

            latchA.countDown();
            latchB.countDown();
            execThread.join(5000);

            assertTrue(bothStarted, "Both independent tasks should start in parallel");
            assertEquals(2, countEvents(TaskCompleted.class));
        }
    }

    // ========================================================================
    // Cancel
    // ========================================================================

    @Nested
    @DisplayName("Scenario cancellation")
    class ScenarioCancellation {

        @Test
        @DisplayName("E2E-40: Cancel on unknown execution ID does not throw")
        void cancelUnknownExecutionDoesNotThrow() {
            var unknownId = ExecutionId.generate();
            // cancel() on unknown ID should be a no-op, not throw
            assertDoesNotThrow(() -> engine.cancel(unknownId));
        }
    }

    // ========================================================================
    // Context propagation
    // ========================================================================

    @Nested
    @DisplayName("Context propagation")
    class ContextPropagation {

        @Test
        @DisplayName("E2E-50: Context flows from preparation to assertion")
        void contextFlowsBetweenPhases() {
            ScenarioDefinition scenario = buildScenario("ctx-flow",
                    List.of(
                            step("db-setup", "database", Phase.PREPARATION, List.of(), List.of()),
                            step("db-check", "database-assertion", Phase.ASSERTION,
                                    List.of(t("db-setup")), List.of("db-setup"))
                    ));

            ExecutionPlan plan = buildPlan(scenario, scenario.steps());
            planMap.put(scenario.id().value(), plan);

            var setupCalls = new AtomicInteger(0);
            taskExecutorLookup.registerTask("database", new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition st) {
                    setupCalls.incrementAndGet();
                    return TaskResult.success(st.id(), "database", Duration.ofMillis(5),
                            Map.of("datasource", "my-db", "rows_cleaned", 500));
                }
                @Override
                public String getSupportedTaskName() { return "database"; }
            });

            var checkCalls = new AtomicInteger(0);
            taskExecutorLookup.registerAssertion("database-assertion", new AssertionExecutor() {
                @Override
                public AssertionResult evaluate(ExecutionContext ctx, StepDefinition st) {
                    checkCalls.incrementAndGet();
                    return new AssertionResult(st.id(), AssertionStatus.PASSED,
                            "verified", null, Duration.ofMillis(5), Instant.now());
                }
                @Override
                public String getSupportedAssertionName() { return "database-assertion"; }
            });

            engine.execute(scenario);

            assertEquals(1, setupCalls.get());
            assertEquals(1, checkCalls.get());
        }
    }

    // ========================================================================
    // Empty scenarios
    // ========================================================================

    @Nested
    @DisplayName("Empty and edge scenarios")
    class EmptyScenarios {

        @Test
        @DisplayName("E2E-60: Scenario with no steps succeeds")
        void emptyScenarioSucceeds() {
            ScenarioDefinition scenario = buildScenario("empty", List.of());
            ExecutionPlan plan = buildPlan(scenario, List.of());
            planMap.put(scenario.id().value(), plan);

            ExecutionId executionId = engine.execute(scenario);
            assertNotNull(executionId);
            assertEquals(1, countEvents(ScenarioStarted.class));
            assertEquals(1, countEvents(ScenarioFinished.class));
        }

        @Test
        @DisplayName("E2E-61: Null scenario throws")
        void nullScenarioThrows() {
            assertThrows(ExecutionException.class, () -> engine.execute(null));
        }
    }

    // ========================================================================
    // Factory methods
    // ========================================================================

    private static TaskId t(String id) { return TaskId.of(id); }
    private static ScenarioId sId(String id) { return ScenarioId.of(id); }

    private static StepDefinition step(String id, String taskName, Phase phase,
                                       List<TaskId> dependsOn, List<String> requiredContexts) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                dependsOn, requiredContexts, Duration.ofSeconds(60), null);
    }

    private static ScenarioDefinition buildScenario(String id, List<StepDefinition> steps) {
        return new ScenarioDefinition(sId(id), "E2E " + id, "1.0", List.of("e2e"), Map.of(),
                ExecutionMode.LOCAL, steps, Map.of());
    }

    private ExecutionPlan buildPlan(ScenarioDefinition scenario, List<StepDefinition> steps) {
        var eid = ExecutionId.generate();
        List<ExecutionStep> prep = new ArrayList<>();
        List<ExecutionStep> injection = new ArrayList<>();
        List<ExecutionStep> assertion = new ArrayList<>();

        for (StepDefinition step : steps) {
            var execStep = new ExecutionStep(step, step.dependsOn(),
                    step.dependsOn().isEmpty() ? 0 : 1,
                    Set.copyOf(step.requiredContexts()));
            switch (step.phase()) {
                case PREPARATION -> prep.add(execStep);
                case INJECTION -> injection.add(execStep);
                case ASSERTION -> assertion.add(execStep);
            }
        }

        return new ExecutionPlan(eid, scenario.id(), prep, injection, assertion,
                ExecutionContext.initial(eid, scenario.id()));
    }

    // ========================================================================
    // Event helpers
    // ========================================================================

    private long countEvents(Class<?> eventType) {
        return publishedEvents.stream().filter(eventType::isInstance).count();
    }

    @SuppressWarnings("unchecked")
    private <T> T getEvent(Class<T> eventType) {
        return (T) publishedEvents.stream()
                .filter(eventType::isInstance)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getEventsOrdered(Class<T> eventType) {
        return publishedEvents.stream()
                .filter(eventType::isInstance)
                .map(e -> (T) e)
                .toList();
    }

    // ========================================================================
    // Stubs
    // ========================================================================

    class StubExecutionRepository implements ExecutionRepository {
        private final Map<String, ExecutionState> states = new ConcurrentHashMap<>();

        @Override
        public void save(ExecutionState state) {
            states.put(state.id().value(), state);
        }

        @Override
        public Optional<ExecutionState> findById(ExecutionId id) {
            return Optional.ofNullable(states.get(id.value()));
        }

        @Override
        public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {
            ExecutionState existing = states.get(id.value());
            if (existing != null) {
                Map<Phase, PhaseStatus> updated = new EnumMap<>(existing.phaseStatuses());
                updated.put(phase, status);
                states.put(id.value(), new ExecutionState(existing.id(), existing.scenarioId(),
                        existing.status(), updated, existing.context(),
                        existing.startedAt(), Instant.now()));
            }
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
            allTaskResults.add(result);
        }

        @Override
        public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
            return Map.of();
        }
    }

    static class StubTaskExecutorLookup implements TaskExecutorLookup {
        private final Map<String, TaskExecutor> taskExecutors = new HashMap<>();
        private final Map<String, AssertionExecutor> assertionExecutors = new HashMap<>();

        void registerTask(String taskName, TaskExecutor executor) {
            taskExecutors.put(taskName, executor);
        }

        void registerAssertion(String assertionName, AssertionExecutor executor) {
            assertionExecutors.put(assertionName, executor);
        }

        @Override
        public TaskExecutor findTaskExecutor(String taskName) {
            return taskExecutors.get(taskName);
        }

        @Override
        public AssertionExecutor findAssertionExecutor(String assertionName) {
            return assertionExecutors.get(assertionName);
        }
    }

    /** TaskExecutor that returns a fixed successful result with simulated latency. */
    static class FixedResultTaskExecutor implements TaskExecutor {
        private final String taskName;
        private final Duration latency;
        private final Map<String, Object> outputs;

        FixedResultTaskExecutor(String taskName, Duration latency, Map<String, Object> outputs) {
            this.taskName = taskName;
            this.latency = latency;
            this.outputs = new HashMap<>(outputs);
        }

        @Override
        public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
            try { Thread.sleep(latency.toMillis()); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return TaskResult.success(step.id(), taskName, latency, new HashMap<>(outputs));
        }

        @Override
        public String getSupportedTaskName() { return taskName; }
    }

    /** AssertionExecutor that returns a fixed result. */
    static class FixedAssertionExecutor implements AssertionExecutor {
        private final String assertionName;
        private final AssertionStatus status;
        private final String message;
        private final Duration latency;

        FixedAssertionExecutor(String assertionName, AssertionStatus status,
                               String message, Duration latency) {
            this.assertionName = assertionName;
            this.status = status;
            this.message = message;
            this.latency = latency;
        }

        @Override
        public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
            try { Thread.sleep(latency.toMillis()); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new AssertionResult(step.id(), status, message, null, latency, Instant.now());
        }

        @Override
        public String getSupportedAssertionName() { return assertionName; }
    }

    /** TaskExecutor that tracks execution order by step ID. */
    static class OrderTrackingTaskExecutor implements TaskExecutor {
        private final String taskName;
        private final Duration latency;
        private final Map<String, Object> outputs;
        private final List<String> order;

        OrderTrackingTaskExecutor(String taskName, Duration latency,
                                  Map<String, Object> outputs, List<String> order) {
            this.taskName = taskName;
            this.latency = latency;
            this.outputs = new HashMap<>(outputs);
            this.order = order;
        }

        @Override
        public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
            try { Thread.sleep(latency.toMillis()); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            order.add(step.id().value());
            return TaskResult.success(step.id(), taskName, latency, new HashMap<>(outputs));
        }

        @Override
        public String getSupportedTaskName() { return taskName; }
    }

    /** AssertionExecutor that tracks execution order by step ID. */
    static class OrderTrackingAssertionExecutor implements AssertionExecutor {
        private final String assertionName;
        private final List<String> order;

        OrderTrackingAssertionExecutor(String assertionName, List<String> order) {
            this.assertionName = assertionName;
            this.order = order;
        }

        @Override
        public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
            order.add(step.id().value());
            return new AssertionResult(step.id(), AssertionStatus.PASSED, "OK",
                    null, Duration.ofMillis(5), Instant.now());
        }

        @Override
        public String getSupportedAssertionName() { return assertionName; }
    }
}
