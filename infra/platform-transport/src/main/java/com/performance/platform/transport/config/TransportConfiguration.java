package com.performance.platform.transport.config;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.http.HttpExecutionTransport;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.tls.TransportTls;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration automatique du transport.
 *
 * <p>Active les {@code @ConfigurationProperties} des transports supportes et
 * expose les {@code @Bean} conditionnels. Le {@code Bean} IN_MEMORY
 * est l'implementation par defaut ({@code matchIfMissing = true}).
 *
 * <p>Les transports supportes sont IN_MEMORY, HTTP et KAFKA (ADR-056).
 * RabbitMQ et le transport socket sont des plugins externes via CUSTOM.
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
@Import(TransportTlsConfiguration.class)
@EnableConfigurationProperties({
        KafkaTransportProperties.class,
        HttpTransportProperties.class,
        HttpListenerProperties.class
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
    public InMemoryExecutionTransport inMemoryExecutionTransport() {
        return new InMemoryExecutionTransport();
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
            ObjectProvider<AgentRegistryPort> registryProvider, HttpListenerProperties listener,
            TransportTls tls, @Value("${runtime.role:ORCHESTRATOR}") String role) {
        return new HttpExecutionTransport(props, registryProvider.getIfAvailable(), listener, tls, role);
    }
}
