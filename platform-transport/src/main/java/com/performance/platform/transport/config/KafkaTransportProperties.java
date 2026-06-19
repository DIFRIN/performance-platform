package com.performance.platform.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration du transport Kafka.
 * Préfixe : {@code transport.kafka}
 *
 * <p>Chaque champ correspond à une propriété dans {@code application.yaml} :
 * <pre>
 * transport:
 *   kafka:
 *     bootstrap-servers: "localhost:9092"
 *     tasks-topic: "agents-tasks"
 *     events-topic: "agents-events"
 *     signals-topic: "agents-signals"
 *     producer-acks: "all"
 *     consumer-group: "${agent.id}"
 * </pre>
 * <p>
 * Le {@code consumerGroup} est obligatoire : chaque agent doit avoir un consumer group
 * unique (egal a son {@code agentId}) pour recevoir tous les messages du topic tasks
 * (ADR-009).
 */
@ConfigurationProperties(prefix = "transport.kafka")
public record KafkaTransportProperties(
        String bootstrapServers,
        String tasksTopic,
        String eventsTopic,
        String signalsTopic,
        String producerAcks,
        String consumerGroup
) {}
