package com.performance.platform.engine.local;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Implémentation locale du {@link StepDispatcher}.
 * Appelle l'executor directement (mémoire partagée JVM) avec retry.
 *
 * <p>En LOCAL, le dispatch est un simple appel de méthode Java —
 * pas de sérialisation, pas de réseau, pas de heartbeat.</p>
 *
 * <p>Depuis ISSUE-154 : chemin d'exécution unifié pour toutes les phases
 * (PREPARATION, INJECTION, ASSERTION). La résolution utilise
 * {@link TaskExecutorLookup#findTaskExecutor(String)} pour tous les cas,
 * car {@code AssertionExecutor} étend {@code TaskExecutor}.</p>
 */
public class LocalStepDispatcher implements StepDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LocalStepDispatcher.class);

    private final TaskExecutorLookup lookup;
    private final RetryExecutor retryExecutor;

    public LocalStepDispatcher(TaskExecutorLookup lookup, RetryExecutor retryExecutor) {
        this.lookup = lookup;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();
        RetryPolicy policy = stepDef.retryPolicy() != null
                ? stepDef.retryPolicy()
                : RetryPolicy.defaults();

        var start = Instant.now();

        try {
            TaskResult result = executeStep(stepDef, context, policy);
            var duration = Duration.between(start, Instant.now());
            log.info("action=step_completed taskId={} taskName={} status={} durationMs={}",
                    stepDef.id().value(), stepDef.taskName(), result.status(), duration.toMillis());
            return result;
        } catch (Exception e) {
            var duration = Duration.between(start, Instant.now());
            var failedResult = TaskResult.failed(
                    stepDef.id(), stepDef.taskName(), duration, e.getMessage(), e);
            log.warn("action=step_exhausted taskId={} taskName={} durationMs={} error={}",
                    stepDef.id().value(), stepDef.taskName(), duration.toMillis(), e.getMessage());
            return failedResult;
        }
    }

    /**
     * Exécute un step avec retry, chemin unifié pour toutes les phases.
     * La résolution de l'executor se fait via {@code findTaskExecutor()}
     * qui trouve aussi les {@code AssertionExecutor} (car ils étendent
     * {@code TaskExecutor}).
     */
    private TaskResult executeStep(
            StepDefinition stepDef,
            ExecutionContext context,
            RetryPolicy policy) {

        TaskExecutor executor = lookup.findTaskExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No TaskExecutor found for taskName: " + stepDef.taskName(), null);
        }

        return retryExecutor.executeWithRetry(policy, () -> executor.execute(context, stepDef));
    }
}
