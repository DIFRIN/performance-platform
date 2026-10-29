package com.performance.platform.infrastructure.executor.kafka;

import java.util.Map;
import java.util.Objects;

public record KafkaClusterProperties(
        String bootstrapServers,
        String producerAcks,
        String consumerGroup,
        Map<String, String> topics
) {
    public KafkaClusterProperties {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers required");
        producerAcks = producerAcks != null ? producerAcks : "all";
        consumerGroup = consumerGroup != null ? consumerGroup : "perf-consumer";
        topics = topics != null ? Map.copyOf(topics) : Map.of();
    }
}
