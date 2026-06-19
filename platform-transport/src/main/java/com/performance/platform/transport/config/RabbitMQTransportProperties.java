package com.performance.platform.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration du transport RabbitMQ.
 * Préfixe : {@code transport.rabbitmq}
 *
 * <p>Chaque champ correspond à une propriété dans {@code application.yaml} :
 * <pre>
 * transport:
 *   rabbitmq:
 *     host: "localhost"
 *     port: 5672
 *     virtual-host: "/"
 *     tasks-exchange: "tasks"
 *     events-exchange: "events"
 *     signals-exchange: "signals"
 *     username: "guest"
 *     password: "guest"
 * </pre>
 */
@ConfigurationProperties(prefix = "transport.rabbitmq")
public record RabbitMQTransportProperties(
        String host,
        int port,
        String virtualHost,
        String tasksExchange,
        String eventsExchange,
        String signalsExchange,
        String username,
        String password
) {}
