package com.performance.platform.engine.local;

import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskFailed;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Execute une phase (PREPARATION / INJECTION / ASSERTION) niveau DAG par niveau DAG.
 * Les etapes d'un meme dagLevel sont executees en parallele via Virtual Threads.
 *
 * <p>Regles :
 * <ul>
 *   <li>Avant l'execution d'un niveau, verification des prerequis : si un dependsOn est
 *       FAILED/SKIPPED, l'etape est marquee SKIPPED.</li>
 *   <li>Chaque etape est executee via {@link RetryExecutor} avec sa {@link RetryPolicy}.</li>
 *   <li>Le resultat est stocke dans le {@link ExecutionContext} avec l'agent "agent-local".</li>
 *   <li>Les events {@link TaskCompleted} et {@link TaskFailed} sont publies apres chaque etape.</li>
 * </ul>
 *
 * <p>Cette classe est interne au module engine. Elle est utilisee par {@link LocalExecutionEngine}
 * et pourra etre reutilisee par {@code RemoteExecutionEngine} (ISSUE-024).</p>
 */
public class DagPhaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagPhaseExecutor.class);
    static final String LOCAL_AGENT = "agent-local";

    private final RetryExecutor retryExecutor;

    public DagPhaseExecutor(RetryExecutor retryExecutor) {
        this.retryExecutor = retryExecutor;
    }

    /**
     * Resultat de l'execution d'une phase.
     *
     * @param updatedContext le contexte d'execution mis a jour avec les resultats de la phase
     * @param anyFailed      true si au moins une etape de cette phase a echoue (FAILED)
     */
    public record PhaseResult(ExecutionContext updatedContext, boolean anyFailed) {
    }

    /**
     * Execute toutes les etapes d'une phase, niveau DAG par niveau DAG.
     *
     * @param steps         les etapes de la phase, deja triees par dagLevel croissant
     * @param context       le contexte d'execution courant
     * @param phase         la phase en cours d'execution
     * @param lookup        le resolver de TaskExecutor / AssertionExecutor
     * @param eventPublisher le publisher d'events de domaine
     * @param cancelled     flag d'annulation cooperative
     * @return le resultat de la phase (contexte mis a jour + indicateur d'echec)
     */
    public PhaseResult executePhase(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            TaskExecutorLookup lookup,
            ApplicationEventPublisher eventPublisher,
            AtomicBoolean cancelled) {

        if (steps == null || steps.isEmpty()) {
            log.info("action=execute_phase phase={} steps=0 executionId={}", phase, context.executionId());
            return new PhaseResult(context, false);
        }

        Map<Integer, List<ExecutionStep>> groupedByLevel = groupStepsByLevel(steps);
        log.info("action=execute_phase phase={} totalSteps={} dagLevels={} executionId={}",
                phase, steps.size(), groupedByLevel.keySet().stream().sorted().toList(), context.executionId());

        ExecutionContext currentContext = context;
        boolean anyFailed = false;

        for (Integer level : groupedByLevel.keySet().stream().sorted().toList()) {
            if (cancelled.get()) {
                log.info("action=phase_cancelled phase={} level={} executionId={}", phase, level, context.executionId());
                break;
            }

            var classification = classifySteps(groupedByLevel.get(level), currentContext);

            // Mark skippable steps
            for (ExecutionStep step : classification.skippable()) {
                StepDefinition stepDef = step.step();
                TaskResult skippedResult = TaskResult.skipped(stepDef.id(), stepDef.taskName(), "dependency failed");
                currentContext = currentContext.with(stepDef.id().value(), LOCAL_AGENT, skippedResult);
                publishTaskSkipped(eventPublisher, currentContext.executionId(), stepDef.id(), skippedResult);
                anyFailed = true;
            }

            // Execute runnable steps
            if (!classification.runnable().isEmpty()) {
                var levelResult = executeLevel(classification.runnable(), currentContext,
                        phase, lookup, eventPublisher, level);
                currentContext = levelResult.updatedContext();
                if (levelResult.anyFailed()) anyFailed = true;
            }
        }

        return new PhaseResult(currentContext, anyFailed);
    }

    /**
     * Group execution steps by their DAG level.
     */
    private Map<Integer, List<ExecutionStep>> groupStepsByLevel(List<ExecutionStep> steps) {
        return steps.stream().collect(Collectors.groupingBy(ExecutionStep::dagLevel));
    }

    /** Classification result: steps that can run vs steps to skip. */
    private record LevelClassification(List<ExecutionStep> runnable, List<ExecutionStep> skippable) {}

    /**
     * Classify steps at a single DAG level into those that can execute (all
     * dependencies satisfied) and those that must be skipped (failed dependency).
     */
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

    /** Result of executing one DAG level. */
    private record LevelResult(ExecutionContext updatedContext, boolean anyFailed) {}

    /**
     * Execute all steps at a single DAG level in parallel via Virtual Threads.
     */
    private LevelResult executeLevel(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            TaskExecutorLookup lookup,
            ApplicationEventPublisher eventPublisher,
            int level) {

        log.info("action=execute_dag_level phase={} level={} steps={} executionId={}",
                phase, level, steps.size(), context.executionId());

        ExecutionContext currentContext = context;
        boolean anyFailed = false;

        try (var vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<StepExecutionResult>> futures = new ArrayList<>();

            for (ExecutionStep step : steps) {
                ExecutionContext ctxAtStart = currentContext;
                futures.add(vtExecutor.submit(() ->
                        executeSingleStep(step, ctxAtStart, phase, lookup)));
            }

            for (Future<StepExecutionResult> future : futures) {
                try {
                    StepExecutionResult result = future.get();
                    currentContext = currentContext.with(
                            result.taskId().value(), LOCAL_AGENT, result.taskResult());
                    publishTaskResult(eventPublisher, currentContext.executionId(),
                            result.taskId(), result.taskResult(), phase);
                    if (result.taskResult().status() == TaskStatus.FAILED) {
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

        return new LevelResult(currentContext, anyFailed);
    }

    /**
     * Verifie que toutes les dependances directes d'une etape sont SUCCESS dans le contexte.
     */
    boolean allDependenciesSatisfied(ExecutionStep step, ExecutionContext context) {
        List<TaskId> deps = step.dependencies();
        if (deps == null || deps.isEmpty()) {
            return true;
        }
        for (TaskId dep : deps) {
            var agentResults = context.store().get(dep.value());
            if (agentResults == null) return false;
            var result = agentResults.get(LOCAL_AGENT);
            if (result == null || !result.isSuccess()) return false;
        }
        return true;
    }

    /**
     * Execute une seule etape avec retry.
     */
    private StepExecutionResult executeSingleStep(
            ExecutionStep execStep,
            ExecutionContext context,
            Phase phase,
            TaskExecutorLookup lookup) {

        StepDefinition stepDef = execStep.step();
        RetryPolicy policy = stepDef.retryPolicy() != null
                ? stepDef.retryPolicy()
                : RetryPolicy.defaults();

        Instant start = Instant.now();

        try {
            TaskResult result;
            if (phase == Phase.ASSERTION) {
                result = executeAssertionStep(stepDef, context, lookup, policy);
            } else {
                result = executePreparationOrInjectionStep(stepDef, context, lookup, policy);
            }
            Duration duration = Duration.between(start, Instant.now());
            log.info("action=step_completed taskId={} taskName={} status={} durationMs={}",
                    stepDef.id().value(), stepDef.taskName(), result.status(), duration.toMillis());
            return new StepExecutionResult(stepDef.id(), result);
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            TaskResult failedResult = TaskResult.failed(
                    stepDef.id(), stepDef.taskName(), duration, e.getMessage(), e);
            log.warn("action=step_exhausted taskId={} taskName={} durationMs={} error={}",
                    stepDef.id().value(), stepDef.taskName(), duration.toMillis(), e.getMessage());
            return new StepExecutionResult(stepDef.id(), failedResult);
        }
    }

    private TaskResult executePreparationOrInjectionStep(
            StepDefinition stepDef,
            ExecutionContext context,
            TaskExecutorLookup lookup,
            RetryPolicy policy) {

        TaskExecutor executor = lookup.findTaskExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No TaskExecutor found for taskName: " + stepDef.taskName(), null);
        }

        return retryExecutor.executeWithRetry(policy, () -> executor.execute(context, stepDef));
    }

    private TaskResult executeAssertionStep(
            StepDefinition stepDef,
            ExecutionContext context,
            TaskExecutorLookup lookup,
            RetryPolicy policy) {

        AssertionExecutor executor = lookup.findAssertionExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No AssertionExecutor found for assertionName: " + stepDef.taskName(), null);
        }

        return retryExecutor.executeWithRetry(policy, () -> {
            var assertionResult = executor.evaluate(context, stepDef);
            return assertionResultToTaskResult(assertionResult, stepDef);
        });
    }

    /**
     * Convertit un {@link com.performance.platform.domain.assertion.AssertionResult}
     * en {@link TaskResult} pour un stockage uniforme dans l'ExecutionContext.
     */
    static TaskResult assertionResultToTaskResult(
            com.performance.platform.domain.assertion.AssertionResult assertionResult,
            StepDefinition stepDef) {

        Duration duration = assertionResult.evaluationDuration();
        Map<String, Object> evidenceOutputs = assertionResult.evidence() != null
                ? assertionResult.evidence().details()
                : null;
        if (evidenceOutputs == null) evidenceOutputs = Map.of();

        return switch (assertionResult.status()) {
            case PASSED -> new TaskResult(stepDef.id(), stepDef.taskName(),
                    TaskStatus.SUCCESS, duration, evidenceOutputs,
                    null, null, assertionResult.evaluatedAt());
            case FAILED -> new TaskResult(stepDef.id(), stepDef.taskName(),
                    TaskStatus.FAILED, duration, evidenceOutputs,
                    assertionResult.description(),
                    null, assertionResult.evaluatedAt());
            case SKIPPED -> TaskResult.skipped(stepDef.id(), stepDef.taskName(),
                    assertionResult.description());
            case ERROR -> new TaskResult(stepDef.id(), stepDef.taskName(),
                    TaskStatus.FAILED, duration, Map.of(),
                    assertionResult.description(),
                    null, assertionResult.evaluatedAt());
        };
    }

    private void publishTaskResult(
            ApplicationEventPublisher publisher,
            com.performance.platform.domain.id.ExecutionId executionId,
            TaskId taskId,
            TaskResult result,
            Phase phase) {

        AgentId agentId = AgentId.of(LOCAL_AGENT);
        Instant now = Instant.now();

        if (result.isSuccess()) {
            publisher.publishEvent(new TaskCompleted(
                    executionId, taskId, agentId, result, result.duration(), now));
        } else {
            publisher.publishEvent(new TaskFailed(
                    executionId, taskId, agentId,
                    result.errorMessage() != null ? result.errorMessage() : "failed",
                    1, now));
        }
    }

    private void publishTaskSkipped(
            ApplicationEventPublisher publisher,
            com.performance.platform.domain.id.ExecutionId executionId,
            TaskId taskId,
            TaskResult result) {

        // Publier comme TaskCompleted pour que les listeners puissent suivre
        AgentId agentId = AgentId.of(LOCAL_AGENT);
        publisher.publishEvent(new TaskCompleted(
                executionId, taskId, agentId, result, Duration.ZERO, Instant.now()));
    }

    private record StepExecutionResult(TaskId taskId, TaskResult taskResult) {
    }
}
