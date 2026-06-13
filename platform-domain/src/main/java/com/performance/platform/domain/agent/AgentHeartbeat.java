package com.performance.platform.domain.agent;

import com.performance.platform.domain.id.AgentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Signal periodique emis par un agent pour indiquer qu'il est vivant.
 * Record immuable — 0 annotation framework.
 */
public record AgentHeartbeat(AgentId agentId, AgentState state, int activeTasks, Instant sentAt) {
    public AgentHeartbeat {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(state, "state required");
        Objects.requireNonNull(sentAt, "sentAt required");
        if (activeTasks < 0) {
            throw new IllegalArgumentException("activeTasks must be non-negative, got " + activeTasks);
        }
    }
}
