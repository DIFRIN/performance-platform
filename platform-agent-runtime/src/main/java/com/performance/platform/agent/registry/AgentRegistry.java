package com.performance.platform.agent.registry;

import com.performance.platform.application.ports.out.AgentRegistryPort;

/**
 * Interface du registre d'agents côté orchestrateur.
 * <p>
 * Étend {@link AgentRegistryPort} — suit la présence des agents et leur TTL,
 * SANS aucune sélection (voir ADR-008 : le filtrage est côté agent).
 * <p>
 * Implémentation par défaut : {@link InMemoryAgentRegistry}
 * (stockage en mémoire, thread-safe).
 */
public interface AgentRegistry extends AgentRegistryPort {
}
