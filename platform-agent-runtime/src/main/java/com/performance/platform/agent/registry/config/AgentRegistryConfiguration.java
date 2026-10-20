package com.performance.platform.agent.registry.config;

import com.performance.platform.agent.registration.AgentLifecycleIngestion;
import com.performance.platform.agent.registry.AgentRegistry;
import com.performance.platform.agent.registry.AgentTtlMonitor;
import com.performance.platform.agent.registry.AgentTtlMonitorFactory;
import com.performance.platform.agent.registry.InMemoryAgentRegistry;
import com.performance.platform.domain.event.AgentLost;
import com.performance.platform.transport.ExecutionTransport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

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

    /**
     * Pont transport → registre : consomme les evenements de cycle de vie
     * publies par les agents et alimente le registre.
     * <p>
     * Sans ce bean, le registre reste vide et l'orchestrateur ne peut
     * dispatcher aucune tache en mode DISTRIBUTED.
     */
    @Bean
    @ConditionalOnExpression(
        "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
    public AgentLifecycleIngestion agentLifecycleIngestion(
            ExecutionTransport transport,
            AgentRegistry agentRegistry,
            ApplicationEventPublisher eventPublisher) {
        return new AgentLifecycleIngestion(transport, agentRegistry, eventPublisher);
    }

    /**
     * Moniteur TTL : evince les agents dont le heartbeat a expire et
     * publie un {@link AgentLost} pour declencher le fast-fail des steps
     * en cours.
     * <p>
     * Demarre avec le contexte ({@code initMethod}) et s'arrete proprement
     * avec lui ({@code destroyMethod}).
     *
     * @param checkIntervalSeconds intervalle de verification, configurable via
     *                             {@code agent.registry.ttl-check-interval-seconds}
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnExpression(
        "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
    public AgentTtlMonitor agentTtlMonitor(
            AgentRegistry agentRegistry,
            ApplicationEventPublisher eventPublisher,
            @Value("${agent.registry.ttl-check-interval-seconds:10}") long checkIntervalSeconds) {
        return AgentTtlMonitorFactory.create(agentRegistry, checkIntervalSeconds,
                agentId -> eventPublisher.publishEvent(
                        new AgentLost(agentId, "heartbeat timeout", Instant.now())));
    }
}
