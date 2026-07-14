package com.performance.platform.engine.local;

import com.performance.platform.assertion.AssertionResultMapper;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.plugin.AssertionExecutor;
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
    public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
        StepDefinition stepDef = execStep.step();
        RetryPolicy policy = stepDef.retryPolicy() != null
                ? stepDef.retryPolicy()
                : RetryPolicy.defaults();

        var start = Instant.now();

        try {
            TaskResult result;
            if (phase == Phase.ASSERTION) {
                result = executeAssertion(stepDef, context, policy);
            } else {
                result = executePreparationOrInjection(stepDef, context, policy);
            }
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

    private TaskResult executePreparationOrInjection(
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

    private TaskResult executeAssertion(
            StepDefinition stepDef,
            ExecutionContext context,
            RetryPolicy policy) {

        AssertionExecutor executor = lookup.findAssertionExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No AssertionExecutor found for assertionName: " + stepDef.taskName(), null);
        }

        return retryExecutor.executeWithRetry(policy, () -> {
            var assertionResult = executor.evaluate(context, stepDef);
            return AssertionResultMapper.toTaskResult(assertionResult, stepDef);
        });
    }
}
