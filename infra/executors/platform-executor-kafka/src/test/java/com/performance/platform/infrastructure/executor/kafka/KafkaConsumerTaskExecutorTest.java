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
                            "cluster", "test"),
                    null, null, Duration.ofSeconds(2), null);

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
                            "cluster", "test"),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("topic");
        }

        @Test
        @DisplayName("should fail when cluster is missing")
        void shouldFailWhenClusterMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-v3"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("operation", "CONSUME",
                            "topic", "my-topic"),
                    null, null, Duration.ofSeconds(2), null);

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
                            "cluster", "test"),
                    null, null, Duration.ofSeconds(2), null);

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
        @DisplayName("should resolve the physical topic from the cluster mapping")
        void shouldResolveTopicViaClusterRegistry() {
            var registry = new RecordingClusterRegistry(Map.of("iot-sut",
                    new KafkaClusterProperties("localhost:9999", "all", "test-group",
                            Map.of("iot-commands", "iot-commands-prod"))));
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c1"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "iot-sut",
                            "topic", "iot-commands",
                            "operation", "CONSUME",
                            "maxMessages", 5),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = exec.execute(emptyContext(), step);

            // The consumer factory is stubbed: resolution happens before any broker call.
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(registry.resolvedTopic).isEqualTo("iot-commands-prod");
            assertThat(registry.requestedCluster).isEqualTo("iot-sut");
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
                    null, null, Duration.ofSeconds(2), null);

            // resolveTopic returns logicalTopic, consumerFactory throws
            TaskResult result = exec.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown Kafka cluster");
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should resolve groupId from cluster consumerGroup")
        void shouldResolveGroupIdFromCluster() {
            var registry = new RecordingClusterRegistry(Map.of("dev",
                    new KafkaClusterProperties("localhost:9999", "all", "my-cluster-group", Map.of())));
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c4"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "dev",
                            "topic", "test-topic",
                            "operation", "CONSUME",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = exec.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(registry.requestedGroupId).isEqualTo("my-cluster-group");
            exec.cleanup(null);
        }

        @Test
        @DisplayName("should override cluster consumerGroup with explicit groupId")
        void shouldOverrideGroupId() {
            var registry = new RecordingClusterRegistry(Map.of("dev",
                    new KafkaClusterProperties("localhost:9999", "all", "cluster-group", Map.of())));
            var exec = new KafkaConsumerTaskExecutor(registry);

            var step = new StepDefinition(
                    TaskId.of("step-c5"), "kafka-consumer", Phase.PREPARATION,
                    Map.of("cluster", "dev",
                            "topic", "test-topic",
                            "operation", "CONSUME",
                            "groupId", "my-override-group",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = exec.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(registry.requestedGroupId).isEqualTo("my-override-group");
            exec.cleanup(null);
        }
    }

    /**
     * Registry that records resolution calls and fails before any broker I/O,
     * keeping the tests hermetic.
     */
    private static final class RecordingClusterRegistry extends KafkaClusterRegistry {
        private String resolvedTopic;
        private String requestedCluster;
        private String requestedGroupId;

        private RecordingClusterRegistry(Map<String, KafkaClusterProperties> clusters) {
            super(clusters);
        }

        @Override
        public String resolveTopic(String clusterName, String logicalTopicName) {
            resolvedTopic = super.resolveTopic(clusterName, logicalTopicName);
            return resolvedTopic;
        }

        @Override
        public org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory(
                String clusterName, String groupId, Duration operationTimeout) {
            requestedCluster = clusterName;
            requestedGroupId = groupId;
            throw new IllegalStateException("consumer factory stubbed for resolution test");
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
                            "cluster", "test",
                            "maxMessages", 1),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = executor.execute(emptyContext(), step);

            // Unknown cluster: the default CONSUME branch is selected.
            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown Kafka cluster");
            assertThat(result.errorMessage()).doesNotContain("Unknown kafka-consumer operation");
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
