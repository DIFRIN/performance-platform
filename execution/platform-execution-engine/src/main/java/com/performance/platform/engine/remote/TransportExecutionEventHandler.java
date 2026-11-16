package com.performance.platform.engine.remote;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.AgentLost;
import com.performance.platform.domain.event.TaskClaimedByAgent;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskGrantSignal;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.correlation.GrantDecision;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.transport.EventChannel;
import com.performance.platform.transport.SignalChannel;
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
 * Consomme les {@link ExecutionEvent} du transport et alimente le tracker de
 * correlation, le repository et le bus d'evenements.
 * <p>
 * Unique dans tous les modes (ADR-059) : en LOCAL, l'agent embarque publie ses
 * evenements sur le transport en memoire.
 * <p>
 * Les claims declenchent la decision de grant (ADR-055) : {@code one} accorde
 * le premier claim et rejette les suivants ; {@code all} laisse le dispatcher
 * accorder les claims a la fin de la fenetre de claim.
 */
public final class TransportExecutionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TransportExecutionEventHandler.class);

    static final String PAYLOAD_STATUS = "status";
    static final String PAYLOAD_ERROR = "error";
    static final String PAYLOAD_OUTPUTS = "outputs";
    static final String PAYLOAD_DURATION_MS = "durationMs";

    private final TaskCorrelationTracker tracker;
    private final SignalChannel signalChannel;
    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransportExecutionEventHandler(EventChannel transport,
                                          SignalChannel signalChannel,
                                          TaskCorrelationTracker tracker,
                                          ExecutionRepository executionRepository,
                                          ApplicationEventPublisher eventPublisher) {
        Objects.requireNonNull(transport, "transport must not be null");
        this.signalChannel = Objects.requireNonNull(signalChannel, "signalChannel must not be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
        this.executionRepository = Objects.requireNonNull(executionRepository,
                "executionRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        transport.subscribe(this::onEvent);
    }

    /**
     * Invalide les grants d'un agent perdu (fast-fail).
     */
    @EventListener
    public void onAgentLost(AgentLost event) {
        tracker.onAgentLost(event.agentId());
        log.warn("action=agent_lost_grants_invalidated agentId={} reason={}",
                event.agentId().value(), event.reason());
    }

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
            case ExecutionEvent.TASK_WORK_IN_PROGRESS -> log.debug(
                    "action=task_progress messageId={} taskId={} executionId={}",
                    event.correlationId().value(), taskId.value(), event.executionId().value());
            default -> {
                // Heartbeats / registration : canal lifecycle separe.
            }
        }
    }

    private void handleTaskClaimed(ExecutionEvent event, TaskId taskId) {
        if (event.agentId() == null) {
            return;
        }
        GrantDecision decision = tracker.onClaimed(event.correlationId(), event.agentId());
        log.info("action=task_claimed messageId={} agentId={} executionId={} decision={}",
                event.correlationId().value(), event.agentId().value(),
                event.executionId().value(), decision);
        eventPublisher.publishEvent(new TaskClaimedByAgent(
                event.executionId(), taskId, event.agentId(),
                event.correlationId(), event.occurredAt()));

        if (decision != GrantDecision.PENDING) {
            publishGrant(event, taskId, event.agentId(), decision == GrantDecision.GRANTED);
        }
    }

    private void publishGrant(ExecutionEvent claim, TaskId taskId, AgentId agentId, boolean granted) {
        signalChannel.broadcastSignal(new TaskGrantSignal(
                SignalId.generate(), claim.executionId(), taskId, claim.correlationId(),
                agentId, granted, Instant.now(), GrantShare.single()));
        log.info("action=grant_published messageId={} agentId={} granted={}",
                claim.correlationId().value(), agentId.value(), granted);
    }

    private void handleTaskCompleted(ExecutionEvent event, TaskId taskId) {
        if (event.agentId() == null) {
            return;
        }
        if (isExecutionTerminal(event)) {
            log.warn("action=late_result_discarded messageId={} taskId={} executionId={}",
                    event.correlationId().value(), taskId.value(), event.executionId().value());
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

    /**
     * ADR-058 : un agent qui termine une tache dont l'execution est deja
     * terminale voit son resultat journalise puis jete.
     */
    private boolean isExecutionTerminal(ExecutionEvent event) {
        var persisted = executionRepository.findById(
                new com.performance.platform.domain.id.ExecutionId(event.executionId().value()));
        return persisted.map(state -> state.status() != ExecutionStatus.RUNNING).orElse(false);
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
        String taskName = payload.getOrDefault("taskName", taskId.value()).toString();
        return new TaskResult(taskId, taskName, status, duration, outputs,
                errorMessage, null, completedAt);
    }
}
