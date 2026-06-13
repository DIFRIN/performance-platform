package com.performance.platform.domain.event;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.id.AgentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un agent s'enregistre aupres de l'orchestrateur.
 * Record immuable, 0 annotation framework.
 */
public record AgentRegistered(AgentId agentId, AgentDescriptor descriptor, Instant timestamp) {

    public AgentRegistered {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(descriptor, "descriptor required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
