package com.performance.platform.domain.agent;

/**
 * États du cycle de vie d'un agent.
 */
public enum AgentState {
    REGISTERING,
    IDLE,
    EXECUTING,
    DRAINING,
    OFFLINE
}
