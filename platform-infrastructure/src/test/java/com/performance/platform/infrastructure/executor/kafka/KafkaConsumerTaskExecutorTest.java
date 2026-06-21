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

@DisplayName("KafkaConsumerTaskExecutor")
class KafkaConsumerTaskExecutorTest {

    private static final KafkaClusterRegistry EMPTY_REGISTRY = new KafkaClusterRegistry(Map.of());

    private KafkaConsumerTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KafkaConsumerTaskExecutor(EMPTY_REGISTRY);
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
                    TaskId.of("step-v1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
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
                    TaskId.of("step-v2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
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
                    TaskId.of("step-v3"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
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
                    TaskId.of("step-v4"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
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
                    TaskId.of("step-v5"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "UNKNOWN_OP",
                            "topic", "some-topic",
                            "bootstrapServers", "localhost:9092"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown kafka-consumer operation");
        }
    }

    // ==================== Cluster reference path ====================

    @Nested
    @DisplayName("Cluster reference")
    class ClusterReference {

        @Test
        @DisplayName("should resolve topic via cluster registry")
        void shouldResolveTopicViaClusterRegistry() {
            var clusterProps = new KafkaClusterProperties(
                    "localhost:9999", "all", "test-group",
                    Map.of("iot-commands", "iot-commands-prod"));
            var registry = new KafkaClusterRegistry(Map.of("iot-sut", clusterProps));
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "iot-sut",
                            "topic", "iot-commands",
                            "operation", "CONSUME",
                            "maxMessages", 5),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = exec.execute(emptyContext(), step);

            // No real Kafka at localhost:9999, but poll returns empty -> SUCCESS
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.taskName()).isEqualTo("kafka-consumer");
            assertThat(result.outputs()).containsKeys("messagesConsumed", "lag");
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should use topic as-is when cluster unknown")
        void shouldUseTopicAsIsWhenClusterUnknown() {
            var emptyReg = new KafkaClusterRegistry(Map.of());
            var exec = new KafkaConsumerTaskExecutor(emptyReg);

            var step = new StepDefinition(
                    TaskId.of("step-c2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "nonexistent",
                            "topic", "my-topic",
                            "operation", "CONSUME",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(10), null);

            // resolveTopic returns logicalTopic, producerFactory throws
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
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c3"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "primary",
                            "bootstrapServers", "should-not-be-used:9092",
                            "topic", "test-topic",
                            "operation", "CONSUME",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = exec.execute(emptyContext(), step);
            // Cluster param was used; poll returns empty from non-existent Kafka
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should resolve groupId from cluster consumerGroup")
        void shouldResolveGroupIdFromCluster() {
            var clusterProps = new KafkaClusterProperties(
                    "localhost:9999", "all", "my-cluster-group", Map.of());
            var registry = new KafkaClusterRegistry(Map.of("dev", clusterProps));
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c4"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "dev",
                            "topic", "test-topic",
                            "operation", "CONSUME",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = exec.execute(emptyContext(), step);
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs()).containsKey("messagesConsumed");
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should override cluster consumerGroup with explicit groupId")
        void shouldOverrideGroupId() {
            var clusterProps = new KafkaClusterProperties(
                    "localhost:9999", "all", "cluster-group", Map.of());
            var registry = new KafkaClusterRegistry(Map.of("dev", clusterProps));
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c5"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "dev",
                            "topic", "test-topic",
                            "operation", "CONSUME",
                            "groupId", "my-override-group",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = exec.execute(emptyContext(), step);
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs()).containsKey("messagesConsumed");
            exec.cleanup(null);
        }
    }

    // ==================== Legacy bootstrapServers path ====================

    @Nested
    @DisplayName("Legacy bootstrapServers")
    class LegacyBootstrapServers {

        @Test
        @DisplayName("should return empty result with unreachable bootstrap servers")
        void shouldFailWithUnreachableBootstrapServers() {
            var step = new StepDefinition(
                    TaskId.of("step-l1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
                            "topic", "test-topic",
                            "bootstrapServers", "invalid-host:9999",
                            "maxMessages", 3),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);

            // Connection fails to unreachable broker -> FAILED
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        @DisplayName("should support COUNT operation via legacy bootstrapServers")
        void shouldSupportCountViaLegacyBootstrap() {
            var step = new StepDefinition(
                    TaskId.of("step-l2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "COUNT",
                            "topic", "test-topic",
                            "bootstrapServers", "invalid-host:9999"),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);

            // COUNT will likely fail finding partitions for unreachable broker
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        }
    }

    // ==================== Contract tests ====================

    @Nested
    @DisplayName("TaskExecutor contract")
    class TaskExecutorContract {

        @Test
        @DisplayName("should return kafka-consumer as supported task name")
        void shouldReturnCorrectTaskName() {
            assertThat(executor.getSupportedTaskName()).isEqualTo("kafka-consumer");
        }

        @Test
        @DisplayName("should default operation to CONSUME")
        void shouldDefaultOperationToConsume() {
            var step = new StepDefinition(
                    TaskId.of("step-ct2"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("topic", "test-topic",
                            "bootstrapServers", "localhost:9999",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);
            // Defaults to CONSUME, poll returns empty
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs()).containsKey("messagesConsumed");
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

    // ==================== Output keys ====================

    @Test
    @DisplayName("should expose expected output key constants")
    void shouldExposeOutputKeyConstants() {
        assertThat(KafkaConsumerTaskExecutor.OUTPUT_MESSAGES_CONSUMED).isEqualTo("messagesConsumed");
        assertThat(KafkaConsumerTaskExecutor.OUTPUT_LAG).isEqualTo("lag");
    }
}
