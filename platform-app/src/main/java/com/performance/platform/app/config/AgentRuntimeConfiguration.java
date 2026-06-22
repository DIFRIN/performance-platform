package com.performance.platform.app.config;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.local.LocalAgent;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.registration.TransportAgentRegistration;
import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.agent.runtime.DistributedAgentRuntime;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Configuration Spring pour les beans du runtime agent.
 * <p>
 * Selectionne le {@link AgentRuntime} selon le mode :
 * <ul>
 *   <li>{@code runtime.mode=LOCAL} -> {@code LocalAgent} avec tous les task names
 *       des {@link TaskExecutor} enregistres dans le contexte Spring</li>
 *   <li>{@code runtime.mode=DISTRIBUTED} et {@code runtime.role=AGENT} ->
 *       {@code DistributedAgentRuntime} avec {@code agent.supported-tasks}
 *       de la configuration</li>
 * </ul>
 * <p>
 * Aucun bean {@link AgentRuntime} n'est cree en mode ORCHESTRATOR.
 * <p>
 * Aucun {@code if/switch} sur {@code runtime.mode} ou {@code runtime.role}
 * dans le code metier : la selection est purement declarative via
 * {@link ConditionalOnProperty} (CF-03).
 *
 * <h3>ADR-015 — Configuration-Driven Agent Specialization</h3>
 * <p>{@code LocalAgent} derive tous les task names des executors enregistres,
 * ignorant {@code AgentProperties.supportedTasks()}.
 * {@code DistributedAgentRuntime} utilise exclusivement
 * {@code AgentProperties.supportedTasks()} comme source.
 *
 * <h3>ADR-006 — Priorite env var > property</h3>
 * <p>Les helpers {@code resolveAgentName()}, {@code resolveAgentHost()},
 * {@code resolveAgentPort()} verifient d'abord les variables d'environnement
 * ({@code AGENT_NAME}, {@code AGENT_HOST}, {@code AGENT_PORT}) puis le hostname
 * de la machine et un port par defaut.
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentRuntimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeConfiguration.class);

    // ========================================================================
    // Mode LOCAL : LocalAgent avec TOUS les task names des executors
    // ========================================================================

    /**
     * Cree un {@link LocalAgent} en mode LOCAL.
     * <p>
     * Tous les {@link TaskExecutor} enregistres dans le contexte Spring sont
     * automatiquement collectes via injection de liste. L'agent declare
     * supporter tous les task names decouverts, ignorant
     * {@code AgentProperties.supportedTasks()} (ADR-015).
     * <p>
     * Les {@link StatefulResourceCleaner} sont injectes via {@link ObjectProvider}
     * pour gerer le cas ou aucun cleaner n'est enregistre.
     */
    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
    public AgentRuntime localAgentRuntime(
            InMemoryExecutionTransport transport,
            List<TaskExecutor> taskExecutors,
            ObjectProvider<StatefulResourceCleaner> cleanersProvider) {

        var allTaskNames = taskExecutors.stream()
                .map(TaskExecutor::getSupportedTaskName)
                .collect(Collectors.toUnmodifiableSet());

        var agentId = AgentId.generate();
        var descriptor = new AgentDescriptor(
                agentId,
                "local-agent",
                "localhost",
                8080,
                null,
                allTaskNames,                    // TOUS les noms du registre
                new AgentCapabilities(0, "1.0.0"),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofMinutes(5)
        );

        var cleaners = cleanersProvider.stream().toList();
        return new LocalAgent(transport, descriptor, Duration.ofMinutes(5), taskExecutors, cleaners);
    }

    // ========================================================================
    // Mode DISTRIBUTED, role AGENT : DistributedAgentRuntime avec config
    // ========================================================================

    /**
     * Cree un {@link DistributedAgentRuntime} en mode DISTRIBUTED, role AGENT.
     * <p>
     * Utilise exclusivement {@code AgentProperties.supportedTasks()}
     * comme source de {@code supportedTaskNames} (ADR-015).
     * <p>
     * Si aucun {@code agent.supported-tasks} n'est configure, l'agent demarre
     * idle avec un ensemble vide (log en WARN dans le runtime).
     */
    @Bean
    @ConditionalOnExpression("'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('AGENT')")
    public AgentRuntime distributedAgentRuntime(
            ExecutionTransport transport,
            AgentProperties agentProperties,
            AgentRegistrationPort registrationPort,
            List<TaskExecutor> taskExecutors,
            ObjectProvider<StatefulResourceCleaner> cleanersProvider) {

        var supportedTaskNames = Set.copyOf(agentProperties.supportedTasks());

        if (supportedTaskNames.isEmpty()) {
            log.warn("No agent.supported-tasks configured — agent will be idle");
        }

        var agentId = AgentId.generate();
        var descriptor = new AgentDescriptor(
                agentId,
                resolveAgentName(),
                resolveAgentHost(),
                resolveAgentPort(),
                null,
                supportedTaskNames,              // depuis la config
                new AgentCapabilities(0, "1.0.0"),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofMinutes(5)
        );

        var filter = new DefaultTaskSpecializationFilter(supportedTaskNames, agentId);
        var cleaners = cleanersProvider.stream().toList();

        return new DistributedAgentRuntime(
                transport,
                filter,
                registrationPort,
                descriptor,
                Duration.ofSeconds(10),         // heartbeat interval
                Duration.ofMinutes(5),          // task execution timeout
                taskExecutors,
                cleaners
        );
    }

    // ========================================================================
    // AgentRegistrationPort (DISTRIBUTED + AGENT)
    // ========================================================================

    /**
     * Port d'enregistrement agent qui publie les evenements de cycle de vie
     * via le transport.
     */
    @Bean
    @ConditionalOnExpression("'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('AGENT')")
    public AgentRegistrationPort agentRegistrationPort(ExecutionTransport transport) {
        return new TransportAgentRegistration(transport);
    }

    // ========================================================================
    // TaskSpecializationFilter (DISTRIBUTED + AGENT)
    // ========================================================================

    /**
     * Filtre de specialisation partage, disponible pour d'autres
     * consommateurs (ex: observability).
     * <p>
     * Note : le {@code DistributedAgentRuntime} utilise son propre filtre
     * cree localement. Ce bean est un point d'acces supplementaire
     * pour les composants qui ont besoin de connaitre la specialisation
     * de l'agent sans dependre du runtime.
     */
    @Bean
    @ConditionalOnExpression("'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('AGENT')")
    public TaskSpecializationFilter taskSpecializationFilter(
            AgentProperties agentProperties) {
        var agentId = AgentId.generate();
        return new DefaultTaskSpecializationFilter(
                Set.copyOf(agentProperties.supportedTasks()), agentId);
    }

    // ========================================================================
    // Helpers — resolution nom/host/port (ADR-006 : env var > hostname > fallback)
    // ========================================================================

    private static String resolveAgentName() {
        var envName = System.getenv("AGENT_NAME");
        if (envName != null && !envName.isBlank()) {
            return envName.strip();
        }
        var envId = System.getenv("AGENT_ID");
        if (envId != null && !envId.isBlank()) {
            return envId.strip();
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "agent-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static String resolveAgentHost() {
        var envHost = System.getenv("AGENT_HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost.strip();
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static int resolveAgentPort() {
        var envPort = System.getenv("AGENT_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort.strip());
        }
        return 8080;
    }
}
