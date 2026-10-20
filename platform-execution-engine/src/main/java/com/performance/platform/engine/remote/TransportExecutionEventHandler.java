package com.performance.platform.engine.remote;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.AgentLost;
import com.performance.platform.domain.event.TaskClaimedByAgent;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Consomme les {@link ExecutionEvent} du transport en mode DISTRIBUTED et
 * alimente le tracker de correlation, le repository et le bus d'evenements.
 * <p>
 * Extrait de l'ancien {@code RemoteExecutionEngine} (PDR-050) : l'engine
 * unique ne connait plus le transport. Les evenements dont le correlationId
 * n'est pas (ou plus) suivi sont ignores, ce qui protege aussi contre les
 * evenements tardifs apres {@code tracker.release(...)}.
 */
public class TransportExecutionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TransportExecutionEventHandler.class);

    static final String PAYLOAD_STATUS = "status";
    static final String PAYLOAD_ERROR = "error";
    static final String PAYLOAD_OUTPUTS = "outputs";
    static final String PAYLOAD_DURATION_MS = "durationMs";

    private final TaskCorrelationTracker tracker;
    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransportExecutionEventHandler(ExecutionTransport transport,
                                          TaskCorrelationTracker tracker,
                                          ExecutionRepository executionRepository,
                                          ApplicationEventPublisher eventPublisher) {
        Objects.requireNonNull(transport, "transport must not be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
        this.executionRepository = Objects.requireNonNull(executionRepository,
                "executionRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        transport.subscribe(this::onEvent);
    }

    /**
     * Invalide les claims d'un agent perdu (fast-fail).
     */
    @EventListener
    public void onAgentLost(AgentLost event) {
        tracker.onAgentLost(event.agentId());
        log.warn("action=agent_lost_claims_invalidated agentId={} reason={}",
                event.agentId().value(), event.reason());
    }

    /**
     * Handler appele par le transport pour chaque evenement d'execution.
     */
    public void onEvent(ExecutionEvent event) {
        if (event == null || event.executionId() == null) {
            return;
        }
        if (event.correlationId() == null) {
            return;
        }

        TaskId taskId = tracker.taskIdFor(event.correlationId());
        if (taskId == null) {
            // Message inconnu ou deja libere : ignorer les evenements tardifs.
            return;
        }

        String eventType = event.eventType();
        if (eventType == null) {
            return;
        }

        switch (eventType) {
            case ExecutionEvent.TASK_CLAIMED -> handleTaskClaimed(event, taskId);
            case ExecutionEvent.TASK_COMPLETED -> handleTaskCompleted(event, taskId);
            case ExecutionEvent.TASK_FAILED -> handleTaskFailed(event, taskId);
            case ExecutionEvent.TASK_WORK_IN_PROGRESS -> handleTaskWorkInProgress(event, taskId);
            default -> {
                // Heartbeats / registration : canal lifecycle separe.
            }
        }
    }

    private void handleTaskClaimed(ExecutionEvent event, TaskId taskId) {
        if (event.agentId() == null) {
            return;
        }
        tracker.onClaimed(event.correlationId(), event.agentId());
        log.info("action=task_claimed messageId={} agentId={} executionId={}",
                event.correlationId().value(), event.agentId().value(), event.executionId().value());
        eventPublisher.publishEvent(new TaskClaimedByAgent(
                event.executionId(), taskId, event.agentId(),
                event.correlationId(), event.occurredAt()));
    }

    private void handleTaskCompleted(ExecutionEvent event, TaskId taskId) {
        if (event.agentId() == null) {
            return;
        }

        TaskResult result = reconstructTaskResult(event, taskId);
        tracker.onCompleted(event.correlationId(), event.agentId(), result);
        executionRepository.saveTaskResult(event.executionId(), taskId, event.agentId(), result);

        log.info("action=task_completed messageId={} agentId={} taskId={} status={} executionId={}",
                event.correlationId().value(), event.agentId().value(),
                taskId.value(), result.status(), event.executionId().value());

        eventPublisher.publishEvent(new TaskCompleted(
                event.executionId(), taskId, event.agentId(),
                result, result.duration(), event.occurredAt()));
    }

    private void handleTaskFailed(ExecutionEvent event, TaskId taskId) {
        if (event.agentId() == null) {
            return;
        }

        String error = (String) event.payload().getOrDefault(PAYLOAD_ERROR, "unknown error");
        tracker.onFailed(event.correlationId(), event.agentId(), error);

        log.warn("action=task_failed messageId={} agentId={} taskId={} error={} executionId={}",
                event.correlationId().value(), event.agentId().value(),
                taskId.value(), error, event.executionId().value());
    }

    private void handleTaskWorkInProgress(ExecutionEvent event, TaskId taskId) {
        log.debug("action=task_progress messageId={} agentId={} taskId={} executionId={}",
                event.correlationId().value(), taskId.value(),
                event.agentId() != null ? event.agentId().value() : "unknown",
                event.executionId().value());
    }

    @SuppressWarnings("unchecked")
    private TaskResult reconstructTaskResult(ExecutionEvent event, TaskId taskId) {
        Map<String, Object> payload = event.payload();

        String statusStr = (String) payload.getOrDefault(PAYLOAD_STATUS, "SUCCESS");
        var status = TaskStatus.valueOf(statusStr);

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
        return new TaskResult(taskId, taskId.value(), status, duration, outputs,
                errorMessage, null, completedAt);
    }
}
