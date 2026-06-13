package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un agent est considere comme perdu (heartbeat expire).
 * Record immuable, 0 annotation framework.
 */
public record AgentLost(AgentId agentId, String reason, Instant timestamp) {

    public AgentLost {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(reason, "reason required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
