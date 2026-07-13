package com.performance.platform.engine.shared;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Exécute une phase (PREPARATION / INJECTION / ASSERTION) niveau DAG par niveau DAG.
 * Les étapes d'un même dagLevel sont exécutées en parallèle via Virtual Threads.
 *
 * <p>Cette classe est partagée entre {@code LocalExecutionEngine} et
 * {@code RemoteExecutionEngine}. La stratégie d'exécution d'un step individuel
 * est déléguée au {@link StepDispatcher}.</p>
 *
 * <p>Règles :
 * <ul>
 *   <li>Avant l'exécution d'un niveau, vérification des prérequis : si un dependsOn est
 *       FAILED/SKIPPED, l'étape est marquée SKIPPED.</li>
 *   <li>Chaque étape est dispatchée via {@link StepDispatcher}.</li>
 *   <li>Le résultat est stocké dans le {@link ExecutionContext}.</li>
 *   <li>La publication des events de domaine (TaskCompleted, TaskFailed) est
 *       à la charge de l'engine appelant — voir {@link PhaseResult#outcomes()}.</li>
 * </ul>
 */
public class DagPhaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagPhaseExecutor.class);

    public static final String DEFAULT_AGENT = "agent-local";

    private final StepDispatcher dispatcher;

    public DagPhaseExecutor(StepDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Résultat d'un step individuel après dispatch.
     */
    public record StepOutcome(TaskId taskId, String taskName, TaskResult result, TaskStatus status) {}

    /**
     * Résultat de l'exécution d'une phase.
     *
     * @param updatedContext le contexte d'exécution mis à jour avec les résultats de la phase
     * @param anyFailed      true si au moins une étape de cette phase a échoué (FAILED)
     * @param outcomes       les résultats individuels de chaque step (pour publication d'events)
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
     * Exécute toutes les étapes d'une phase, niveau DAG par niveau DAG.
     *
     * @param steps     les étapes de la phase, déjà triées par dagLevel croissant
     * @param context   le contexte d'exécution courant
     * @param phase     la phase en cours d'exécution
     * @param cancelled flag d'annulation coopérative
     * @return le résultat de la phase (contexte mis à jour + outcomes pour events)
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
                log.info("action=phase_cancelled phase={} level={} executionId={}", phase, level, context.executionId());
                break;
            }

            var classification = classifySteps(groupedByLevel.get(level), currentContext);

            // Mark skippable steps
            for (ExecutionStep step : classification.skippable()) {
                StepDefinition stepDef = step.step();
                var skippedResult = TaskResult.skipped(stepDef.id(), stepDef.taskName(), "dependency failed");
                currentContext = currentContext.with(stepDef.id().value(), DEFAULT_AGENT, skippedResult);
                allOutcomes.add(new StepOutcome(stepDef.id(), stepDef.taskName(), skippedResult, TaskStatus.SKIPPED));
                anyFailed = true;
            }

            // Execute runnable steps
            if (!classification.runnable().isEmpty()) {
                var levelResult = executeLevel(classification.runnable(), currentContext, phase, level);
                currentContext = levelResult.updatedContext();
                allOutcomes.addAll(levelResult.outcomes());
                if (levelResult.anyFailed()) anyFailed = true;
            }
        }

        return new PhaseResult(currentContext, anyFailed, allOutcomes);
    }

    /**
     * Groupe les étapes par leur niveau DAG.
     */
    private Map<Integer, List<ExecutionStep>> groupStepsByLevel(List<ExecutionStep> steps) {
        return steps.stream().collect(Collectors.groupingBy(ExecutionStep::dagLevel));
    }

    /** Classification : étapes exécutables vs à ignorer. */
    private record LevelClassification(List<ExecutionStep> runnable, List<ExecutionStep> skippable) {}

    /**
     * Classifie les étapes d'un niveau DAG selon l'état de leurs dépendances.
     */
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

    /** Résultat d'un niveau DAG. */
    private record LevelResult(ExecutionContext updatedContext, boolean anyFailed, List<StepOutcome> outcomes) {}

    /**
     * Exécute toutes les étapes d'un niveau DAG en parallèle via Virtual Threads.
     */
    private LevelResult executeLevel(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            int level) {

        log.info("action=execute_dag_level phase={} level={} steps={} executionId={}",
                phase, level, steps.size(), context.executionId());

        ExecutionContext currentContext = context;
        boolean anyFailed = false;
        List<StepOutcome> outcomes = new ArrayList<>();

        try (var vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<StepOutcome>>();

            for (ExecutionStep step : steps) {
                ExecutionContext ctxAtStart = currentContext;
                futures.add(vtExecutor.submit(() -> {
                    TaskResult result = dispatcher.dispatch(step, ctxAtStart, phase);
                    return new StepOutcome(step.step().id(), step.step().taskName(), result, result.status());
                }));
            }

            for (Future<StepOutcome> future : futures) {
                try {
                    StepOutcome outcome = future.get();
                    currentContext = currentContext.with(
                            outcome.taskId().value(), DEFAULT_AGENT, outcome.result());
                    outcomes.add(outcome);
                    if (outcome.result().status() == TaskStatus.FAILED) {
                        anyFailed = true;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Phase execution interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    log.error("action=step_unexpected_error phase={} level={} executionId={}",
                            phase, level, currentContext.executionId(), cause);
                    anyFailed = true;
                }
            }
        }

        return new LevelResult(currentContext, anyFailed, outcomes);
    }

    /**
     * Vérifie que toutes les dépendances directes d'une étape sont SUCCESS dans le contexte.
     */
    public boolean allDependenciesSatisfied(ExecutionStep step, ExecutionContext context) {
        List<TaskId> deps = step.dependencies();
        if (deps == null || deps.isEmpty()) {
            return true;
        }
        for (TaskId dep : deps) {
            var agentResults = context.store().get(dep.value());
            if (agentResults == null) return false;
            var result = agentResults.get(DEFAULT_AGENT);
            if (result == null || !result.isSuccess()) return false;
        }
        return true;
    }
}
