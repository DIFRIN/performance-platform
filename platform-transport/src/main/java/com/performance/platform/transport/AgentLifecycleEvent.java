package com.performance.platform.transport;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Événement de cycle de vie d'un agent (enregistrement, heartbeat, désenregistrement).
 * <p>
 * Contrairement à {@code ExecutionEvent}, ce record n'a PAS d'{@code ExecutionId} —
 * ces événements ne sont pas liés à une exécution de scénario.
 * <p>
 * Créé suite à l'ADR-012 pour éliminer le sentinel {@code NO_EXECUTION}
 * et séparer sémantiquement les canaux execution / lifecycle dans le transport.
 */
public record AgentLifecycleEvent(
        EventId id,
        AgentId agentId,
        String eventType,
        Map<String, Object> payload,
        Instant occurredAt
) {
    /** L'agent s'est enregistré auprès de l'orchestrateur. */
    public static final String AGENT_REGISTERED   = "AgentRegistered";
    /** L'agent a émis un heartbeat. */
    public static final String AGENT_HEARTBEAT    = "AgentHeartbeat";
    /** L'agent s'est explicitement désenregistré. */
    public static final String AGENT_DEREGISTERED = "AgentDeregistered";

    public AgentLifecycleEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        // Defensive copy du payload
        payload = (payload == null) ? Map.of() : Map.copyOf(payload);
    }
}
