package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un agent precedentment perdu se reconnecte.
 * Record immuable, 0 annotation framework.
 */
public record AgentRecovered(AgentId agentId, Instant timestamp) {

    public AgentRecovered {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
