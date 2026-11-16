package com.performance.platform.infrastructure.executor.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "platform")
public record PlatformKafkaProperties(
        Map<String, KafkaClusterProperties> kafkaClusters
) {
    public PlatformKafkaProperties {
        kafkaClusters = kafkaClusters != null ? Map.copyOf(kafkaClusters) : Map.of();
    }
}
