package com.performance.platform.runtime;

/**
 * Configuration runtime resolue : mode, role et transport.
 *
 * @param mode          mode d'execution (LOCAL ou DISTRIBUTED)
 * @param role          role de l'instance (ORCHESTRATOR, AGENT, NONE)
 * @param transportType type de transport configure (peut etre null)
 */
public record RuntimeConfig(RuntimeMode mode, RuntimeRole role, String transportType) {
}
