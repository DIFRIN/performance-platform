package com.performance.platform.transport.message;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evenement du cycle de vie d'une execution transporte entre l'agent et
 * l'orchestrateur. Enveloppe les evenements metier du domaine dans un
 * format de transport generique (type + payload).
 * <p>
 * Le champ {@code payload} est copie defensivement dans le constructeur
 * compact pour garantir l'immutabilite.
 */
public record ExecutionEvent(
        EventId id,
        ExecutionId executionId,
        MessageId correlationId,
        AgentId agentId,
        String eventType,
        Map<String, Object> payload,
        Instant occurredAt
) {
    /** Types d'evenements standardises pour le transport. */
    public static final String AGENT_REGISTERED      = "AgentRegistered";
    public static final String AGENT_HEARTBEAT       = "AgentHeartbeat";
    public static final String AGENT_DEREGISTERED    = "AgentDeregistered";
    public static final String TASK_CLAIMED          = "TaskClaimedByAgent";
    public static final String TASK_WORK_IN_PROGRESS = "TaskWorkInProgress";
    public static final String TASK_COMPLETED        = "TaskCompleted";
    public static final String TASK_FAILED           = "TaskFailed";

    public ExecutionEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        // Defensive copy du payload pour garantir l'immutabilite
        payload = (payload == null) ? Map.of() : Map.copyOf(payload);
    }

    /**
     * Constructeur de commodite. Delegue au constructeur canonique qui
     * effectue {@code Map.copyOf(payload)} pour garantir l'immutabilite.
     * Utile pour la deserialisation ou les tests quand la Map fournie
     * est deja immuable ou sera copiee de toute facon.
     */
    public static ExecutionEvent of(
            EventId id,
            ExecutionId executionId,
            MessageId correlationId,
            AgentId agentId,
            String eventType,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
        return new ExecutionEvent(id, executionId, correlationId, agentId,
                eventType, payload, occurredAt);
    }
}
