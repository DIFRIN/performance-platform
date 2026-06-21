package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.Map;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kafka TaskExecutors IT")
@Testcontainers
@Tag("integration-tests")
class KafkaTaskExecutorsIT {

    @Container
    @SuppressWarnings("deprecation")
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private static KafkaConsumerTaskExecutor consumerExecutor;
    private static KafkaProducerTaskExecutor producerExecutor;

    private static final KafkaClusterRegistry EMPTY_REGISTRY = new KafkaClusterRegistry(Map.of());

    @BeforeAll
    static void setUp() {
        consumerExecutor = new KafkaConsumerTaskExecutor();
        producerExecutor = new KafkaProducerTaskExecutor(EMPTY_REGISTRY);
    }

    @AfterAll
    static void tearDown() {
        consumerExecutor.cleanup(null);
        producerExecutor.cleanup(null);
    }

    private String bootstrapServers() {
        return kafka.getBootstrapServers();
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"),
                ScenarioId.of("scenario-001"));
    }

    // ==================== Seed helpers (using raw Kafka clients) ====================

    /**
     * Produces messages directly to Kafka using a raw producer (independent of
     * KafkaProducerTaskExecutor, so consumer tests don't depend on the producer
     * executor).
     */
    private void seedMessages(String topic, int count) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (var producer = new KafkaProducer<String, String>(props)) {
            for (int i = 1; i <= count; i++) {
                producer.send(new ProducerRecord<>(topic, "key-" + i, "value-" + i));
            }
            producer.flush();
        }
    }

    /**
     * Consumes messages directly from Kafka using a raw consumer (independent of
     * KafkaConsumerTaskExecutor, so producer tests don't depend on the consumer
     * executor).
     */
    private int consumeAll(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verifier-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));
            int consumed = 0;
            long deadline = System.currentTimeMillis() + 30_000;

            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(java.time.Duration.ofMillis(5000));
                int count = records.count();
                consumed += count;
                if (count == 0) {
                    break; // no more messages
                }
            }
            consumer.commitSync();
            return consumed;
        }
    }

    // ==================== Consumer tests ====================

    @Nested
    @DisplayName("CONSUME operation")
    class ConsumeOperation {

        @Test
        @DisplayName("should consume messages from a topic")
        void shouldConsumeMessages() {
            String topic = "test-consume-" + UUID.randomUUID().toString().substring(0, 8);
            seedMessages(topic, 15);

            var step = new StepDefinition(
                    TaskId.of("step-c1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "groupId", "test-consumer-group",
                            "maxMessages", 10),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.taskName()).isEqualTo("kafka-consumer");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey("messagesConsumed");
            assertThat(result.outputs()).containsKey("lag");

            int consumed = (int) result.outputs().get("messagesConsumed");
            assertThat(consumed).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("should consume up to maxMessages limit")
        void shouldRespectMaxMessagesLimit() {
            String topic = "test-max-msg-" + UUID.randomUUID().toString().substring(0, 8);
            seedMessages(topic, 50);

            var step = new StepDefinition(
                    TaskId.of("step-c2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "groupId", "test-consumer-group-2",
                            "maxMessages", 5),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            int consumed = (int) result.outputs().get("messagesConsumed");
            assertThat(consumed).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("should return 0 messagesConsumed on empty topic")
        void shouldReturnZeroOnEmptyTopic() {
            String topic = "test-empty-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-c3"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "groupId", "test-empty-group"),
                    null, null, Duration.ofSeconds(15), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            int consumed = (int) result.outputs().get("messagesConsumed");
            assertThat(consumed).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("COUNT operation")
    class CountOperation {

        @Test
        @DisplayName("should count messages in a topic")
        void shouldCountMessages() {
            String topic = "test-count-" + UUID.randomUUID().toString().substring(0, 8);
            seedMessages(topic, 25);

            var step = new StepDefinition(
                    TaskId.of("step-cn1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "COUNT", "topic", topic,
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey("messagesConsumed");
            long consumed = ((Number) result.outputs().get("messagesConsumed")).longValue();
            assertThat(consumed).isEqualTo(25L);
        }

        @Test
        @DisplayName("should return 0 count for empty topic (auto-created by broker)")
        void shouldReturnZeroForEmptyTopic() {
            String topic = "test-count-empty-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-cn2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "COUNT", "topic", topic,
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            long consumed = ((Number) result.outputs().get("messagesConsumed")).longValue();
            assertThat(consumed).isEqualTo(0L);
        }
    }

    // ==================== Producer tests ====================

    @Nested
    @DisplayName("PRODUCE operation")
    class ProduceOperation {

        @Test
        @DisplayName("should produce messages to a topic")
        void shouldProduceMessages() {
            String topic = "test-produce-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-p1"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "messageCount", 20,
                            "messageTemplate", "hello-{index}"),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.taskName()).isEqualTo("kafka-producer");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey("messagesProduced");
            assertThat(result.outputs().get("messagesProduced")).isEqualTo(20);

            // Verify messages actually arrived
            int consumed = consumeAll(topic);
            assertThat(consumed).isEqualTo(20);
        }

        @Test
        @DisplayName("should use messageTemplate with index")
        void shouldUseMessageTemplate() {
            String topic = "test-template-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-p2"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "messageCount", 5,
                            "messageTemplate", "order-{index}-created"),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get("messagesProduced")).isEqualTo(5);
        }

        @Test
        @DisplayName("should support PRELOAD as alias for PRODUCE")
        void shouldSupportPreloadOperation() {
            String topic = "test-preload-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-p3"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRELOAD", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "messageCount", 3,
                            "messageTemplate", "preload-msg"),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get("messagesProduced")).isEqualTo(3);

            int consumed = consumeAll(topic);
            assertThat(consumed).isEqualTo(3);
        }

        @Test
        @DisplayName("should default to 1 message when messageCount absent")
        void shouldDefaultToSingleMessage() {
            String topic = "test-default-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-p4"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE", "topic", topic,
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get("messagesProduced")).isEqualTo(1);
        }
    }

    // ==================== Error handling ====================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("consumer should fail when topic is missing")
        void consumerShouldFailWhenTopicMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-e1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("topic");
        }

        @Test
        @DisplayName("consumer should fail when bootstrapServers is missing")
        void consumerShouldFailWhenBootstrapServersMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-e2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME", "topic", "some-topic"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("bootstrapServers");
        }

        @Test
        @DisplayName("producer should fail when topic is missing")
        void producerShouldFailWhenTopicMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-e3"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE",
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("topic");
        }

        @Test
        @DisplayName("producer should fail when bootstrapServers is missing")
        void producerShouldFailWhenBootstrapServersMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-e4"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE", "topic", "some-topic"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("bootstrapServers");
        }

        @Test
        @DisplayName("consumer should fail for unknown operation")
        void consumerShouldFailForUnknownOperation() {
            var step = new StepDefinition(
                    TaskId.of("step-e5"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "UNKNOWN_OP", "topic", "some-topic",
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown kafka-consumer operation");
        }

        @Test
        @DisplayName("producer should fail for unknown operation")
        void producerShouldFailForUnknownOperation() {
            var step = new StepDefinition(
                    TaskId.of("step-e6"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "UNKNOWN_OP", "topic", "some-topic",
                            "bootstrapServers", bootstrapServers()),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown kafka-producer operation");
        }
    }

    // ==================== Contract tests ====================

    @Nested
    @DisplayName("TaskExecutor contract")
    class TaskExecutorContract {

        @Test
        @DisplayName("consumer should return 'kafka-consumer' as supported task name")
        void consumerShouldReturnCorrectTaskName() {
            assertThat(consumerExecutor.getSupportedTaskName()).isEqualTo("kafka-consumer");
        }

        @Test
        @DisplayName("producer should return 'kafka-producer' as supported task name")
        void producerShouldReturnCorrectTaskName() {
            assertThat(producerExecutor.getSupportedTaskName()).isEqualTo("kafka-producer");
        }

        @Test
        @DisplayName("consumer should include correct taskName in TaskResult")
        void consumerShouldIncludeTaskNameInResult() {
            String topic = "test-taskname-" + UUID.randomUUID().toString().substring(0, 8);
            seedMessages(topic, 3);

            var step = new StepDefinition(
                    TaskId.of("step-t1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "groupId", "contract-group"),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = consumerExecutor.execute(emptyContext(), step);

            assertThat(result.taskName()).isEqualTo("kafka-consumer");
            assertThat(result.taskId()).isEqualTo(TaskId.of("step-t1"));
        }

        @Test
        @DisplayName("producer should include correct taskName in TaskResult")
        void producerShouldIncludeTaskNameInResult() {
            String topic = "test-taskname-p-" + UUID.randomUUID().toString().substring(0, 8);

            var step = new StepDefinition(
                    TaskId.of("step-t2"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE", "topic", topic,
                            "bootstrapServers", bootstrapServers(),
                            "messageCount", 1),
                    null, null, Duration.ofSeconds(30), null);

            TaskResult result = producerExecutor.execute(emptyContext(), step);

            assertThat(result.taskName()).isEqualTo("kafka-producer");
            assertThat(result.taskId()).isEqualTo(TaskId.of("step-t2"));
        }
    }

    // ==================== StatefulResourceCleaner tests ====================

    @Nested
    @DisplayName("StatefulResourceCleaner")
    class CleanupTests {

        @Test
        @DisplayName("consumer cleanup with null executionId should not throw")
        void consumerCleanupNullShouldNotThrow() {
            consumerExecutor.cleanup(null);
            // No exception thrown = success
        }

        @Test
        @DisplayName("producer cleanup with null executionId should not throw")
        void producerCleanupNullShouldNotThrow() {
            producerExecutor.cleanup(null);
            // No exception thrown = success
        }

        @Test
        @DisplayName("cleanup with unknown executionId should be a no-op")
        void cleanupUnknownExecutionIdShouldBeNoop() {
            consumerExecutor.cleanup(ExecutionId.of("non-existent"));
            producerExecutor.cleanup(ExecutionId.of("non-existent"));
            // No exception thrown = success
        }
    }

    // ==================== Unit tests for resolveTemplate ====================

    @Nested
    @DisplayName("resolveTemplate utility")
    class ResolveTemplateTests {

        @Test
        @DisplayName("should replace {index} placeholder")
        void shouldReplaceIndexPlaceholder() {
            String result = KafkaProducerTaskExecutor.resolveTemplate("msg-{index}", 42);
            assertThat(result).isEqualTo("msg-42");
        }

        @Test
        @DisplayName("should replace {timestamp} placeholder")
        void shouldReplaceTimestampPlaceholder() {
            String result = KafkaProducerTaskExecutor.resolveTemplate("ts-{timestamp}", 1);
            assertThat(result).startsWith("ts-");
            assertThat(result).isNotEqualTo("ts-{timestamp}");
        }

        @Test
        @DisplayName("should return template as-is when no placeholders")
        void shouldReturnAsIsWhenNoPlaceholders() {
            String result = KafkaProducerTaskExecutor.resolveTemplate("plain message", 1);
            assertThat(result).isEqualTo("plain message");
        }
    }
}
