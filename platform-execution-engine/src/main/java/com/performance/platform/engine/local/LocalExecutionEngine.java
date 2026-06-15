package com.performance.platform.engine.local;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.PhaseCompleted;
import com.performance.platform.domain.event.PhaseStarted;
import com.performance.platform.domain.event.ScenarioCancelled;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.ScenarioStarted;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.ExecutionEngine;
import com.performance.platform.engine.plan.ExecutionPlanBuilder;
import com.performance.platform.engine.retry.RetryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation locale (in-process) de {@link ExecutionEngine}.
 * Execute les 3 phases en sequence :
 * <ol>
 *   <li>PREPARATION — setup, donnees de test</li>
 *   <li>INJECTION — generation de charge</li>
 *   <li>ASSERTION — verification des resultats (toujours executee)</li>
 * </ol>
 *
 * <p>Au sein de chaque phase, les etapes sont regroupees par {@code dagLevel}
 * et executees en parallele via Virtual Threads.
 * L'annulation est cooperative : le flag est verifie entre les niveaux DAG
 * et entre les phases.</p>
 *
 * <p>Checkpoint via {@link ExecutionRepository#save} apres chaque phase.
 * Les events de domaine {@link ScenarioStarted}, {@link PhaseStarted},
 * {@link PhaseCompleted}, {@link TaskCompleted}, {@link TaskFailed},
 * et {@link ScenarioFinished} sont publies via {@link ApplicationEventPublisher}.</p>
 *
 * <p>Active uniquement quand {@code runtime.mode=LOCAL}.</p>
 */
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(LocalExecutionEngine.class);

    private final ExecutionPlanBuilder planBuilder;
    private final RetryExecutor retryExecutor;
    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecutorLookup taskExecutorLookup;
    private final DagPhaseExecutor dagPhaseExecutor;

    /**
     * Suivi en memoire des executions en cours pour getStatus() et cancel().
     * Cle : executionId.value() → AtomicBoolean d'annulation.
     */
    private final Map<String, ExecutionState> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public LocalExecutionEngine(
            ExecutionPlanBuilder planBuilder,
            RetryExecutor retryExecutor,
            ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher,
            TaskExecutorLookup taskExecutorLookup) {
        this.planBuilder = planBuilder;
        this.retryExecutor = retryExecutor;
        this.executionRepository = executionRepository;
        this.eventPublisher = eventPublisher;
        this.taskExecutorLookup = taskExecutorLookup;
        this.dagPhaseExecutor = new DagPhaseExecutor(retryExecutor);
    }

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
        AtomicBoolean cancelled = cancelFlags.get(executionId.value());
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
        } catch (Exception e) {
            log.error("action=execute_unexpected_error executionId={} error={}",
                    executionId.value(), e.getMessage(), e);
            verdict = Verdict.FAILED;
            applyVerdict(executionId, plan.scenarioId(), ctx, verdict, false);
        }

        finalizeExecution(executionId, plan.scenarioId(), verdict, start);
        return executionId;
    }

    private ExecutionPlan initializeExecution(ScenarioDefinition scenario) {
        ExecutionPlan plan = planBuilder.build(scenario);
        ExecutionId executionId = plan.id();
        ScenarioId scenarioId = scenario.id();
        log.info("action=execute_start executionId={} scenarioId={} scenarioName={}",
                executionId.value(), scenarioId.value(), scenario.name());

        ExecutionState state = createInitialState(executionId, scenarioId, plan.initialContext());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeExecutions.put(executionId.value(), state);
        cancelFlags.put(executionId.value(), cancelled);

        eventPublisher.publishEvent(new ScenarioStarted(executionId, scenarioId, Instant.now()));
        log.info("action=scenario_started executionId={}", executionId.value());

        return plan;
    }

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
        ExecutionState state = activeExecutions.get(executionId.value());
        if (state == null) return;

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
        state = updateState(state, newStatus, ctx);
        activeExecutions.put(executionId.value(), state);
    }

    private void finalizeExecution(ExecutionId executionId, ScenarioId scenarioId,
                                    Verdict verdict, Instant start) {
        ExecutionState state = activeExecutions.get(executionId.value());
        if (state != null) {
            executionRepository.save(state);
        }

        Duration totalDuration = Duration.between(start, Instant.now());
        eventPublisher.publishEvent(new ScenarioFinished(
                executionId, scenarioId, verdict, totalDuration, Instant.now()));
        log.info("action=scenario_finished executionId={} verdict={} durationMs={}",
                executionId.value(), verdict, totalDuration.toMillis());

        activeExecutions.remove(executionId.value());
        cancelFlags.remove(executionId.value());
    }

    @Override
    public ExecutionStatus getStatus(ExecutionId id) {
        ExecutionState state = activeExecutions.get(id.value());
        if (state != null) {
            return state.status();
        }
        Optional<ExecutionState> persisted = executionRepository.findById(id);
        return persisted.map(ExecutionState::status)
                .orElseThrow(() -> new ExecutionException("Execution not found: " + id.value()));
    }

    @Override
    public void cancel(ExecutionId id) {
        AtomicBoolean flag = cancelFlags.get(id.value());
        if (flag != null) {
            flag.set(true);
            log.info("action=cancel_requested executionId={}", id.value());
        } else {
            // L'execution est peut-etre deja terminee
            log.warn("action=cancel_unknown executionId={}", id.value());
        }
    }

    /**
     * Execute une phase complete via {@link DagPhaseExecutor} et met a jour
     * le repository avec checkpoint et transition de statut.
     */
    private ExecutionContext executePhase(
            Phase phase,
            java.util.List<com.performance.platform.domain.execution.ExecutionStep> steps,
            ExecutionContext context,
            ExecutionId executionId,
            AtomicBoolean cancelled) {

        // Publier PhaseStarted
        eventPublisher.publishEvent(new PhaseStarted(executionId, phase, Instant.now()));
        executionRepository.updatePhase(executionId, phase, PhaseStatus.RUNNING);
        log.info("action=phase_started phase={} executionId={}", phase, executionId.value());

        // Executer les etapes de la phase
        DagPhaseExecutor.PhaseResult result = dagPhaseExecutor.executePhase(
                steps, context, phase, taskExecutorLookup, eventPublisher, cancelled);

        // Determiner le statut final de la phase
        boolean anyFailedInPhase = checkFailedInPhase(result.updatedContext(), steps);
        PhaseStatus phaseStatus = anyFailedInPhase ? PhaseStatus.FAILED : PhaseStatus.COMPLETED;

        // Publier PhaseCompleted
        eventPublisher.publishEvent(new PhaseCompleted(executionId, phase, phaseStatus, Instant.now()));
        executionRepository.updatePhase(executionId, phase, phaseStatus);
        log.info("action=phase_completed phase={} status={} executionId={}",
                phase, phaseStatus, executionId.value());

        // Checkpoint : sauvegarder l'etat courant
        ExecutionState currentState = activeExecutions.get(executionId.value());
        if (currentState != null) {
            ExecutionState updated = updatePhaseInState(currentState, phase, phaseStatus, result.updatedContext());
            activeExecutions.put(executionId.value(), updated);
            executionRepository.save(updated);
        }

        return result.updatedContext();
    }

    /**
     * Verifie si une phase contient au moins un resultat FAILED.
     * Utilise {@code context.store()} pour acceder directement aux TaskResult,
     * car {@code context.get()} retourne les valeurs des outputs, pas les TaskResult.
     */
    private boolean checkFailedInPhase(ExecutionContext context,
                                        java.util.List<com.performance.platform.domain.execution.ExecutionStep> steps) {
        for (var step : steps) {
            var agentResults = context.store().get(step.step().id().value());
            if (agentResults != null) {
                var result = agentResults.get(DagPhaseExecutor.LOCAL_AGENT);
                if (result != null && result.status() == TaskStatus.FAILED) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifie si une phase (quelconque) contient un resultat SKIPPED.
     */
    private boolean checkSkippedInAnyPhase(ExecutionContext context, ExecutionPlan plan) {
        return checkSkippedInSteps(context, plan.preparationSteps())
                || checkSkippedInSteps(context, plan.injectionSteps())
                || checkSkippedInSteps(context, plan.assertionSteps());
    }

    private boolean checkSkippedInSteps(ExecutionContext context,
                                         java.util.List<com.performance.platform.domain.execution.ExecutionStep> steps) {
        for (var step : steps) {
            var agentResults = context.store().get(step.step().id().value());
            if (agentResults != null) {
                var result = agentResults.get(DagPhaseExecutor.LOCAL_AGENT);
                if (result != null && result.status() == TaskStatus.SKIPPED) return true;
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
}
