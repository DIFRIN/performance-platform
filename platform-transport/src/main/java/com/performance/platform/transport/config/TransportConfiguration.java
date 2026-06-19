package com.performance.platform.transport.config;

import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportType;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration automatique du transport.
 *
 * <p>Active les {@code @ConfigurationProperties} des 4 transports et
 * expose les {@code @Bean} conditionnels. Le {@code Bean} IN_MEMORY
 * est l'implementation par defaut ({@code matchIfMissing = true}).
 *
 * <p>Les {@code Bean} KAFKA / RABBITMQ / HTTP / SOCKET sont des squelettes
 * qui seront completes dans ISSUE-029 a ISSUE-032. Ils sont marques
 * {@link Lazy @Lazy} pour eviter un crash au demarrage.
 *
 * <p><strong>TransportType IN_MEMORY</strong> est le defaut pour le mode LOCAL
 * et les tests. Les transports reels sont selectionnes via
 * {@code transport.type} dans {@code application.yaml} ou via la variable
 * d'environnement {@code TRANSPORT_TYPE} (ADR-006 : env prioritaire).
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
     * Transport KAFKA — squelette, complete par ISSUE-029.
     */
    @Bean
    @Lazy
    @ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
    public ExecutionTransport kafkaExecutionTransport(KafkaTransportProperties props) {
        throw new UnsupportedOperationException(
                "Kafka transport not yet implemented — ISSUE-029");
    }

    /**
     * Transport RABBITMQ — squelette, complete par ISSUE-030.
     */
    @Bean
    @Lazy
    @ConditionalOnProperty(name = "transport.type", havingValue = "RABBITMQ")
    public ExecutionTransport rabbitMQExecutionTransport(RabbitMQTransportProperties props) {
        throw new UnsupportedOperationException(
                "RabbitMQ transport not yet implemented — ISSUE-030");
    }

    /**
     * Transport HTTP — squelette, complete par ISSUE-031.
     */
    @Bean
    @Lazy
    @ConditionalOnProperty(name = "transport.type", havingValue = "HTTP")
    public ExecutionTransport httpExecutionTransport(HttpTransportProperties props) {
        throw new UnsupportedOperationException(
                "HTTP transport not yet implemented — ISSUE-031");
    }

    /**
     * Transport SOCKET — squelette, complete par ISSUE-032.
     */
    @Bean
    @Lazy
    @ConditionalOnProperty(name = "transport.type", havingValue = "SOCKET")
    public ExecutionTransport socketExecutionTransport(SocketTransportProperties props) {
        throw new UnsupportedOperationException(
                "Socket transport not yet implemented — ISSUE-032");
    }
}
