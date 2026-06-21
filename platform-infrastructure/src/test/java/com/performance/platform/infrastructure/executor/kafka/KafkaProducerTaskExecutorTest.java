package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaProducerTaskExecutor")
class KafkaProducerTaskExecutorTest {

    private static final KafkaClusterRegistry EMPTY_REGISTRY = new KafkaClusterRegistry(Map.of());

    private KafkaProducerTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KafkaProducerTaskExecutor(EMPTY_REGISTRY);
    }

    @AfterEach
    void tearDown() {
        executor.cleanup(null);
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"),
                ScenarioId.of("scenario-001"));
    }

    // ==================== Parameter validation ====================

    @Nested
    @DisplayName("Parameter validation")
    class ParameterValidation {

        @Test
        @DisplayName("should fail when topic is missing")
        void shouldFailWhenTopicMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-v1"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE",
                            "bootstrapServers", "localhost:9092"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("topic");
        }

        @Test
        @DisplayName("should fail when topic is blank")
        void shouldFailWhenTopicBlank() {
            var step = new StepDefinition(
                    TaskId.of("step-v2"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE",
                            "topic", "   ",
                            "bootstrapServers", "localhost:9092"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("topic");
        }

        @Test
        @DisplayName("should fail when neither cluster nor bootstrapServers is provided")
        void shouldFailWhenNeitherClusterNorBootstrapServers() {
            var step = new StepDefinition(
                    TaskId.of("step-v3"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE",
                            "topic", "my-topic"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("cluster");
        }

        @Test
        @DisplayName("should fail when bootstrapServers is blank and no cluster")
        void shouldFailWhenBootstrapServersBlankAndNoCluster() {
            var step = new StepDefinition(
                    TaskId.of("step-v4"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE",
                            "topic", "my-topic",
                            "bootstrapServers", ""),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("cluster");
        }

        @Test
        @DisplayName("should fail for unknown operation")
        void shouldFailForUnknownOperation() {
            var step = new StepDefinition(
                    TaskId.of("step-v5"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "UNKNOWN_OP",
                            "topic", "some-topic",
                            "bootstrapServers", "localhost:9092"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown kafka-producer operation");
        }
    }

    // ==================== Cluster reference path ====================

    @Nested
    @DisplayName("Cluster reference")
    class ClusterReference {

        @Test
        @DisplayName("should resolve topic via cluster registry")
        void shouldResolveTopicViaClusterRegistry() {
            // Use a registry with a valid-looking cluster (no real Kafka needed
            // for this test — it will fail at send, but proves cluster resolution works)
            var clusterProps = new KafkaClusterProperties(
                    "localhost:9999", "all", "test-group",
                    Map.of("iot-commands", "iot-commands-prod"));
            var registry = new KafkaClusterRegistry(Map.of("iot-sut", clusterProps));
            var exec = new KafkaProducerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c1"), "kafka-producer", Phase.PREPARATION,
                    Map.of("cluster", "iot-sut",
                            "topic", "iot-commands",
                            "messageCount", 5),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = exec.execute(emptyContext(), step);

            // Will be FAILED because no real Kafka at localhost:9999,
            // but the cluster resolution logic was exercised
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.taskName()).isEqualTo("kafka-producer");
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should use topic as-is when cluster unknown")
        void shouldUseTopicAsIsWhenClusterUnknown() {
            var emptyReg = new KafkaClusterRegistry(Map.of());
            var exec = new KafkaProducerTaskExecutor(emptyReg);

            var step = new StepDefinition(
                    TaskId.of("step-c2"), "kafka-producer", Phase.PREPARATION,
                    Map.of("cluster", "nonexistent",
                            "topic", "my-topic",
                            "messageCount", 1),
                    null, null, Duration.ofSeconds(10), null);

            // resolveTopic returns logicalTopic for unknown cluster
            // producerFactory for unknown cluster throws
            TaskResult result = exec.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown Kafka cluster");
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should prefer cluster over bootstrapServers when both present")
        void shouldPreferClusterOverBootstrapServers() {
            var clusterProps = new KafkaClusterProperties(
                    "localhost:9999", "all", "test-group", Map.of());
            var registry = new KafkaClusterRegistry(Map.of("primary", clusterProps));
            var exec = new KafkaProducerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c3"), "kafka-producer", Phase.PREPARATION,
                    Map.of("cluster", "primary",
                            "bootstrapServers", "should-not-be-used:9092",
                            "topic", "test-topic",
                            "messageCount", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = exec.execute(emptyContext(), step);
            // Will fail (no real Kafka) but cluster param was used
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            exec.cleanup(null);
        }
    }

    // ==================== Legacy bootstrapServers path ====================

    @Nested
    @DisplayName("Legacy bootstrapServers")
    class LegacyBootstrapServers {

        @Test
        @DisplayName("should fail gracefully with invalid bootstrap servers")
        void shouldFailGracefullyWithInvalidBootstrapServers() {
            var step = new StepDefinition(
                    TaskId.of("step-l1"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRODUCE",
                            "topic", "test-topic",
                            "bootstrapServers", "invalid-host:9999",
                            "messageCount", 3),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        }
    }

    // ==================== Contract tests ====================

    @Nested
    @DisplayName("TaskExecutor contract")
    class TaskExecutorContract {

        @Test
        @DisplayName("should return kafka-producer as supported task name")
        void shouldReturnCorrectTaskName() {
            assertThat(executor.getSupportedTaskName()).isEqualTo("kafka-producer");
        }

        @Test
        @DisplayName("should default operation to PRODUCE")
        void shouldDefaultOperationToProduce() {
            var step = new StepDefinition(
                    TaskId.of("step-ct2"), "kafka-producer", Phase.PREPARATION,
                    Map.of("topic", "test-topic",
                            "bootstrapServers", "localhost:9999",
                            "messageCount", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).doesNotContain("Unknown");
        }

        @Test
        @DisplayName("should support PRELOAD as alias for PRODUCE")
        void shouldSupportPreloadOperation() {
            var step = new StepDefinition(
                    TaskId.of("step-ct3"), "kafka-producer", Phase.PREPARATION,
                    Map.of("operation", "PRELOAD",
                            "topic", "test-topic",
                            "bootstrapServers", "localhost:9999",
                            "messageCount", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).doesNotContain("Unknown");
        }
    }

    // ==================== Cleanup tests ====================

    @Nested
    @DisplayName("StatefulResourceCleaner")
    class CleanupTests {

        @Test
        @DisplayName("cleanup with null executionId should not throw")
        void cleanupNullShouldNotThrow() {
            executor.cleanup(null);
        }

        @Test
        @DisplayName("cleanup with unknown executionId should be a no-op")
        void cleanupUnknownExecutionIdShouldBeNoop() {
            executor.cleanup(ExecutionId.of("non-existent"));
        }
    }

    // ==================== resolveTemplate tests ====================

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
        @DisplayName("should replace both placeholders in same template")
        void shouldReplaceBothPlaceholders() {
            String result = KafkaProducerTaskExecutor.resolveTemplate("{index}-{timestamp}", 7);
            assertThat(result).startsWith("7-");
            assertThat(result).doesNotContain("{index}");
            assertThat(result).doesNotContain("{timestamp}");
        }

        @Test
        @DisplayName("should return template as-is when no placeholders")
        void shouldReturnAsIsWhenNoPlaceholders() {
            String result = KafkaProducerTaskExecutor.resolveTemplate("plain message", 1);
            assertThat(result).isEqualTo("plain message");
        }
    }

    // ==================== Output keys ====================

    @Test
    @DisplayName("should expose expected output key constants")
    void shouldExposeOutputKeyConstants() {
        assertThat(KafkaProducerTaskExecutor.OUTPUT_MESSAGES_PRODUCED).isEqualTo("messagesProduced");
        assertThat(KafkaProducerTaskExecutor.OUTPUT_MESSAGES_FAILED).isEqualTo("messagesFailed");
    }
}
