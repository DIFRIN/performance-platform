package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.PhaseCompleted;
import com.performance.platform.domain.event.PhaseStarted;
import com.performance.platform.domain.event.ScenarioCancelled;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.ScenarioStarted;
import com.performance.platform.domain.event.TaskClaimedByAgent;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskDispatched;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.ExecutionEngine;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.plan.ExecutionPlanBuilder;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.Subscription;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation distribuee de {@link ExecutionEngine}.
 * Diffuse les taches aux agents via {@link ExecutionTransport} en broadcast
 * (sans {@code targetAgentId}), suit les claims/results multi-agents, et
 * applique la {@link TaskCompletionPolicy}.
 *
 * <p>Active uniquement quand {@code runtime.mode=DISTRIBUTED}.</p>
 *
 * <p>Sequence :
 * <ol>
 *   <li>Construit l'{@link ExecutionPlan}</li>
 *   <li>Verifie la presence d'agents competents via {@link AgentAvailabilityChecker}</li>
 *   <li>Pour chaque etape (ordre DAG) :
 *     <ul>
 *       <li>Attend un agent pour {@code taskName}</li>
 *       <li>Construit le {@link PartialExecutionContext} depuis {@code requiredContextKeys}</li>
 *       <li>Cree {@link TaskExecutionRequest} (sans targetAgentId — broadcast)</li>
 *       <li>{@code transport.dispatchTask(request)}</li>
 *       <li>{@code tracker.trackDispatched(...)} + publie {@link TaskDispatched}</li>
 *     </ul>
 *   </li>
 *   <li>Collecte les events (subscribe) :
 *     <ul>
 *       <li>{@code TaskClaimedByAgent} → enregistre le claim (multi-agent accepte)</li>
 *       <li>{@code TaskWorkInProgress} → reset du timeout</li>
 *       <li>{@code TaskCompleted} → stocke dans {@code context[taskId][agentId]}</li>
 *       <li>{@code TaskFailed} → retry si configure</li>
 *     </ul>
 *   </li>
 *   <li>Completion selon {@code completionPolicy} avant le step suivant</li>
 *   <li>Agent perdu (TTL) → {@code ScenarioRestartSignal} broadcast → scenario FAILED</li>
 * </ol>
 * </p>
 *
 * <p>I/O bloquant (attente agents, polling completions) execute sous Virtual Threads.</p>
 */
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(RemoteExecutionEngine.class);

    /** Intervalle de polling pour les completions (ms). */
    static final long POLL_COMPLETION_MS = 500;

    // Payload keys des ExecutionEvent
    static final String PAYLOAD_TASK_ID = "taskId";
    static final String PAYLOAD_STATUS = "status";
    static final String PAYLOAD_ERROR = "error";
    static final String PAYLOAD_ATTEMPT = "attempt";
    static final String PAYLOAD_OUTPUTS = "outputs";
    static final String PAYLOAD_DURATION_MS = "durationMs";
    static final String PAYLOAD_COMPLETED_AT = "completedAt";

    private final ExecutionPlanBuilder planBuilder;
    private final AgentAvailabilityChecker availabilityChecker;
    private final TaskCorrelationTracker tracker;
    private final ExecutionTransport transport;
    private final ExecutionRepository executionRepository;
    private final ExecutionConfig config;
    private final ApplicationEventPublisher eventPublisher;

    /** Suivi en memoire des executions actives. Cle : executionId.value(). */
    private final Map<String, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();

    /** Souscription globale aux events de transport. */
    private final Subscription transportSubscription;

    public RemoteExecutionEngine(
            ExecutionPlanBuilder planBuilder,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionTransport transport,
            ExecutionRepository executionRepository,
            ExecutionConfig config,
            ApplicationEventPublisher eventPublisher) {
        this.planBuilder = planBuilder;
        this.availabilityChecker = availabilityChecker;
        this.tracker = tracker;
        this.transport = transport;
        this.executionRepository = executionRepository;
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.transportSubscription = transport.subscribe(this::onTransportEvent);
    }

    // ==================== ExecutionEngine API ====================

    @Override
    public ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException {
        if (scenario == null) {
            throw new ExecutionException("ScenarioDefinition must not be null");
        }
        if (scenario.id() == null) {
            throw new ExecutionException("ScenarioDefinition.id must not be null");
        }

        ExecutionPlan plan = initializeExecution(scenario);
        ExecutionId executionId = plan.id();
        AtomicBoolean cancelled = cancelFlags(executionId);
        ExecutionContext ctx = plan.initialContext();
        Instant start = Instant.now();
        boolean hasFailure = false;
        Verdict verdict = Verdict.SUCCESS;

        try {
            ctx = executePhase(Phase.PREPARATION, plan.preparationSteps(), ctx, executionId, cancelled);
            if (checkFailedInPhase(ctx, plan.preparationSteps())) hasFailure = true;

            ctx = executePhase(Phase.INJECTION, plan.injectionSteps(), ctx, executionId, cancelled);
            if (checkFailedInPhase(ctx, plan.injectionSteps())) hasFailure = true;

            ctx = executePhase(Phase.ASSERTION, plan.assertionSteps(), ctx, executionId, cancelled);
            if (checkFailedInPhase(ctx, plan.assertionSteps())) hasFailure = true;

            verdict = computeVerdict(ctx, plan, hasFailure, cancelled.get());
            applyVerdict(executionId, plan.scenarioId(), ctx, verdict, cancelled.get());
        } catch (NoAvailableAgentException e) {
            log.error("action=no_agent_available executionId={} error={}",
                    executionId.value(), e.getMessage(), e);
            verdict = Verdict.FAILED;
            applyVerdict(executionId, plan.scenarioId(), ctx, verdict, false);
        } catch (Exception e) {
            log.error("action=execute_unexpected_error executionId={} error={}",
                    executionId.value(), e.getMessage(), e);
            verdict = Verdict.FAILED;
            applyVerdict(executionId, plan.scenarioId(), ctx, verdict, false);
        }

        finalizeExecution(executionId, plan.scenarioId(), verdict, start);
        return executionId;
    }

    @Override
    public ExecutionStatus getStatus(ExecutionId id) {
        ActiveExecution exec = activeExecutions.get(id.value());
        if (exec != null) {
            return exec.state.status();
        }
        Optional<ExecutionState> persisted = executionRepository.findById(id);
        return persisted.map(ExecutionState::status)
                .orElseThrow(() -> new ExecutionException("Execution not found: " + id.value()));
    }

    @Override
    public void cancel(ExecutionId id) {
        ActiveExecution exec = activeExecutions.get(id.value());
        if (exec != null) {
            exec.cancelFlag.set(true);
            log.info("action=cancel_requested executionId={}", id.value());
        } else {
            log.warn("action=cancel_unknown executionId={}", id.value());
        }
    }

    // ==================== Initialization ====================

    private ExecutionPlan initializeExecution(ScenarioDefinition scenario) {
        ExecutionPlan plan = planBuilder.build(scenario);
        ExecutionId executionId = plan.id();
        ScenarioId scenarioId = scenario.id();
        log.info("action=execute_start executionId={} scenarioId={} scenarioName={}",
                executionId.value(), scenarioId.value(), scenario.name());

        ExecutionState state = createInitialState(executionId, scenarioId, plan.initialContext());
        ActiveExecution exec = new ActiveExecution(state);
        activeExecutions.put(executionId.value(), exec);

        eventPublisher.publishEvent(new ScenarioStarted(executionId, scenarioId, Instant.now()));
        log.info("action=scenario_started executionId={}", executionId.value());

        return plan;
    }

    // ==================== Phase Execution ====================

    private ExecutionContext executePhase(
            Phase phase,
            List<ExecutionStep> steps,
            ExecutionContext context,
            ExecutionId executionId,
            AtomicBoolean cancelled) {

        if (steps == null || steps.isEmpty()) {
            log.info("action=execute_phase phase={} steps=0 executionId={}", phase, executionId.value());
            return context;
        }

        // Publier PhaseStarted
        eventPublisher.publishEvent(new PhaseStarted(executionId, phase, Instant.now()));
        executionRepository.updatePhase(executionId, phase, PhaseStatus.RUNNING);
        log.info("action=phase_started phase={} executionId={}", phase, executionId.value());

        Map<Integer, List<ExecutionStep>> groupedByLevel = groupStepsByLevel(steps);
        ExecutionContext currentContext = context;
        boolean anyFailed = false;

        for (Integer level : groupedByLevel.keySet().stream().sorted().toList()) {
            if (cancelled.get()) {
                log.info("action=phase_cancelled phase={} level={} executionId={}", phase, level, executionId.value());
                break;
            }

            var classification = classifySteps(groupedByLevel.get(level), currentContext);

            // Mark skippable steps
            for (ExecutionStep step : classification.skippable()) {
                StepDefinition stepDef = step.step();
                TaskResult skippedResult = TaskResult.skipped(stepDef.id(), stepDef.taskName(), "dependency failed");
                currentContext = currentContext.with(stepDef.id().value(), "agent-remote", skippedResult);
                anyFailed = true;
            }

            // Dispatch runnable steps
            if (!classification.runnable().isEmpty()) {
                var levelResult = dispatchAndWaitLevel(
                        classification.runnable(), currentContext, phase, executionId, cancelled);
                currentContext = levelResult.updatedContext();
                if (levelResult.anyFailed()) anyFailed = true;
            }
        }

        // Determine phase status
        PhaseStatus phaseStatus = anyFailed ? PhaseStatus.FAILED : PhaseStatus.COMPLETED;

        // Publier PhaseCompleted
        eventPublisher.publishEvent(new PhaseCompleted(executionId, phase, phaseStatus, Instant.now()));
        executionRepository.updatePhase(executionId, phase, phaseStatus);
        log.info("action=phase_completed phase={} status={} executionId={}",
                phase, phaseStatus, executionId.value());

        // Checkpoint
        ActiveExecution exec = activeExecutions.get(executionId.value());
        if (exec != null) {
            ExecutionState updated = updatePhaseInState(exec.state, phase, phaseStatus, currentContext);
            exec.state = updated;
            executionRepository.save(updated);
        }

        return currentContext;
    }

    // ==================== Dispatch & Wait ====================

    /**
     * Dispatch all runnable steps at a DAG level and wait for completions
     * according to the configured completion policy.
     */
    private LevelResult dispatchAndWaitLevel(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            ExecutionId executionId,
            AtomicBoolean cancelled) {

        log.info("action=dispatch_dag_level phase={} steps={} executionId={}",
                phase, steps.size(), executionId.value());

        List<PendingDispatch> dispatched = new ArrayList<>();

        for (ExecutionStep execStep : steps) {
            if (cancelled.get()) break;

            StepDefinition stepDef = execStep.step();

            // 1. Await agent
            availabilityChecker.awaitAgentFor(stepDef.taskName(), config.taskAvailabilityTimeout());
            log.info("action=agent_available taskName={} executionId={}", stepDef.taskName(), executionId.value());

            // 2. Build partial context
            PartialExecutionContext partialCtx = PartialContextBuilder.build(context, execStep.requiredContextKeys());

            // 3. Create request (broadcast, pas de targetAgentId)
            MessageId messageId = MessageId.generate();
            RetryPolicy retry = stepDef.retryPolicy() != null ? stepDef.retryPolicy() : RetryPolicy.defaults();
            TaskExecutionRequest request = new TaskExecutionRequest(
                    messageId, executionId, stepDef, partialCtx, Instant.now(), retry);

            // 4. Dispatch
            transport.dispatchTask(request);

            // 5. Track
            tracker.trackDispatched(messageId, stepDef.id(), executionId);
            PendingDispatch pending = new PendingDispatch(messageId, stepDef.id());
            ActiveExecution exec = activeExecutions.get(executionId.value());
            if (exec != null) {
                exec.pendingDispatches.put(messageId, pending);
            }
            dispatched.add(pending);

            // 6. Publish event
            eventPublisher.publishEvent(new TaskDispatched(
                    executionId, stepDef.id(), stepDef.taskName(), messageId, Instant.now()));
            log.info("action=task_dispatched taskId={} taskName={} messageId={} executionId={}",
                    stepDef.id().value(), stepDef.taskName(), messageId.value(), executionId.value());
        }

        // Wait for all dispatched steps to complete
        ExecutionContext currentContext = context;
        boolean anyFailed = false;
        long deadlineMs = System.currentTimeMillis() + config.taskExecutionTimeout().toMillis();

        for (PendingDispatch pending : dispatched) {
            if (cancelled.get()) {
                // Annulation: marquer comme failed et sortir
                TaskResult cancelResult = TaskResult.failed(pending.taskId, pending.taskId.value(),
                        Duration.ZERO, "Execution cancelled", null);
                currentContext = currentContext.with(pending.taskId.value(), "agent-remote", cancelResult);
                anyFailed = true;
                continue;
            }
            boolean completed = awaitCompletion(pending, deadlineMs, cancelled);
            if (!completed) {
                // Timeout — mark as failed
                TaskResult timeoutResult = TaskResult.failed(pending.taskId, pending.taskId.value(),
                        config.taskExecutionTimeout(), "Task execution timed out", null);
                currentContext = currentContext.with(pending.taskId.value(), "agent-remote", timeoutResult);
                anyFailed = true;
                log.warn("action=task_timeout taskId={} messageId={} executionId={}",
                        pending.taskId.value(), pending.messageId.value(), executionId.value());
            } else {
                // Collect results from all agents that completed
                for (var entry : pending.results.entrySet()) {
                    currentContext = currentContext.with(
                            pending.taskId.value(), entry.getKey().value(), entry.getValue());
                }
                // Check if any agent failed
                boolean hasFailures = pending.results.values().stream()
                        .anyMatch(r -> r.status() == TaskStatus.FAILED);
                if (hasFailures) anyFailed = true;
            }
        }

        return new LevelResult(currentContext, anyFailed);
    }

    /**
     * Poll the tracker until completion policy is satisfied, deadline reached,
     * or execution is cancelled.
     */
    private boolean awaitCompletion(PendingDispatch pending, long deadlineMs, AtomicBoolean cancelled) {
        TaskCompletionPolicy policy = config.completionPolicy();

        while (System.currentTimeMillis() < deadlineMs) {
            if (cancelled.get()) {
                log.info("action=await_completion_cancelled messageId={}", pending.messageId.value());
                return false;
            }
            if (tracker.isComplete(pending.messageId, policy)) {
                log.debug("action=completion_satisfied messageId={} taskId={} policy={}",
                        pending.messageId.value(), pending.taskId.value(), policy);
                return true;
            }

            long remainingMs = Math.min(POLL_COMPLETION_MS, deadlineMs - System.currentTimeMillis());
            if (remainingMs <= 0) break;

            try {
                Thread.sleep(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("action=await_completion_interrupted messageId={}", pending.messageId.value());
                return false;
            }
        }

        log.warn("action=completion_timeout messageId={} taskId={} policy={}",
                pending.messageId.value(), pending.taskId.value(), policy);
        return false;
    }

    // ==================== Event Handler ====================

    /**
     * Handler appele par le transport pour chaque {@link ExecutionEvent} recu.
     * Filtre par executionId et correlationId pour ne traiter que les events
     * pertinents pour cette instance d'engine.
     */
    private void onTransportEvent(ExecutionEvent event) {
        if (event == null || event.executionId() == null) return;

        ActiveExecution exec = activeExecutions.get(event.executionId().value());
        if (exec == null) return;

        MessageId correlationId = event.correlationId();
        if (correlationId == null) return;

        PendingDispatch pending = exec.pendingDispatches.get(correlationId);
        if (pending == null) return;

        String eventType = event.eventType();
        if (eventType == null) return;

        switch (eventType) {
            case ExecutionEvent.TASK_CLAIMED -> handleTaskClaimed(event, pending);
            case ExecutionEvent.TASK_COMPLETED -> handleTaskCompleted(event, pending);
            case ExecutionEvent.TASK_FAILED -> handleTaskFailed(event, pending);
            case ExecutionEvent.TASK_WORK_IN_PROGRESS -> handleTaskWorkInProgress(event, pending);
            default -> {
                // Ignorer les autres types d'events (heartbeat, registration, etc.)
            }
        }
    }

    private void handleTaskClaimed(ExecutionEvent event, PendingDispatch pending) {
        if (event.agentId() == null) return;
        tracker.onClaimed(pending.messageId, event.agentId());
        log.info("action=task_claimed messageId={} agentId={} executionId={}",
                pending.messageId.value(), event.agentId().value(), event.executionId().value());
        eventPublisher.publishEvent(new TaskClaimedByAgent(
                event.executionId(), pending.taskId, event.agentId(),
                pending.messageId, event.occurredAt()));
    }

    private void handleTaskCompleted(ExecutionEvent event, PendingDispatch pending) {
        if (event.agentId() == null) return;

        TaskResult result = reconstructTaskResult(event, pending.taskId);
        pending.results.put(event.agentId(), result);
        tracker.onCompleted(pending.messageId, event.agentId(), result);
        executionRepository.saveTaskResult(event.executionId(), pending.taskId, event.agentId(), result);

        log.info("action=task_completed messageId={} agentId={} taskId={} status={} executionId={}",
                pending.messageId.value(), event.agentId().value(),
                pending.taskId.value(), result.status(), event.executionId().value());

        eventPublisher.publishEvent(new TaskCompleted(
                event.executionId(), pending.taskId, event.agentId(),
                result, result.duration(), event.occurredAt()));
    }

    private void handleTaskFailed(ExecutionEvent event, PendingDispatch pending) {
        if (event.agentId() == null) return;

        String error = (String) event.payload().getOrDefault(PAYLOAD_ERROR, "unknown error");
        tracker.onFailed(pending.messageId, event.agentId(), error);

        // Creer un TaskResult failed et le stocker
        TaskResult failedResult = TaskResult.failed(pending.taskId, pending.taskId.value(),
                Duration.ZERO, error, null);
        pending.results.put(event.agentId(), failedResult);

        log.warn("action=task_failed messageId={} agentId={} taskId={} error={} executionId={}",
                pending.messageId.value(), event.agentId().value(),
                pending.taskId.value(), error, event.executionId().value());
    }

    private void handleTaskWorkInProgress(ExecutionEvent event, PendingDispatch pending) {
        // Reset le timeout de completion — l'agent est vivant
        pending.lastProgressTime = System.currentTimeMillis();
        log.debug("action=task_progress messageId={} agentId={} executionId={}",
                pending.messageId.value(),
                event.agentId() != null ? event.agentId().value() : "unknown",
                event.executionId().value());
    }

    /**
     * Reconstruit un {@link TaskResult} a partir du payload d'un
     * {@link ExecutionEvent} de type {@code TASK_COMPLETED}.
     */
    @SuppressWarnings("unchecked")
    private TaskResult reconstructTaskResult(ExecutionEvent event, TaskId taskId) {
        Map<String, Object> payload = event.payload();

        String statusStr = (String) payload.getOrDefault(PAYLOAD_STATUS, "SUCCESS");
        TaskStatus status = TaskStatus.valueOf(statusStr);

        Duration duration;
        Object durationObj = payload.get(PAYLOAD_DURATION_MS);
        if (durationObj instanceof Number num) {
            duration = Duration.ofMillis(num.longValue());
        } else {
            duration = Duration.ZERO;
        }

        Map<String, Object> outputs;
        Object outputsObj = payload.get(PAYLOAD_OUTPUTS);
        if (outputsObj instanceof Map<?, ?> map) {
            outputs = (Map<String, Object>) map;
        } else {
            outputs = Map.of();
        }

        String errorMessage = null;
        if (status == TaskStatus.FAILED) {
            errorMessage = (String) payload.getOrDefault(PAYLOAD_ERROR, "failed");
        }

        Instant completedAt = event.occurredAt();

        return new TaskResult(taskId, taskId.value(), status, duration, outputs, errorMessage, null, completedAt);
    }

    // ==================== Verdict & Finalization ====================

    private Verdict computeVerdict(ExecutionContext context, ExecutionPlan plan,
                                    boolean hasFailure, boolean cancelled) {
        if (cancelled || hasFailure) {
            return Verdict.FAILED;
        }
        boolean hasSkipped = checkSkippedInAnyPhase(context, plan);
        return hasSkipped ? Verdict.WARNING : Verdict.SUCCESS;
    }

    private void applyVerdict(ExecutionId executionId, ScenarioId scenarioId,
                               ExecutionContext ctx, Verdict verdict, boolean cancelled) {
        ActiveExecution exec = activeExecutions.get(executionId.value());
        if (exec == null) return;

        ExecutionStatus newStatus;
        if (cancelled) {
            newStatus = ExecutionStatus.CANCELLED;
            eventPublisher.publishEvent(new ScenarioCancelled(
                    executionId, scenarioId, "cancelled by user", Instant.now()));
            log.info("action=scenario_cancelled executionId={}", executionId.value());
        } else if (verdict == Verdict.FAILED) {
            newStatus = ExecutionStatus.FAILED;
        } else {
            newStatus = ExecutionStatus.COMPLETED;
        }
        exec.state = updateState(exec.state, newStatus, ctx);
    }

    private void finalizeExecution(ExecutionId executionId, ScenarioId scenarioId,
                                    Verdict verdict, Instant start) {
        ActiveExecution exec = activeExecutions.remove(executionId.value());
        if (exec != null) {
            executionRepository.save(exec.state);
        }

        Duration totalDuration = Duration.between(start, Instant.now());
        eventPublisher.publishEvent(new ScenarioFinished(
                executionId, scenarioId, verdict, totalDuration, Instant.now()));
        log.info("action=scenario_finished executionId={} verdict={} durationMs={}",
                executionId.value(), verdict, totalDuration.toMillis());
    }

    // ==================== Helpers ====================

    private AtomicBoolean cancelFlags(ExecutionId executionId) {
        ActiveExecution exec = activeExecutions.get(executionId.value());
        return exec != null ? exec.cancelFlag : new AtomicBoolean(false);
    }

    private Map<Integer, List<ExecutionStep>> groupStepsByLevel(List<ExecutionStep> steps) {
        return steps.stream().collect(Collectors.groupingBy(ExecutionStep::dagLevel));
    }

    private record LevelClassification(List<ExecutionStep> runnable, List<ExecutionStep> skippable) {}

    private LevelClassification classifySteps(List<ExecutionStep> steps, ExecutionContext context) {
        List<ExecutionStep> runnable = new ArrayList<>();
        List<ExecutionStep> skippable = new ArrayList<>();
        for (ExecutionStep step : steps) {
            if (allDependenciesSatisfied(step, context)) {
                runnable.add(step);
            } else {
                skippable.add(step);
            }
        }
        return new LevelClassification(runnable, skippable);
    }

    private boolean allDependenciesSatisfied(ExecutionStep step, ExecutionContext context) {
        List<TaskId> deps = step.dependencies();
        if (deps == null || deps.isEmpty()) return true;
        for (TaskId dep : deps) {
            var agentResults = context.store().get(dep.value());
            if (agentResults == null) return false;
            boolean anySuccess = agentResults.values().stream()
                    .anyMatch(TaskResult::isSuccess);
            if (!anySuccess) return false;
        }
        return true;
    }

    private boolean checkFailedInPhase(ExecutionContext context, List<ExecutionStep> steps) {
        for (var step : steps) {
            var agentResults = context.store().get(step.step().id().value());
            if (agentResults != null) {
                boolean anyFailed = agentResults.values().stream()
                        .anyMatch(r -> r.status() == TaskStatus.FAILED);
                if (anyFailed) return true;
            }
        }
        return false;
    }

    private boolean checkSkippedInAnyPhase(ExecutionContext context, ExecutionPlan plan) {
        return checkSkippedInSteps(context, plan.preparationSteps())
                || checkSkippedInSteps(context, plan.injectionSteps())
                || checkSkippedInSteps(context, plan.assertionSteps());
    }

    private boolean checkSkippedInSteps(ExecutionContext context, List<ExecutionStep> steps) {
        for (var step : steps) {
            var agentResults = context.store().get(step.step().id().value());
            if (agentResults != null) {
                boolean anySkipped = agentResults.values().stream()
                        .anyMatch(r -> r.status() == TaskStatus.SKIPPED);
                if (anySkipped) return true;
            }
        }
        return false;
    }

    private ExecutionState createInitialState(ExecutionId id, ScenarioId scenarioId, ExecutionContext context) {
        Instant now = Instant.now();
        Map<Phase, PhaseStatus> phases = new EnumMap<>(Phase.class);
        phases.put(Phase.PREPARATION, PhaseStatus.PENDING);
        phases.put(Phase.INJECTION, PhaseStatus.PENDING);
        phases.put(Phase.ASSERTION, PhaseStatus.PENDING);
        return new ExecutionState(id, scenarioId, ExecutionStatus.RUNNING, phases, context, now, now);
    }

    private ExecutionState updateState(ExecutionState current, ExecutionStatus newStatus, ExecutionContext newContext) {
        return new ExecutionState(
                current.id(), current.scenarioId(), newStatus,
                current.phaseStatuses(), newContext,
                current.startedAt(), Instant.now());
    }

    private ExecutionState updatePhaseInState(ExecutionState current, Phase phase,
                                               PhaseStatus phaseStatus, ExecutionContext newContext) {
        Map<Phase, PhaseStatus> updatedPhases = new EnumMap<>(current.phaseStatuses());
        updatedPhases.put(phase, phaseStatus);
        return new ExecutionState(
                current.id(), current.scenarioId(), current.status(),
                updatedPhases, newContext,
                current.startedAt(), Instant.now());
    }

    // ==================== Inner Types ====================

    /** Result of executing one DAG level. */
    private record LevelResult(ExecutionContext updatedContext, boolean anyFailed) {}

    /**
     * Etat d'une tache dispatch en attente de resultats.
     */
    private static class PendingDispatch {
        final MessageId messageId;
        final TaskId taskId;
        final Map<AgentId, TaskResult> results = new ConcurrentHashMap<>();
        volatile long lastProgressTime = System.currentTimeMillis();

        PendingDispatch(MessageId messageId, TaskId taskId) {
            this.messageId = messageId;
            this.taskId = taskId;
        }
    }

    /**
     * Etat complet d'une execution en cours, partage entre le thread
     * d'execution principal (Virtual Thread) et le handler d'events du
     * transport (potentiellement appele depuis un autre thread).
     */
    private static class ActiveExecution {
        volatile ExecutionState state;
        final AtomicBoolean cancelFlag = new AtomicBoolean(false);
        final Map<MessageId, PendingDispatch> pendingDispatches = new ConcurrentHashMap<>();

        ActiveExecution(ExecutionState state) {
            this.state = state;
        }
    }
}
