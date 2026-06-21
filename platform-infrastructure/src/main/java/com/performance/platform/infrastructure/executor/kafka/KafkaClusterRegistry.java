package com.performance.platform.infrastructure.executor.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

public class KafkaClusterRegistry {

    private static final Logger log = LoggerFactory.getLogger(KafkaClusterRegistry.class);

    private final Map<String, KafkaClusterProperties> clusters;

    public KafkaClusterRegistry(Map<String, KafkaClusterProperties> clusters) {
        this.clusters = Map.copyOf(clusters);
        log.info("action=kafka_cluster_registry_initialized clusterCount={}", this.clusters.size());
    }

    public KafkaClusterProperties get(String clusterName) {
        return clusters.get(clusterName);
    }

    public String resolveTopic(String clusterName, String logicalTopicName) {
        KafkaClusterProperties cluster = clusters.get(clusterName);
        if (cluster == null) {
            log.debug("action=resolve_topic_unknown_cluster clusterName={} logicalTopic={}",
                    clusterName, logicalTopicName);
            return logicalTopicName;
        }
        String resolved = cluster.topics().getOrDefault(logicalTopicName, logicalTopicName);
        if (!resolved.equals(logicalTopicName)) {
            log.debug("action=resolve_topic_mapped clusterName={} logicalTopic={} physicalTopic={}",
                    clusterName, logicalTopicName, resolved);
        }
        return resolved;
    }

    public ProducerFactory<String, String> producerFactory(String clusterName) {
        KafkaClusterProperties cluster = clusters.get(clusterName);
        if (cluster == null) {
            throw new IllegalArgumentException("Unknown Kafka cluster: " + clusterName);
        }
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, cluster.producerAcks());
        log.debug("action=producer_factory_created clusterName={} bootstrapServers={} acks={}",
                clusterName, cluster.bootstrapServers(), cluster.producerAcks());
        return new DefaultKafkaProducerFactory<>(config);
    }

    public ConsumerFactory<String, String> consumerFactory(String clusterName, String groupId) {
        KafkaClusterProperties cluster = clusters.get(clusterName);
        if (cluster == null) {
            throw new IllegalArgumentException("Unknown Kafka cluster: " + clusterName);
        }
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.bootstrapServers());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        log.debug("action=consumer_factory_created clusterName={} groupId={} bootstrapServers={}",
                clusterName, groupId, cluster.bootstrapServers());
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
