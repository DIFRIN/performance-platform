package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.config.ResultSelectionPolicy;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.domain.event.TaskDispatched;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.transport.TaskChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    private final TaskChannel transport;
    private final AgentAvailabilityChecker availabilityChecker;
    private final TaskCorrelationTracker tracker;
    private final ExecutionConfig config;
    private final ApplicationEventPublisher eventPublisher;

    public RemoteStepDispatcher(
            TaskChannel transport,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionConfig config,
            ApplicationEventPublisher eventPublisher) {
        this.transport = transport;
        this.availabilityChecker = availabilityChecker;
        this.tracker = tracker;
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();

        try {
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

            // 4. Track for correlation (before dispatch to avoid race)
            tracker.trackDispatched(messageId, stepDef.id(), context.executionId());

            // Release the correlation state in all cases: without this, every
            // dispatched message leaks a permanent entry in the tracker (C3).
            try {
                // 5. Dispatch via transport
                transport.dispatchTask(request);

                // 6. Publish TaskDispatched domain event
                eventPublisher.publishEvent(new TaskDispatched(
                        context.executionId(), stepDef.id(), stepDef.taskName(),
                        messageId, Instant.now()));

                log.info("action=task_dispatched taskId={} taskName={} messageId={}",
                        stepDef.id().value(), stepDef.taskName(), messageId.value());

                // 7. Await completion (blocking within Virtual Thread)
                return awaitCompletion(messageId, stepDef.id(), config);
            } finally {
                tracker.release(messageId);
            }
        } catch (NoAvailableAgentException e) {
            log.warn("action=no_agent_available taskName={} error={}", stepDef.taskName(), e.getMessage());
            return TaskResult.failed(stepDef.id(), stepDef.taskName(), Duration.ZERO, e.getMessage(), e);
        }
    }

    /**
     * Attend le signal de completion emis par le tracker, puis selectionne
     * le resultat de facon deterministe. Aucun polling : le tracker complete
     * le futur des qu'un evenement satisfait la politique.
     */
    private TaskResult awaitCompletion(MessageId messageId, TaskId taskId, ExecutionConfig config) {
        TaskCompletionPolicy policy = config.completionPolicy();
        CompletableFuture<Void> signal = tracker.completionSignal(messageId, policy);

        try {
            signal.get(config.taskExecutionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("action=completion_timeout messageId={} taskId={} policy={}",
                    messageId.value(), taskId.value(), policy);
            return TaskResult.failed(taskId, taskId.value(),
                    config.taskExecutionTimeout(), "Task execution timed out", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failed(taskId, taskId.value(),
                    Duration.ZERO, "Interrupted while awaiting completion", null);
        } catch (ExecutionException e) {
            return TaskResult.failed(taskId, taskId.value(),
                    Duration.ZERO, "Completion signal failed: " + e.getMessage(), e);
        }

        List<TaskResult> ordered = tracker.resultsInCompletionOrder(messageId);
        if (ordered.isEmpty()) {
            return TaskResult.failed(taskId, taskId.value(),
                    Duration.ZERO, "No results received", null);
        }

        // Selection deterministe par ordre de completion, avec preference
        // pour un resultat reussi.
        ResultSelectionPolicy selectionPolicy = config.resultSelectionPolicy();
        List<TaskResult> candidates = selectionPolicy == ResultSelectionPolicy.LAST_COMPLETED
                ? ordered.reversed()
                : ordered;
        TaskResult selected = candidates.stream()
                .filter(TaskResult::isSuccess)
                .findFirst()
                .orElse(candidates.getFirst());
        log.debug("action=completion_satisfied messageId={} taskId={} policy={} selection={}",
                messageId.value(), taskId.value(), policy, selectionPolicy);
        return selected;
    }
}
