package com.performance.platform.app.api.dto;

import java.util.Set;

/**
 * REST response for GET /api/v1/agents.
 * Maps {@link com.performance.platform.domain.agent.AgentDescriptor} to a
 * view-friendly representation.
 *
 * @param agentId         the agent identifier
 * @param name            the agent name
 * @param state           the agent lifecycle state (e.g. IDLE, EXECUTING)
 * @param supportedTasks  the task names this agent can execute
 * @param lastHeartbeatAt ISO-8601 timestamp of the last heartbeat
 */
public record AgentResponse(
        String agentId,
        String name,
        String state,
        Set<String> supportedTasks,
        String lastHeartbeatAt) {

    public AgentResponse {
        supportedTasks = supportedTasks == null ? Set.of() : Set.copyOf(supportedTasks);
    }
}
