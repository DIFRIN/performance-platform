package com.performance.platform.engine.shared;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Execute une phase (PREPARATION / INJECTION / ASSERTION) niveau DAG par niveau DAG.
 * Les etapes d'un meme dagLevel sont executees en parallele via Virtual Threads.
 *
 * <p>Cette classe est utilisee par l'{@code ExecutionEngine} unique.
 * La strategie d'execution d'un step individuel est deleguee au
 * {@link StepDispatcher}.</p>
 *
 * <p>Regles :
 * <ul>
 *   <li>Avant l'execution d'un niveau, verification des prerequis : si un dependsOn est
 *       FAILED/SKIPPED, l'etape est marquee SKIPPED.</li>
 *   <li>Chaque etape est dispatchee via {@link StepDispatcher}.</li>
 *   <li>Chaque resultat par agent est stocke dans le {@link ExecutionContext}.</li>
 *   <li>La publication des events de domaine (TaskCompleted, TaskFailed) est
 *       a la charge de l'engine appelant, voir {@link PhaseResult#outcomes()}.</li>
 * </ul>
 */
public class DagPhaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagPhaseExecutor.class);

    public static final String DEFAULT_AGENT = "agent-local";

    private final StepDispatcher dispatcher;
    private final ExecutionLifecycleDispatcher lifecycleDispatcher;

    public DagPhaseExecutor(StepDispatcher dispatcher, ExecutionLifecycleDispatcher lifecycleDispatcher) {
        this.dispatcher = dispatcher;
        this.lifecycleDispatcher = lifecycleDispatcher;
    }

    /**
     * Resultat d'un step individuel apres dispatch.
     */
    public record StepOutcome(TaskId taskId, AgentId agentId, String taskName,
                              TaskResult result, TaskStatus status) {}

    /**
     * Resultat de l'execution d'une phase.
     *
     * @param updatedContext le contexte d'execution mis a jour avec les resultats de la phase
     * @param anyFailed      true si au moins une etape de cette phase a echoue (FAILED)
     * @param outcomes       les resultats individuels de chaque step (pour publication d'events)
     */
    public record PhaseResult(
            ExecutionContext updatedContext,
            boolean anyFailed,
            List<StepOutcome> outcomes) {

        public PhaseResult(ExecutionContext updatedContext, boolean anyFailed, List<StepOutcome> outcomes) {
            this.updatedContext = updatedContext;
            this.anyFailed = anyFailed;
            this.outcomes = List.copyOf(outcomes);
        }
    }

    /**
     * Execute toutes les etapes d'une phase, niveau DAG par niveau DAG.
     *
     * @param steps     les etapes de la phase, deja triees par dagLevel croissant
     * @param context   le contexte d'execution courant
     * @param phase     la phase en cours d'execution
     * @param cancelled flag d'annulation cooperative
     * @return le resultat de la phase
     */
    public PhaseResult executePhase(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            AtomicBoolean cancelled) {

        if (steps == null || steps.isEmpty()) {
            log.info("action=execute_phase phase={} steps=0 executionId={}", phase, context.executionId());
            return new PhaseResult(context, false, List.of());
        }

        Map<Integer, List<ExecutionStep>> groupedByLevel = groupStepsByLevel(steps);
        log.info("action=execute_phase phase={} totalSteps={} dagLevels={} executionId={}",
                phase, steps.size(), groupedByLevel.keySet().stream().sorted().toList(), context.executionId());

        ExecutionContext currentContext = context;
        boolean anyFailed = false;
        List<StepOutcome> allOutcomes = new ArrayList<>();

        for (Integer level : groupedByLevel.keySet().stream().sorted().toList()) {
            if (cancelled.get()) {
                log.info("action=phase_cancelled phase={} level={} executionId={}",
                        phase, level, context.executionId());
                break;
            }

            var classification = classifySteps(groupedByLevel.get(level), currentContext);

            for (ExecutionStep step : classification.skippable()) {
                StepDefinition stepDef = step.step();
                var agentId = AgentId.of(DEFAULT_AGENT);
                var skippedResult = TaskResult.skipped(stepDef.id(), stepDef.taskName(), "dependency failed");
                currentContext = currentContext.with(stepDef.id().value(), agentId.value(), skippedResult);
                allOutcomes.add(new StepOutcome(stepDef.id(), agentId, stepDef.taskName(),
                        skippedResult, TaskStatus.SKIPPED));
                anyFailed = true;
            }

            if (!classification.runnable().isEmpty()) {
                var levelResult = executeLevel(classification.runnable(), currentContext, phase, level, cancelled);
                currentContext = levelResult.updatedContext();
                allOutcomes.addAll(levelResult.outcomes());
                if (levelResult.anyFailed()) anyFailed = true;
            }
        }

        return new PhaseResult(currentContext, anyFailed, allOutcomes);
    }

    private Map<Integer, List<ExecutionStep>> groupStepsByLevel(List<ExecutionStep> steps) {
        return steps.stream().collect(Collectors.groupingBy(ExecutionStep::dagLevel));
    }

    /** Classification : etapes executables vs a ignorer. */
    private record LevelClassification(List<ExecutionStep> runnable, List<ExecutionStep> skippable) {}

    private LevelClassification classifySteps(List<ExecutionStep> steps, ExecutionContext context) {
        var runnable = new ArrayList<ExecutionStep>();
        var skippable = new ArrayList<ExecutionStep>();
        for (ExecutionStep step : steps) {
            if (allDependenciesSatisfied(step, context)) {
                runnable.add(step);
            } else {
                skippable.add(step);
            }
        }
        return new LevelClassification(runnable, skippable);
    }

    /** Resultat d'un niveau DAG. */
    private record LevelResult(ExecutionContext updatedContext, boolean anyFailed, List<StepOutcome> outcomes) {}

    /** Resultat brut d'une etape dispatchee dans un niveau. */
    private record LevelOutcome(ExecutionStep step, List<StepDispatchResult> results) {}

    /**
     * Execute toutes les etapes d'un niveau DAG en parallele via Virtual Threads.
     * Supporte l'annulation cooperative : quand {@code cancelled} devient true,
     * les futures en attente sont annulees (interruption des threads),
     * et les etapes non terminees sont marquees FAILED.
     */
    private LevelResult executeLevel(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            int level,
            AtomicBoolean cancelled) {

        log.info("action=execute_dag_level phase={} level={} steps={} executionId={}",
                phase, level, steps.size(), context.executionId());

        ExecutionContext currentContext = context;
        boolean anyFailed = false;
        List<StepOutcome> outcomes = new ArrayList<>();

        try (var vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<LevelOutcome>>();

            for (ExecutionStep step : steps) {
                ExecutionContext ctxAtStart = currentContext;
                futures.add(vtExecutor.submit(() -> {
                    lifecycleDispatcher.dispatch(ExecutionLifecycleSignal.start(
                            SignalId.generate(),
                            ctxAtStart.executionId(),
                            step.step().id(),
                            Map.of(
                                    ExecutionLifecycleSignal.PARAM_TASK_NAME, step.step().taskName(),
                                    ExecutionLifecycleSignal.PARAM_PHASE, phase.name()
                            )
                    ));

                    try {
                        List<StepDispatchResult> results = dispatcher.dispatch(step, ctxAtStart);
                        return new LevelOutcome(step, results);
                    } finally {
                        lifecycleDispatcher.dispatch(ExecutionLifecycleSignal.stop(
                                SignalId.generate(),
                                ctxAtStart.executionId(),
                                step.step().id(),
                                Map.of(
                                        ExecutionLifecycleSignal.PARAM_TASK_NAME, step.step().taskName(),
                                        ExecutionLifecycleSignal.PARAM_PHASE, phase.name()
                                )
                        ));
                    }
                }));
            }

            var pollIntervalMs = 500L;
            futuresLoop:
            for (Future<LevelOutcome> future : futures) {
                LevelOutcome levelOutcome = null;

                while (levelOutcome == null) {
                    if (cancelled.get()) {
                        for (Future<LevelOutcome> f : futures) {
                            f.cancel(true);
                        }
                        log.info("action=level_cancelled phase={} level={} executionId={}",
                                phase, level, currentContext.executionId());
                        anyFailed = true;
                        break futuresLoop;
                    }

                    try {
                        levelOutcome = future.get(pollIntervalMs, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        // Re-check cancellation flag et re-poll
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new LevelResult(currentContext, anyFailed, outcomes);
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        log.error("action=step_unexpected_error phase={} level={} executionId={}",
                                phase, level, currentContext.executionId(), cause);
                        anyFailed = true;
                        break;
                    } catch (CancellationException e) {
                        log.info("action=step_cancelled phase={} level={} executionId={}",
                                phase, level, currentContext.executionId());
                        anyFailed = true;
                        break;
                    }
                }

                if (levelOutcome != null) {
                    for (StepDispatchResult dispatchResult : levelOutcome.results()) {
                        var taskId = levelOutcome.step().step().id();
                        TaskResult result = dispatchResult.result();
                        currentContext = currentContext.with(
                                taskId.value(), dispatchResult.agentId().value(), result);
                        outcomes.add(new StepOutcome(taskId, dispatchResult.agentId(),
                                levelOutcome.step().step().taskName(), result, result.status()));
                        if (result.status() == TaskStatus.FAILED) {
                            anyFailed = true;
                        }
                    }
                }
            }
        }

        return new LevelResult(currentContext, anyFailed, outcomes);
    }

    /**
     * Verifie qu'au moins un resultat SUCCESS existe pour chaque dependance directe.
     */
    public boolean allDependenciesSatisfied(ExecutionStep step, ExecutionContext context) {
        List<TaskId> deps = step.dependencies();
        if (deps == null || deps.isEmpty()) {
            return true;
        }
        for (TaskId dep : deps) {
            var agentResults = context.store().get(dep.value());
            if (agentResults == null || agentResults.isEmpty()) return false;
            boolean anySuccess = agentResults.values().stream().anyMatch(TaskResult::isSuccess);
            if (!anySuccess) return false;
        }
        return true;
    }
}
