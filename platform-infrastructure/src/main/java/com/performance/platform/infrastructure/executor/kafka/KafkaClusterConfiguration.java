package com.performance.platform.infrastructure.executor.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformKafkaProperties.class)
public class KafkaClusterConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaClusterConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "platform.kafka-clusters", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaClusterRegistry kafkaClusterRegistry(PlatformKafkaProperties props) {
        var registry = new KafkaClusterRegistry(props.kafkaClusters());
        if (props.kafkaClusters().isEmpty()) {
            log.warn("action=no_kafka_cluster_configured prefix=platform.kafka-clusters");
        }
        return registry;
    }
}
