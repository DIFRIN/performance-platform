package com.performance.platform.domain.agent;

/**
 * Capacites statiques declarees par un agent.
 * Represente les ressources et la version de l'agent.
 * Record immuable — 0 annotation framework.
 */
public record AgentCapabilities(int maxConcurrentTasks, String version) {
    public AgentCapabilities {
        if (maxConcurrentTasks < 0) {
            throw new IllegalArgumentException("maxConcurrentTasks must be non-negative, got " + maxConcurrentTasks);
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
