package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.*;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.DefaultTaskCorrelationTracker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.plan.ExecutionPlanBuilder;
import com.performance.platform.transport.*;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RemoteExecutionEngine")
class RemoteExecutionEngineTest {

    // ==================== Fixtures ====================

    static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);

    // Dependencies (injectables/stubs)
    Map<String, ExecutionPlan> planMap = new HashMap<>();
    ExecutionPlanBuilder planBuilder = s -> planMap.computeIfAbsent(
            s.id().value(), k -> buildSimplePlan(s));
    StubAgentAvailabilityChecker availabilityChecker;
    TaskCorrelationTracker tracker;
    FakeExecutionTransport transport;
    StubExecutionRepository executionRepository;
    ExecutionConfig config;
    ConcurrentLinkedQueue<Object> publishedEvents;
    ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        planMap.clear();
        publishedEvents = new ConcurrentLinkedQueue<>();
        tracker = new DefaultTaskCorrelationTracker();
        transport = new FakeExecutionTransport();
        eventPublisher = event -> publishedEvents.add(event);
        availabilityChecker = new StubAgentAvailabilityChecker();
        executionRepository = new StubExecutionRepository();
        config = new ExecutionConfig(
                Duration.ofSeconds(30),     // taskAvailabilityTimeout
                Duration.ofSeconds(60),     // taskExecutionTimeout
                Duration.ofSeconds(10),     // workInProgressResetInterval
                TaskCompletionPolicy.FIRST_COMPLETE
        );
    }

    // ---- Static helpers (same pattern as LocalExecutionEngineTest) ----

    private static TaskId t(String id) { return TaskId.of(id); }
    private static ScenarioId sId(String id) { return ScenarioId.of(id); }

    private static StepDefinition step(String id, String taskName, Phase phase) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                List.of(), List.of(), Duration.ofSeconds(30), null);
    }

    private static StepDefinition step(String id, String taskName, Phase phase,
                                         List<TaskId> dependsOn, List<String> requiredContexts) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                dependsOn, requiredContexts, Duration.ofSeconds(30), null);
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

    private ExecutionStep execStep(StepDefinition stepDef, List<TaskId> deps, int dagLevel, Set<String> requiredKeys) {
        return new ExecutionStep(stepDef, deps == null ? List.of() : deps, dagLevel, requiredKeys);
    }

    /**
     * Build a simple plan with 1 step in PREPARATION.
     * Used by the default planBuilder lambda.
     */
    private ExecutionPlan buildSimplePlan(ScenarioDefinition scenario) {
        List<StepDefinition> steps = scenario.steps();
        List<ExecutionStep> prep = new ArrayList<>();
        List<ExecutionStep> inj = new ArrayList<>();
        List<ExecutionStep> assertion = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            StepDefinition sd = steps.get(i);
            // Determine DAG level from dependsOn: simple strategy — level = dependsOn size > 0 ? 1 : 0
            int level = sd.dependsOn().isEmpty() ? 0 : 1;
            Set<String> reqKeys = new HashSet<>(sd.requiredContexts());
            ExecutionStep es = execStep(sd, sd.dependsOn(), level, reqKeys);
            switch (sd.phase()) {
                case PREPARATION -> prep.add(es);
                case INJECTION -> inj.add(es);
                case ASSERTION -> assertion.add(es);
            }
        }
        return buildPlan(scenario, prep, inj, assertion);
    }

    // ==================== Dispatch broadcast ====================

    @Nested
    @DisplayName("Dispatch")
    class DispatchTests {

        @Test
        @DisplayName("should dispatch task without targetAgentId (broadcast)")
        void dispatchBroadcast_noTargetAgentId() {
            ScenarioDefinition sc = scenario("s1", List.of(step("s1", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, config, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);

            assertThat(transport.dispatchedRequests).hasSize(1);
            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            assertThat(req.step().taskName()).isEqualTo("db-init");
            assertThat(req.executionId()).isNotNull();

            // Fire completion so engine finishes
            transport.fireEvent(completedEvent(req, AgentId.generate(), Map.of("result", "done")));
            waitFor(() -> !engineThread.isAlive(), 5000);
        }

        @Test
        @DisplayName("should dispatch multiple steps at same DAG level")
        void dispatchMultipleSteps_dagLevel() {
            List<StepDefinition> steps = List.of(
                    step("t1", "db-init", Phase.PREPARATION),
                    step("t2", "cache-warm", Phase.PREPARATION));
            ScenarioDefinition sc = scenario("s2", steps);
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, config, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> transport.dispatchedRequests.size() >= 2, 2000);

            assertThat(transport.dispatchedRequests).hasSize(2);
            List<String> taskNames = transport.dispatchedRequests.stream()
                    .map(r -> r.step().taskName())
                    .toList();
            assertThat(taskNames).contains("db-init", "cache-warm");

            // Fire completions for both so engine finishes
            for (TaskExecutionRequest req : transport.dispatchedRequests) {
                transport.fireEvent(completedEvent(req, AgentId.generate(), Map.of("result", "done")));
            }
            waitFor(() -> !engineThread.isAlive(), 5000);
        }

        @Test
        @DisplayName("should publish TaskDispatched events")
        void publishTaskDispatched() {
            ScenarioDefinition sc = scenario("s3", List.of(step("s3", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, config, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);
            TaskExecutionRequest req = transport.dispatchedRequests.get(0);

            List<TaskDispatched> dispatched = publishedEvents.stream()
                    .filter(e -> e instanceof TaskDispatched)
                    .map(e -> (TaskDispatched) e)
                    .toList();
            assertThat(dispatched).hasSize(1);
            assertThat(dispatched.get(0).taskName()).isEqualTo("db-init");

            transport.fireEvent(completedEvent(req, AgentId.generate(), Map.of("result", "done")));
            waitFor(() -> !engineThread.isAlive(), 5000);
        }
    }

    // ==================== Claim handling ====================

    @Nested
    @DisplayName("Claim")
    class ClaimTests {

        @Test
        @DisplayName("should register agent claim via TaskCorrelationTracker")
        void trackClaim() {
            ScenarioDefinition sc = scenario("c1", List.of(step("c1", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, config, eventPublisher);

            startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);

            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            var agentId = AgentId.generate();
            transport.fireEvent(new ExecutionEvent(
                    EventId.generate(), req.executionId(), req.id(), agentId,
                    ExecutionEvent.TASK_CLAIMED, Map.of(), Instant.now()));
            sleep(200);

            Set<AgentId> claims = tracker.claimsFor(req.id());
            assertThat(claims).contains(agentId);
        }

        @Test
        @DisplayName("should accept multi-agent claims (ADR-011)")
        void multiAgentClaims() {
            ScenarioDefinition sc = scenario("c2", List.of(step("c2", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var allComplete = new ExecutionConfig(
                    config.taskAvailabilityTimeout(), config.taskExecutionTimeout(),
                    config.workInProgressResetInterval(), TaskCompletionPolicy.ALL_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, allComplete, eventPublisher);

            startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);

            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            var a1 = AgentId.generate();
            var a2 = AgentId.generate();

            transport.fireEvent(claimedEvent(req, a1));
            transport.fireEvent(claimedEvent(req, a2));
            sleep(200);

            Set<AgentId> claims = tracker.claimsFor(req.id());
            assertThat(claims).containsExactlyInAnyOrder(a1, a2);
        }
    }

    // ==================== Completion policies ====================

    @Nested
    @DisplayName("Completion")
    class CompletionTests {

        @Test
        @DisplayName("FIRST_COMPLETE: advance au premier resultat")
        void firstComplete() {
            ScenarioDefinition sc = scenario("cp1", List.of(step("cp1", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, config, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);

            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            var a1 = AgentId.generate();
            var a2 = AgentId.generate();

            transport.fireEvent(claimedEvent(req, a1));
            transport.fireEvent(claimedEvent(req, a2));
            // Seul a1 complete
            transport.fireEvent(completedEvent(req, a1, Map.of("result", "done")));

            waitFor(() -> !engineThread.isAlive(), 5000);
            assertThat(engineThread.isAlive()).isFalse();
        }

        @Test
        @DisplayName("ALL_COMPLETE: attend tous les resultats")
        void allComplete() {
            ScenarioDefinition sc = scenario("cp2", List.of(step("cp2", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var allCompleteCfg = new ExecutionConfig(
                    config.taskAvailabilityTimeout(), config.taskExecutionTimeout(),
                    config.workInProgressResetInterval(), TaskCompletionPolicy.ALL_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, allCompleteCfg, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);

            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            var a1 = AgentId.generate();
            var a2 = AgentId.generate();

            transport.fireEvent(claimedEvent(req, a1));
            transport.fireEvent(claimedEvent(req, a2));
            // Seul a1 complete — engine bloque encore
            transport.fireEvent(completedEvent(req, a1, Map.of("r", "ok")));
            sleep(500);
            assertThat(engineThread.isAlive()).isTrue();

            // a2 complete → engine se termine
            transport.fireEvent(completedEvent(req, a2, Map.of("r", "also-ok")));
            waitFor(() -> !engineThread.isAlive(), 5000);
            assertThat(engineThread.isAlive()).isFalse();
        }

        @Test
        @DisplayName("should complete when claimed agent fails (ALL_COMPLETE)")
        void allCompleteWithFailure() {
            ScenarioDefinition sc = scenario("cp3", List.of(step("cp3", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var allCompleteCfg = new ExecutionConfig(
                    config.taskAvailabilityTimeout(), config.taskExecutionTimeout(),
                    config.workInProgressResetInterval(), TaskCompletionPolicy.ALL_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, allCompleteCfg, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);

            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            var a1 = AgentId.generate();

            transport.fireEvent(claimedEvent(req, a1));
            transport.fireEvent(new ExecutionEvent(
                    EventId.generate(), req.executionId(), req.id(), a1,
                    ExecutionEvent.TASK_FAILED,
                    Map.of("error", "simulated failure", "attempt", 1),
                    Instant.now()));

            waitFor(() -> !engineThread.isAlive(), 5000);
            assertThat(engineThread.isAlive()).isFalse();
        }
    }

    // ==================== Error handling ====================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should fail scenario when no agent available")
        void noAgentAvailable() {
            availabilityChecker.failWith(new NoAvailableAgentException("db-init"));
            ScenarioDefinition sc = scenario("e1", List.of(step("e1", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var shortCfg = new ExecutionConfig(
                    Duration.ofMillis(100), Duration.ofSeconds(10),
                    Duration.ofSeconds(3), TaskCompletionPolicy.FIRST_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, shortCfg, eventPublisher);

            engine.execute(sc);

            List<ScenarioFinished> finished = publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished)
                    .map(e -> (ScenarioFinished) e)
                    .toList();
            assertThat(finished).hasSize(1);
            assertThat(finished.get(0).verdict()).isEqualTo(Verdict.FAILED);
        }

        @Test
        @DisplayName("should handle step timeout")
        void stepTimeout() {
            ScenarioDefinition sc = scenario("e2", List.of(step("e2", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var tinyCfg = new ExecutionConfig(
                    Duration.ofSeconds(30), Duration.ofMillis(300),
                    Duration.ofSeconds(10), TaskCompletionPolicy.FIRST_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, tinyCfg, eventPublisher);

            ExecutionId executionId = engine.execute(sc);

            Optional<ExecutionState> state = executionRepository.findById(executionId);
            assertThat(state).isPresent();
            assertThat(state.get().status()).isEqualTo(ExecutionStatus.FAILED);
        }

        @Test
        @DisplayName("should skip step when dependency failed")
        void skipOnFailedDependency() {
            List<StepDefinition> steps = List.of(
                    step("first", "first", Phase.PREPARATION),
                    step("second", "second", Phase.PREPARATION,
                            List.of(t("first")), List.of("first")));
            ScenarioDefinition sc = scenario("e3", steps);
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var shortCfg = new ExecutionConfig(
                    Duration.ofSeconds(5), Duration.ofMillis(200),
                    Duration.ofSeconds(3), TaskCompletionPolicy.FIRST_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, shortCfg, eventPublisher);

            engine.execute(sc);

            // "first" timeout (FAILED) → phase has anyFailed=true → verdict FAILED
            List<ScenarioFinished> finished = publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished)
                    .map(e -> (ScenarioFinished) e)
                    .toList();
            assertThat(finished).hasSize(1);
            assertThat(finished.get(0).verdict()).isEqualTo(Verdict.FAILED);
        }
    }

    // ==================== getStatus / cancel ====================

    @Nested
    @DisplayName("getStatus / cancel")
    class GetStatusCancelTests {

        @Test
        @DisplayName("cancel stops execution")
        void cancelStopsExecution() {
            ScenarioDefinition sc = scenario("g1", List.of(step("g1", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var longCfg = new ExecutionConfig(
                    Duration.ofSeconds(5), Duration.ofSeconds(60),
                    Duration.ofSeconds(10), TaskCompletionPolicy.ALL_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, longCfg, eventPublisher);

            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);
            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            ExecutionId executionId = req.executionId();

            assertThat(engine.getStatus(executionId)).isEqualTo(ExecutionStatus.RUNNING);

            engine.cancel(executionId);
            waitFor(() -> !engineThread.isAlive(), 3000);
            assertThat(engineThread.isAlive()).isFalse();
            assertThat(engine.getStatus(executionId)).isEqualTo(ExecutionStatus.CANCELLED);
        }
    }

    // ==================== Event publishing ====================

    @Nested
    @DisplayName("Event publishing")
    class EventPublishingTests {

        @Test
        @DisplayName("should publish ScenarioStarted and ScenarioFinished")
        void lifecycleEvents() {
            ScenarioDefinition sc = scenario("ep1", List.of(step("ep1", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, config, eventPublisher);

            // Execute in thread and fire completion to avoid blocking
            Thread engineThread = startInThread(() -> engine.execute(sc));
            waitFor(() -> !transport.dispatchedRequests.isEmpty(), 2000);
            TaskExecutionRequest req = transport.dispatchedRequests.get(0);
            transport.fireEvent(completedEvent(req, AgentId.generate(), Map.of("result", "done")));
            waitFor(() -> !engineThread.isAlive(), 5000);

            List<ScenarioStarted> started = publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioStarted)
                    .map(e -> (ScenarioStarted) e)
                    .toList();
            List<ScenarioFinished> finished = publishedEvents.stream()
                    .filter(e -> e instanceof ScenarioFinished)
                    .map(e -> (ScenarioFinished) e)
                    .toList();

            assertThat(started).hasSize(1);
            assertThat(finished).hasSize(1);
            assertThat(started.get(0).scenarioId()).isEqualTo(sc.id());
            assertThat(finished.get(0).verdict()).isEqualTo(Verdict.SUCCESS);
        }

        @Test
        @DisplayName("should publish PhaseStarted and PhaseCompleted")
        void phaseEvents() {
            ScenarioDefinition sc = scenario("ep2", List.of(step("ep2", "db-init", Phase.PREPARATION)));
            planMap.put(sc.id().value(), buildSimplePlan(sc));

            // Use short timeout so engine completes quickly without external events
            var fastCfg = new ExecutionConfig(
                    Duration.ofSeconds(5), Duration.ofMillis(200),
                    Duration.ofSeconds(3), TaskCompletionPolicy.FIRST_COMPLETE);
            var engine = new RemoteExecutionEngine(
                    planBuilder, availabilityChecker, tracker, transport,
                    executionRepository, fastCfg, eventPublisher);

            engine.execute(sc);

            List<PhaseStarted> started = publishedEvents.stream()
                    .filter(e -> e instanceof PhaseStarted)
                    .map(e -> (PhaseStarted) e)
                    .toList();
            List<PhaseCompleted> completed = publishedEvents.stream()
                    .filter(e -> e instanceof PhaseCompleted)
                    .map(e -> (PhaseCompleted) e)
                    .toList();

            assertThat(started).isNotEmpty();
            assertThat(completed).isNotEmpty();
            assertThat(started.get(0).phase()).isEqualTo(Phase.PREPARATION);
        }
    }

    // ==================== Event constructors ====================

    ExecutionEvent claimedEvent(TaskExecutionRequest req, AgentId agentId) {
        return new ExecutionEvent(
                EventId.generate(), req.executionId(), req.id(), agentId,
                ExecutionEvent.TASK_CLAIMED, Map.of(), Instant.now());
    }

    ExecutionEvent completedEvent(TaskExecutionRequest req, AgentId agentId,
                                   Map<String, Object> outputs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "SUCCESS");
        payload.put("durationMs", 50L);
        payload.put("outputs", outputs);
        return new ExecutionEvent(
                EventId.generate(), req.executionId(), req.id(), agentId,
                ExecutionEvent.TASK_COMPLETED, payload, Instant.now());
    }

    // ---- Thread helpers ----

    Thread startInThread(Runnable r) {
        var t = Thread.ofVirtual().name("test-engine").unstarted(r);
        t.start();
        return t;
    }

    void waitFor(BooleanSupplier condition, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (!condition.get() && System.currentTimeMillis() - start < timeoutMs) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean get();
    }

    // ---- Stubs ----

    /** Transport factice qui stocke les requetes et permet de simuler des events. */
    static class FakeExecutionTransport implements ExecutionTransport {
        final List<TaskExecutionRequest> dispatchedRequests = new ArrayList<>();
        ExecutionEventHandler eventHandler;

        @Override public void dispatchTask(TaskExecutionRequest request) { dispatchedRequests.add(request); }
        @Override public void broadcastSignal(AgentSignal signal) {}
        @Override public void publishEvent(ExecutionEvent event) {}
        @Override public Subscription subscribe(ExecutionEventHandler handler) {
            this.eventHandler = handler;
            return new Subscription() {
                @Override public void cancel() {}
                @Override public boolean isActive() { return true; }
            };
        }
        @Override public void receiveTask(TaskRequestHandler handler) {}
        @Override public void receiveSignal(AgentSignalHandler handler) {}
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean isConnected() { return true; }
        @Override public TransportType getType() { return TransportType.IN_MEMORY; }
        @Override public void publishAgentEvent(AgentLifecycleEvent event) {}
        @Override public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
            return new Subscription() {
                @Override public void cancel() {}
                @Override public boolean isActive() { return true; }
            };
        }

        void fireEvent(ExecutionEvent event) {
            if (eventHandler != null) eventHandler.onEvent(event);
        }
    }

    /** Repository en memoire. */
    static class StubExecutionRepository implements ExecutionRepository {
        final Map<String, ExecutionState> states = new HashMap<>();

        @Override public void save(ExecutionState state) { states.put(state.id().value(), state); }
        @Override public Optional<ExecutionState> findById(ExecutionId id) {
            return Optional.ofNullable(states.get(id.value()));
        }
        @Override public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {}
        @Override public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {}
        @Override public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) { return Map.of(); }
        @Override public List<ExecutionState> findAll(int limit) { return List.of(); }
        @Override public void deleteById(ExecutionId id) { /* no-op */ }
    }

    /** Stub pour AgentAvailabilityChecker. */
    static class StubAgentAvailabilityChecker implements AgentAvailabilityChecker {
        private volatile NoAvailableAgentException failure;

        void failWith(NoAvailableAgentException e) { this.failure = e; }

        @Override
        public void awaitAgentFor(String taskName, Duration timeout) {
            if (failure != null) throw failure;
            // OK by default (agent dispo immediatement)
        }
        @Override
        public boolean hasAgentFor(String taskName) { return failure == null; }
    }
}
