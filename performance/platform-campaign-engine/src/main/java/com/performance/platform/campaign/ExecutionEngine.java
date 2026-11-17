package com.performance.platform.campaign;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.ports.in.CancelExecutionUseCase;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
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
import com.performance.platform.domain.execution.ExecutionTask;
import com.performance.platform.domain.execution.ScenarioDetails;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.application.ports.out.ExecutionPlanBuilder;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import com.performance.platform.engine.shared.DagPhaseExecutor;
import com.performance.platform.engine.shared.StepDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Moteur d'execution unique, independant du mode LOCAL/DISTRIBUTED.
 * <p>
 * Il n'y a plus de fork du moteur ni de strategie par mode : le
 * {@link StepDispatcher} injecte ({@code TransportStepDispatcher}) est le
 * meme partout (ADR-059). Le moteur partage exactement le meme parcours de
 * phases, le meme calcul de verdict et la meme machine d'etat.
 * <p>
 * Les evenements de tache sont publies par {@code TransportExecutionEventHandler}
 * dans tous les modes. Le moteur ne connait ni le transport ni le tracker.
 * <p>
 * Actif en LOCAL et en DISTRIBUTED + ORCHESTRATOR.
 */
@Service
@ConditionalOnExpression(
    "'${runtime.mode:LOCAL}'.equals('LOCAL') || ('${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
    + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR'))")
public class ExecutionEngine implements ExecuteScenarioUseCase, SubmitScenarioUseCase, CancelExecutionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final ExecutionPlanBuilder planBuilder;
    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutionLifecycleDispatcher lifecycleDispatcher;
    private final StepDispatcher stepDispatcher;
    private final DagPhaseExecutor dagPhaseExecutor;

    /** Suivi en memoire des executions actives. Cle : executionId.value(). */
    private final Map<String, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();
    private final ExecutionGroups executionGroups = new ExecutionGroups();

    public ExecutionEngine(
            ExecutionPlanBuilder planBuilder,
            ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher,
            ExecutionLifecycleDispatcher lifecycleDispatcher,
            StepDispatcher stepDispatcher) {
        this.planBuilder = planBuilder;
        this.executionRepository = executionRepository;
        this.eventPublisher = eventPublisher;
        this.lifecycleDispatcher = lifecycleDispatcher;
        this.stepDispatcher = stepDispatcher;
        this.dagPhaseExecutor = new DagPhaseExecutor(stepDispatcher, lifecycleDispatcher);
    }

    // ==================== UseCase API ====================

    @Override
    public ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException {
        return runExecution(prepareExecution(scenario), scenario.metadata().get("executionGroup"));
    }

    @Override
    public ExecutionId submit(ScenarioDefinition scenario) {
        ExecutionPlan plan = prepareExecution(scenario);
        try {
            Thread.ofVirtual().name("execution-" + plan.id().value()).start(
                    () -> runExecution(plan, scenario.metadata().get("executionGroup")));
        } catch (RuntimeException e) {
            applyVerdict(plan.id(), plan.scenarioId(), plan.initialContext(), Verdict.FAILED, false);
            finalizeExecution(plan.id(), plan.scenarioId(), Verdict.FAILED, Instant.now());
            throw e;
        }
        return plan.id();
    }

    private ExecutionPlan prepareExecution(ScenarioDefinition scenario) {
        if (scenario == null) {
            throw new ExecutionException("ScenarioDefinition must not be null");
        }
        if (scenario.id() == null) {
            throw new ExecutionException("ScenarioDefinition.id must not be null");
        }

        return initializeExecution(scenario);
    }

    private ExecutionId runExecution(ExecutionPlan plan, String group) {
        boolean acquired = false;
        try {
            if (group != null && !group.isBlank()) {
                log.info("action=execution_group_wait executionId={} group={}", plan.id().value(), group);
                acquired = executionGroups.acquire(group, cancelFlags(plan.id()));
                if (!acquired) {
                    applyVerdict(plan.id(), plan.scenarioId(), plan.initialContext(), Verdict.FAILED, true);
                    finalizeExecution(plan.id(), plan.scenarioId(), Verdict.FAILED, Instant.now());
                    return plan.id();
                }
                log.info("action=execution_group_acquired executionId={} group={}", plan.id().value(), group);
            }
            return runExecution(plan);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("action=execution_group_interrupted executionId={} group={}", plan.id().value(), group, e);
            applyVerdict(plan.id(), plan.scenarioId(), plan.initialContext(), Verdict.FAILED, true);
            finalizeExecution(plan.id(), plan.scenarioId(), Verdict.FAILED, Instant.now());
            return plan.id();
        } finally {
            if (acquired) {
                executionGroups.release(group);
                log.info("action=execution_group_released executionId={} group={}", plan.id().value(), group);
            }
        }
    }

    private ExecutionId runExecution(ExecutionPlan plan) {
        ExecutionId executionId = plan.id();
        AtomicBoolean cancelled = cancelFlags(executionId);
        ExecutionContext ctx = plan.initialContext();
        Instant start = Instant.now();
        boolean hasFailure = false;
        Verdict verdict = Verdict.SUCCESS;

        // ADR-054 : le STOP appartient au moteur. Un run echoue ou annule
        // arrete quand meme les services demarres (finally).
        var startedServices = new java.util.ArrayDeque<ExecutionStep>();

        try {
            startedServices.addAll(startServices(plan.scenarioServices(), ctx, executionId));

            ctx = executePhase(Phase.PREPARATION, plan.preparationSteps(), ctx, executionId, cancelled);
            if (checkFailedInPhase(ctx, plan.preparationSteps())) {
                hasFailure = true;
            }

            startedServices.addAll(startServices(plan.injectionServices(), ctx, executionId));

            ctx = executePhase(Phase.INJECTION, plan.injectionSteps(), ctx, executionId, cancelled);
            if (checkFailedInPhase(ctx, plan.injectionSteps())) {
                hasFailure = true;
            }

            ctx = stopServices(plan.injectionServices(), ctx, executionId);
            startedServices.removeAll(plan.injectionServices());

            ctx = executePhase(Phase.ASSERTION, plan.assertionSteps(), ctx, executionId, cancelled);
            if (checkFailedInPhase(ctx, plan.assertionSteps())) {
                hasFailure = true;
            }

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
        } finally {
            if (!startedServices.isEmpty()) {
                var remaining = new ArrayList<>(startedServices);
                java.util.Collections.reverse(remaining);
                try {
                    ctx = stopServices(remaining, ctx, executionId);
                } catch (RuntimeException e) {
                    log.warn("action=service_cleanup_failed executionId={} error={}",
                            executionId.value(), e.getMessage());
                }
            }
        }

        finalizeExecution(executionId, plan.scenarioId(), verdict, start);
        return executionId;
    }

    /**
     * Demarre les service steps de la portee (ADR-054). Un echec de demarrage
     * fait echouer l'execution : le service est indispensable au scenario.
     */
    private List<ExecutionStep> startServices(List<ExecutionStep> services, ExecutionContext context,
                                              ExecutionId executionId) {
        var started = new ArrayList<ExecutionStep>(services.size());
        for (ExecutionStep service : services) {
            markTaskStarted(executionId, service.step().id());
            stepDispatcher.startService(service, context);
            started.add(service);
            log.info("action=service_started taskId={} executionId={}",
                    service.step().id().value(), executionId.value());
        }
        return started;
    }

    /**
     * Arrete les service steps de la portee et integre leur resultat (publie au
     * STOP) au contexte d'execution, ou les assertions le lisent via {@code ref}.
     */
    private ExecutionContext stopServices(List<ExecutionStep> services, ExecutionContext context,
                                          ExecutionId executionId) {
        ExecutionContext current = context;
        for (ExecutionStep service : services) {
            try {
                var results = stepDispatcher.stopService(service, current);
                for (var dispatchResult : results) {
                    current = current.with(service.step().id().value(),
                            dispatchResult.agentId().value(), dispatchResult.result());
                }
            } catch (RuntimeException e) {
                log.warn("action=service_stop_failed taskId={} executionId={} error={}",
                        service.step().id().value(), executionId.value(), e.getMessage());
            }
            log.info("action=service_stopped taskId={} executionId={}",
                    service.step().id().value(), executionId.value());
        }
        return current;
    }

    private void markTaskStarted(ExecutionId executionId, TaskId taskId) {
        ActiveExecution exec = activeExecutions.get(executionId.value());
        if (exec == null) {
            return;
        }
        synchronized (exec) {
            ExecutionState current = exec.state;
            exec.state = new ExecutionState(current.id(), current.scenarioId(), current.status(),
                    current.phaseStatuses(), current.context(), current.startedAt(), Instant.now(),
                    current.plannedTaskCount(), current.tasks().stream()
                            .map(task -> task.id().equals(taskId) ? task.start() : task).toList(),
                    current.scenarioYaml(), current.scenarioDetails());
            executionRepository.save(exec.state);
        }
    }

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

        ExecutionState state = createInitialState(
                executionId, scenarioId, plan.initialContext(), scenario);
        // Persist the initial row before any updatePhase: the JPA repository
        // requires the execution to exist (and the CLI/API poll it).
        executionRepository.save(state);
        activeExecutions.put(executionId.value(), new ActiveExecution(state));

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

        eventPublisher.publishEvent(new PhaseStarted(executionId, phase, Instant.now()));
        ActiveExecution active = activeExecutions.get(executionId.value());
        synchronized (active) {
            active.state = updatePhaseInState(active.state, phase, PhaseStatus.RUNNING, context);
            executionRepository.save(active.state);
        }
        log.info("action=phase_started phase={} executionId={}", phase, executionId.value());

        DagPhaseExecutor.PhaseResult result = dagPhaseExecutor.executePhase(
                steps, context, phase, cancelled);

        // Les resultats dispatches sont deja publies par l'agent (TaskCompleted
        // via le handler d'evenements). Seuls les steps ignores par dependance
        // sont publies ici.
        publishSkippedOutcomes(executionId, result.outcomes());

        boolean missingOutcomes = !cancelled.get() && result.outcomes().stream()
                .map(DagPhaseExecutor.StepOutcome::taskId).distinct().count() != steps.size();
        boolean anyFailedInPhase = missingOutcomes || checkFailedInPhase(result.updatedContext(), steps);
        PhaseStatus phaseStatus = anyFailedInPhase ? PhaseStatus.FAILED : PhaseStatus.COMPLETED;

        eventPublisher.publishEvent(new PhaseCompleted(executionId, phase, phaseStatus, Instant.now()));
        executionRepository.updatePhase(executionId, phase, phaseStatus);
        log.info("action=phase_completed phase={} status={} executionId={}",
                phase, phaseStatus, executionId.value());

        ActiveExecution exec = activeExecutions.get(executionId.value());
        if (exec != null) {
            synchronized (exec) {
                exec.state = updatePhaseInState(exec.state, phase, phaseStatus, result.updatedContext());
                executionRepository.save(exec.state);
            }
        }

        if (missingOutcomes) {
            throw new ExecutionException("Phase " + phase + " ended without results for all dispatched steps");
        }
        return result.updatedContext();
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
        if (exec == null) {
            return;
        }

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

        var totalDuration = Duration.between(start, Instant.now());
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

    private boolean checkFailedInPhase(ExecutionContext context, List<ExecutionStep> steps) {
        for (var step : steps) {
            var agentResults = context.store().get(step.step().id().value());
            if (agentResults != null) {
                boolean anyFailed = agentResults.values().stream()
                        .anyMatch(r -> r.status() == TaskStatus.FAILED);
                if (anyFailed) {
                    return true;
                }
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
                if (anySkipped) {
                    return true;
                }
            }
        }
        return false;
    }

    private void publishSkippedOutcomes(ExecutionId executionId,
                                        List<DagPhaseExecutor.StepOutcome> outcomes) {
        var now = Instant.now();
        for (var outcome : outcomes) {
            if (outcome.status() != TaskStatus.SKIPPED) {
                continue;
            }
            executionRepository.saveTaskResult(
                    executionId, outcome.taskId(), outcome.agentId(), outcome.result());
            eventPublisher.publishEvent(new TaskCompleted(
                    executionId, outcome.taskId(), outcome.agentId(),
                    outcome.result(), outcome.result().duration(), now));
        }
    }

    private ExecutionState createInitialState(ExecutionId id, ScenarioId scenarioId,
                                              ExecutionContext context, ScenarioDefinition scenario) {
        var now = Instant.now();
        Map<Phase, PhaseStatus> phases = new EnumMap<>(Phase.class);
        phases.put(Phase.PREPARATION, PhaseStatus.PENDING);
        phases.put(Phase.INJECTION, PhaseStatus.PENDING);
        phases.put(Phase.ASSERTION, PhaseStatus.PENDING);
        return new ExecutionState(
                id, scenarioId, ExecutionStatus.RUNNING, phases, context, now, now, scenario.steps().size(),
                scenario.steps().stream().map(step ->
                        new ExecutionTask(step.id(), step.taskName(), displayPhase(step), false,
                                taskConfiguration(step, scenario))).toList(), scenario.sourceYaml(),
                new ScenarioDetails(scenario.name(), scenario.version(), scenario.tags(), scenario.metadata(), scenario.report()));
    }

    /**
     * Une service step n'a pas de phase (ADR-054) : pour le read-model et le
     * rapport, elle est rattachee a la phase qu'elle sert.
     */
    private static Phase displayPhase(StepDefinition step) {
        if (step.phase() != null) {
            return step.phase();
        }
        return step.during() == com.performance.platform.domain.scenario.ServiceScope.SCENARIO
                ? Phase.PREPARATION
                : Phase.INJECTION;
    }

    private Map<String, Object> taskConfiguration(StepDefinition step, ScenarioDefinition scenario) {
        var configuration = new java.util.LinkedHashMap<String, Object>();
        configuration.put("parameters", step.parameters());
        configuration.put("dependsOn", step.dependsOn().stream().map(TaskId::value).toList());
        configuration.put("requiredContexts", step.resolvedRequiredContexts());
        if (step.ref() != null) {
            configuration.put("ref", step.ref().value());
        }
        if (step.during() != null) {
            configuration.put("during", step.during().name());
        }
        if (!step.drain().isZero()) {
            configuration.put("drain", step.drain().toString());
        }
        configuration.put("fanOut", step.fanOut().name());
        if (step.timeout() != null) {
            configuration.put("timeout", step.timeout().toString());
        }
        if (step.retryPolicy() != null) {
            var retry = step.retryPolicy();
            configuration.put("retryPolicy", Map.of(
                    "maxAttempts", retry.maxAttempts(), "initialDelay", retry.initialDelay().toString(),
                    "multiplier", retry.multiplier(), "maxDelay", retry.maxDelay().toString(),
                    "retryableExceptions", retry.retryableExceptions().stream().map(Class::getName).sorted().toList()));
        }
        return configuration;
    }

    private ExecutionState updateState(ExecutionState current, ExecutionStatus newStatus,
                                       ExecutionContext newContext) {
        return new ExecutionState(
                current.id(), current.scenarioId(), newStatus,
                current.phaseStatuses(), newContext,
                current.startedAt(), Instant.now(), current.plannedTaskCount(), current.tasks(),
                current.scenarioYaml(), current.scenarioDetails());
    }

    private ExecutionState updatePhaseInState(ExecutionState current, Phase phase,
                                              PhaseStatus phaseStatus, ExecutionContext newContext) {
        Map<Phase, PhaseStatus> updatedPhases = new EnumMap<>(current.phaseStatuses());
        updatedPhases.put(phase, phaseStatus);
        return new ExecutionState(
                current.id(), current.scenarioId(), current.status(),
                updatedPhases, newContext,
                current.startedAt(), Instant.now(), current.plannedTaskCount(), current.tasks(),
                current.scenarioYaml(), current.scenarioDetails());
    }

    /** Etat partage entre le thread d'execution et les handlers. */
    private static final class ActiveExecution {
        final AtomicBoolean cancelFlag = new AtomicBoolean(false);
        volatile ExecutionState state;

        ActiveExecution(ExecutionState state) {
            this.state = state;
        }
    }
}
