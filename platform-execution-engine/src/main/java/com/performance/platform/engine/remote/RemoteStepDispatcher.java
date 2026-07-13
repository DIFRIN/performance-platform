package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.transport.ExecutionTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Implémentation distribuée du {@link StepDispatcher}.
 * Diffuse la tâche aux agents via {@link ExecutionTransport} en broadcast
 * et attend les résultats selon la {@link TaskCompletionPolicy}.
 *
 * <p>La logique de dispatch est encapsulée ici : availability check,
 * partial context, transport broadcast, tracking, et await completion.
 * Le {@code DagPhaseExecutor} partagé ne connaît rien du transport.</p>
 */
public class RemoteStepDispatcher implements StepDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteStepDispatcher.class);

    private final ExecutionTransport transport;
    private final AgentAvailabilityChecker availabilityChecker;
    private final TaskCorrelationTracker tracker;
    private final ExecutionConfig config;

    public RemoteStepDispatcher(
            ExecutionTransport transport,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionConfig config) {
        this.transport = transport;
        this.availabilityChecker = availabilityChecker;
        this.tracker = tracker;
        this.config = config;
    }

    @Override
    public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
        StepDefinition stepDef = execStep.step();

        // 1. Await agent availability
        availabilityChecker.awaitAgentFor(stepDef.taskName(), config.taskAvailabilityTimeout());
        log.info("action=agent_available taskName={}", stepDef.taskName());

        // 2. Build partial context
        var partialCtx = PartialContextBuilder.build(context, execStep.requiredContextKeys());

        // 3. Create request (broadcast — no targetAgentId)
        var messageId = MessageId.generate();
        RetryPolicy retry = stepDef.retryPolicy() != null ? stepDef.retryPolicy() : RetryPolicy.defaults();
        var request = new com.performance.platform.transport.message.TaskExecutionRequest(
                messageId, context.executionId(), stepDef, partialCtx, Instant.now(), retry);

        // 4. Dispatch via transport
        transport.dispatchTask(request);

        // 5. Track for correlation
        tracker.trackDispatched(messageId, stepDef.id(), context.executionId());

        log.info("action=task_dispatched taskId={} taskName={} messageId={}",
                stepDef.id().value(), stepDef.taskName(), messageId.value());

        // 6. Await completion (blocking within Virtual Thread)
        return awaitCompletion(messageId, stepDef.id(), config);
    }

    /**
     * Poll the tracker until completion policy is satisfied or timeout.
     */
    private TaskResult awaitCompletion(MessageId messageId, TaskId taskId, ExecutionConfig config) {
        long deadlineMs = System.currentTimeMillis() + config.taskExecutionTimeout().toMillis();
        TaskCompletionPolicy policy = config.completionPolicy();

        while (System.currentTimeMillis() < deadlineMs) {
            if (tracker.isComplete(messageId, policy)) {
                var results = tracker.getResults(messageId);
                if (results != null && !results.isEmpty()) {
                    // Return first successful result, or first if none succeeded
                    var successResult = results.values().stream()
                            .filter(TaskResult::isSuccess)
                            .findFirst();
                    if (successResult.isPresent()) {
                        log.debug("action=completion_satisfied messageId={} taskId={} policy={}",
                                messageId.value(), taskId.value(), policy);
                        return successResult.get();
                    }
                    return results.values().iterator().next();
                }
                return TaskResult.failed(taskId, taskId.value(),
                        Duration.ZERO, "No results received", null);
            }

            try {
                Thread.sleep(RemoteExecutionEngine.POLL_COMPLETION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return TaskResult.failed(taskId, taskId.value(),
                        Duration.ZERO, "Interrupted while awaiting completion", null);
            }
        }

        log.warn("action=completion_timeout messageId={} taskId={} policy={}",
                messageId.value(), taskId.value(), policy);
        return TaskResult.failed(taskId, taskId.value(),
                config.taskExecutionTimeout(), "Task execution timed out", null);
    }
}
