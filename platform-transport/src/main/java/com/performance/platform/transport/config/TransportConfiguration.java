package com.performance.platform.transport.config;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.http.HttpExecutionTransport;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.rabbitmq.RabbitMQExecutionTransport;
import com.performance.platform.transport.socket.SocketExecutionTransport;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration automatique du transport.
 *
 * <p>Active les {@code @ConfigurationProperties} des 4 transports et
 * expose les {@code @Bean} conditionnels. Le {@code Bean} IN_MEMORY
 * est l'implementation par defaut ({@code matchIfMissing = true}).
 *
 * <p>Les transports KAFKA, RABBITMQ, HTTP, et SOCKET sont des
 * implementations completes (ISSUE-029, 030, 031, 032).
 *
 * <p><strong>TransportType IN_MEMORY</strong> est le defaut pour le mode LOCAL
 * et les tests. Les transports reels sont selectionnes via
 * {@code transport.type} dans {@code application.yaml} ou via la variable
 * d'environnement {@code TRANSPORT_TYPE} (ADR-006 : env prioritaire).
 *
 * <p>Le transport KAFKA est configure via {@link KafkaTransportBeans}
 * (ISSUE-089, ISSUE-091) qui expose le {@code KafkaExecutionTransport}
 * avec {@code KafkaTemplate} et
 * {@code ConcurrentKafkaListenerContainerFactory} qualifies.
 */
@Configuration
@EnableConfigurationProperties({
        KafkaTransportProperties.class,
        RabbitMQTransportProperties.class,
        HttpTransportProperties.class,
        SocketTransportProperties.class
})
public class TransportConfiguration {

    /**
     * Transport IN_MEMORY — defaut pour le mode LOCAL et les tests.
     * <p>
     * Actif quand {@code transport.type} est absent ou explicitement
     * positionne a {@code IN_MEMORY}.
     */
    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "IN_MEMORY", matchIfMissing = true)
    public ExecutionTransport inMemoryExecutionTransport() {
        return new InMemoryExecutionTransport();
    }

    /**
     * Transport RABBITMQ — implementation complete (ISSUE-030).
     * <p>
     * Utilise des exchanges FANOUT pour le broadcast des tasks/signals/events.
     * Chaque agent cree une file exclusive auto-delete.
     * Ack manuel (at-least-once). I/O sur Virtual Threads.
     */
    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "RABBITMQ")
    public ExecutionTransport rabbitMQExecutionTransport(RabbitMQTransportProperties props) {
        return new RabbitMQExecutionTransport(props);
    }

    /**
     * Transport HTTP — implementation complete (ISSUE-031).
     * <p>
     * POST les {@code TaskExecutionRequest} aux agents capables via leur
     * {@code httpCallbackUrl}. Le {@code AgentRegistryPort} est optionnel
     * via {@code ObjectProvider} pour permettre les tests unitaires Spring
     * sans registre d'agents. En production, le registre est toujours present.
     */
    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "HTTP")
    public ExecutionTransport httpExecutionTransport(HttpTransportProperties props,
            ObjectProvider<AgentRegistryPort> registryProvider) {
        return new HttpExecutionTransport(props, registryProvider.getIfAvailable());
    }

    /**
     * Transport SOCKET — implementation complete (ISSUE-032).
     * <p>
     * Connexions TCP persistantes. Detection automatique du role :
     * bind reussi = orchestrateur, port deja utilise = agent.
     * Best-effort (pas de garantie at-least-once). Reconnexion
     * automatique cote agent.
     */
    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "SOCKET")
    public ExecutionTransport socketExecutionTransport(SocketTransportProperties props) {
        return new SocketExecutionTransport(props);
    }
}
