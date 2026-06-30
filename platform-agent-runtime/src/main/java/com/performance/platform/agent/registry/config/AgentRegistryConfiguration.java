package com.performance.platform.agent.registry.config;

import com.performance.platform.agent.registry.AgentRegistry;
import com.performance.platform.agent.registry.InMemoryAgentRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour le registre d'agents.
 * <p>
 * Expose le bean {@link AgentRegistry} uniquement en mode DISTRIBUTED
 * avec le role ORCHESTRATOR. Un seul bean satisfait a la fois
 * {@link AgentRegistry} et {@link com.performance.platform.application.ports.out.AgentRegistryPort}
 * (car {@link AgentRegistry} etend {@code AgentRegistryPort}).
 * <p>
 * En LOCAL et AGENT, aucun bean registre n'est cree — coherent avec
 * {@code DefaultAgentAvailabilityChecker} (conditionnel DISTRIBUTED,
 * voir ISSUE-143).
 * <p>
 * Self-wiring : le cablage vit dans {@code platform-agent-runtime},
 * pas dans {@code platform-app} (ADR-026).
 *
 * @see InMemoryAgentRegistry
 */
@Configuration
public class AgentRegistryConfiguration {

    /**
     * Registre d'agents de l'orchestrateur. Un seul bean satisfait
     * {@link AgentRegistry} ET {@link com.performance.platform.application.ports.out.AgentRegistryPort}.
     * <p>
     * Conditionnel : uniquement en DISTRIBUTED + ORCHESTRATOR.
     */
    @Bean
    @ConditionalOnExpression(
        "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
    public AgentRegistry agentRegistry() {
        return new InMemoryAgentRegistry();
    }
}
