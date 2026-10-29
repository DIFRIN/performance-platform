package com.performance.platform.infrastructure.executor.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KafkaClusterRegistry")
class KafkaClusterRegistryTest {

    private static final KafkaClusterProperties DEFAULT_CLUSTER = new KafkaClusterProperties(
            "localhost:9092", "all", "perf-consumer", Map.of()
    );

    private static final KafkaClusterProperties IOT_SUT_CLUSTER = new KafkaClusterProperties(
            "localhost:9093", "1", "perf-test-consumer",
            Map.of("iot-commands", "iot-commands-dev", "device-events", "device-events-dev")
    );

    private final KafkaClusterRegistry registry = new KafkaClusterRegistry(Map.of(
            "default", DEFAULT_CLUSTER,
            "iot-sut", IOT_SUT_CLUSTER
    ));

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("returns cluster properties for known name")
        void shouldReturnClusterForKnownName() {
            assertThat(registry.get("default")).isSameAs(DEFAULT_CLUSTER);
            assertThat(registry.get("iot-sut")).isSameAs(IOT_SUT_CLUSTER);
        }

        @Test
        @DisplayName("returns null for unknown cluster name")
        void shouldReturnNullForUnknownCluster() {
            assertThat(registry.get("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("resolveTopic()")
    class ResolveTopicTests {

        @Test
        @DisplayName("resolves logical topic to physical topic when mapped")
        void shouldResolveTopicWithMapping() {
            assertThat(registry.resolveTopic("iot-sut", "iot-commands"))
                    .isEqualTo("iot-commands-dev");
            assertThat(registry.resolveTopic("iot-sut", "device-events"))
                    .isEqualTo("device-events-dev");
        }

        @Test
        @DisplayName("returns logical name when no mapping exists in cluster topics")
        void shouldResolveTopicFallbackToLogicalName() {
            assertThat(registry.resolveTopic("iot-sut", "raw-topic"))
                    .isEqualTo("raw-topic");
        }

        @Test
        @DisplayName("returns logical name when cluster has no topic overrides")
        void shouldResolveTopicFallbackForClusterWithEmptyTopics() {
            assertThat(registry.resolveTopic("default", "anything"))
                    .isEqualTo("anything");
        }

        @Test
        @DisplayName("returns logical name for unknown cluster")
        void shouldResolveTopicFallbackForUnknownCluster() {
            assertThat(registry.resolveTopic("unknown", "anything"))
                    .isEqualTo("anything");
        }
    }

    @Nested
    @DisplayName("producerFactory()")
    class ProducerFactoryTests {

        @Test
        @DisplayName("creates ProducerFactory with correct bootstrap servers and acks")
        void shouldCreateProducerFactory() {
            ProducerFactory<String, String> pf = registry.producerFactory("default");

            assertThat(pf).isNotNull();
            Map<String, Object> config = pf.getConfigurationProperties();
            assertThat(config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo("localhost:9092");
            assertThat(config.get(ProducerConfig.ACKS_CONFIG))
                    .isEqualTo("all");
            assertThat(config.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                    .isEqualTo(org.apache.kafka.common.serialization.StringSerializer.class);
            assertThat(config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                    .isEqualTo(org.apache.kafka.common.serialization.StringSerializer.class);
        }

        @Test
        @DisplayName("ProducerFactory respects cluster-specific acks setting")
        void shouldCreateProducerFactoryWithClusterSpecificAcks() {
            ProducerFactory<String, String> pf = registry.producerFactory("iot-sut");

            Map<String, Object> config = pf.getConfigurationProperties();
            assertThat(config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo("localhost:9093");
            assertThat(config.get(ProducerConfig.ACKS_CONFIG))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for unknown cluster")
        void shouldThrowForUnknownCluster() {
            assertThatThrownBy(() -> registry.producerFactory("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown Kafka cluster")
                    .hasMessageContaining("unknown");
        }
    }

    @Nested
    @DisplayName("consumerFactory()")
    class ConsumerFactoryTests {

        @Test
        @DisplayName("creates ConsumerFactory with correct config")
        void shouldCreateConsumerFactory() {
            ConsumerFactory<String, String> cf = registry.consumerFactory("default", "my-group");

            assertThat(cf).isNotNull();
            Map<String, Object> config = cf.getConfigurationProperties();
            assertThat(config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo("localhost:9092");
            assertThat(config.get(ConsumerConfig.GROUP_ID_CONFIG))
                    .isEqualTo("my-group");
            assertThat(config.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
                    .isEqualTo("earliest");
            assertThat(config.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
                    .isEqualTo(false);
            assertThat(config.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
                    .isEqualTo(org.apache.kafka.common.serialization.StringDeserializer.class);
            assertThat(config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                    .isEqualTo(org.apache.kafka.common.serialization.StringDeserializer.class);
        }

        @Test
        @DisplayName("ConsumerFactory uses custom group ID per call")
        void shouldCreateConsumerFactoryWithCustomGroupId() {
            ConsumerFactory<String, String> cf = registry.consumerFactory("iot-sut", "custom-group");

            Map<String, Object> config = cf.getConfigurationProperties();
            assertThat(config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                    .isEqualTo("localhost:9093");
            assertThat(config.get(ConsumerConfig.GROUP_ID_CONFIG))
                    .isEqualTo("custom-group");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for unknown cluster")
        void shouldThrowForUnknownCluster() {
            assertThatThrownBy(() -> registry.consumerFactory("unknown", "group"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown Kafka cluster")
                    .hasMessageContaining("unknown");
        }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("KafkaClusterProperties defaults producerAcks to 'all' when null")
        void shouldDefaultProducerAcksToAll() {
            var props = new KafkaClusterProperties("localhost:9092", null, null, null);
            assertThat(props.producerAcks()).isEqualTo("all");
        }

        @Test
        @DisplayName("KafkaClusterProperties defaults consumerGroup to 'perf-consumer' when null")
        void shouldDefaultConsumerGroup() {
            var props = new KafkaClusterProperties("localhost:9092", "1", null, null);
            assertThat(props.consumerGroup()).isEqualTo("perf-consumer");
        }

        @Test
        @DisplayName("KafkaClusterProperties defaults topics to empty map when null")
        void shouldDefaultTopicsToEmptyMap() {
            var props = new KafkaClusterProperties("localhost:9092", "all", "group", null);
            assertThat(props.topics()).isEmpty();
        }

        @Test
        @DisplayName("KafkaClusterProperties rejects null bootstrapServers")
        void shouldRejectNullBootstrapServers() {
            assertThatThrownBy(() -> new KafkaClusterProperties(null, "all", "group", Map.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("bootstrapServers");
        }

        @Test
        @DisplayName("Registry constructor defensively copies the input map")
        void shouldDefensivelyCopyInputMap() {
            var mutableMap = new java.util.HashMap<>(Map.of(
                    "default", DEFAULT_CLUSTER
            ));
            var reg = new KafkaClusterRegistry(mutableMap);
            mutableMap.clear();
            assertThat(reg.get("default")).isNotNull();
        }
    }
}
