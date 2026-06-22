package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TaskExecutor for Kafka consumer operations: CONSUME, COUNT.
 * <p>
 * Polls Kafka topics and counts available messages via Spring
 * {@link ConsumerFactory} and named cluster references (v2.0) or legacy
 * inline bootstrap servers. Each operation creates a short-lived
 * {@link Consumer} and closes it after the call. A reference is kept per
 * execution so that {@link #cleanup(ExecutionId)} can interrupt a stuck
 * consumer (e.g. during a scenario restart).
 * <p>
 * Parameters (v2.0 - cluster reference preferred):
 * <ul>
 *   <li>{@code cluster} - named Kafka cluster in {@link KafkaClusterRegistry}
 *       (v2.0, preferred)</li>
 *   <li>{@code bootstrapServers} - broker addresses (legacy, log WARN)</li>
 *   <li>{@code topic} - Kafka topic name (required; resolved through
 *       {@code KafkaClusterRegistry.resolveTopic()} when {@code cluster}
 *       is present)</li>
 *   <li>{@code operation} - {@code CONSUME} (poll up to maxMessages) or
 *       {@code COUNT} (count available messages via offsets)</li>
 *   <li>{@code groupId} - consumer group id (overrides {@code consumerGroup}
 *       from cluster when present)</li>
 *   <li>{@code maxMessages} - (optional, default 100) max messages to consume</li>
 *   <li>{@code timeout} - (optional, default 30s) poll timeout</li>
 * </ul>
 * <p>
 * Outputs: {@code {messagesConsumed: N, lag: M}}.
 * I/O blocking operations run under Virtual Threads.
 *
 * <p><b>CC-02 justification</b> - Class exceeds 300 lines due to the inherent
 * complexity of coordinating a stateful Kafka consumer lifecycle (create, poll,
 * commit, close, cleanup) within the Virtual Thread model, with hybrid
 * cluster-vs-legacy resolution. Splitting the consumer management, offset
 * computation, and cleanup into separate classes would scatter cohesive I/O
 * coordination logic across multiple files without reducing total complexity.</p>
 */
@Preparation(name = "kafka-consumer", version = "2.0.0",
        description = "Kafka consumer via named cluster reference or inline bootstrap servers")
@Component
public class KafkaConsumerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerTaskExecutor.class);
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    // Output keys (CRAFT-08: extracted from string literals)
    static final String OUTPUT_MESSAGES_CONSUMED = "messagesConsumed";
    static final String OUTPUT_LAG = "lag";

    private final KafkaClusterRegistry clusterRegistry;
    private final Map<String, Consumer<String, String>> consumersByExecution = new ConcurrentHashMap<>();

    public KafkaConsumerTaskExecutor(KafkaClusterRegistry clusterRegistry) {
        this.clusterRegistry = Objects.requireNonNull(clusterRegistry, "clusterRegistry must not be null");
    }

    @Override
    public String getSupportedTaskName() {
        return "kafka-consumer";
    }

    /**
     * Executes a kafka-consumer operation (CONSUME or COUNT).
     *
     * <p><b>CC-02</b>: method ~49 lines - parameter extraction, cluster-vs-legacy
     * resolution, validation, Virtual Thread dispatch, and exception handling form
     * a single cohesive control-flow unit that would lose clarity if split.</p>
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        ExecutionId executionId = context.executionId();
        var operation = Objects.toString(step.parameters().get("operation"), "CONSUME").toUpperCase().trim();
        var topic = Objects.toString(step.parameters().get("topic"), null);
        String clusterName = (String) step.parameters().get("cluster");
        String bootstrapServers = (String) step.parameters().get("bootstrapServers");
        String groupId = (String) step.parameters().get("groupId");
        int maxMessages = parseMaxMessages(step);
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : DEFAULT_TIMEOUT_MS;

        if (topic == null || topic.isBlank()) {
            return fail(step, startNanos, "Required parameter 'topic' is missing or blank");
        }
        if (clusterName == null && (bootstrapServers == null || bootstrapServers.isBlank())) {
            return fail(step, startNanos, "Either 'cluster' or 'bootstrapServers' parameter is required");
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                try {
                    return switch (operation) {
                        case "CONSUME" -> executeConsume(step, startNanos, clusterName, bootstrapServers,
                                topic, groupId, maxMessages, timeoutMs, executionId);
                        case "COUNT" -> executeCount(step, startNanos, clusterName, bootstrapServers,
                                topic, timeoutMs, executionId);
                        default -> fail(step, startNanos, "Unknown kafka-consumer operation: " + operation);
                    };
                } catch (Exception e) {
                    log.error("action=kafka_consumer_error operation={} topic={} executionId={} stepId={}",
                            operation, topic, executionId.value(), step.id().value(), e);
                    return fail(step, startNanos, e.getMessage());
                }
            });
            return future.get(timeoutMs + 10_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("action=kafka_consumer_timeout operation={} topic={} executionId={} stepId={} timeoutMs={}",
                    operation, topic, executionId.value(), step.id().value(), timeoutMs, e);
            return fail(step, startNanos, "Kafka consumer operation timed out after " + timeoutMs + "ms");
        } catch (Exception e) {
            log.error("action=kafka_consumer_unexpected_error operation={} topic={} executionId={} stepId={}",
                    operation, topic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * CONSUME: subscribes to the topic and polls for messages.
     * <p>
     * <b>Polling behaviour</b>: the {@code toTake} cap inside
     * {@link #pollMessages} is defense-in-depth that ensures the reported count
     * never exceeds {@code maxMessages} regardless of the broker-configured
     * {@code max.poll.records}.
     *
     * <p><b>CC-02</b>: method ~58 lines - cluster-vs-legacy resolution, consumer
     * lifecycle (create/poll/commit/close), lag computation, and output assembly
     * form a single logical unit.</p>
     */
    private TaskResult executeConsume(StepDefinition step, long startNanos,
                                      String clusterName, String bootstrapServers,
                                      String topic, String groupId,
                                      int maxMessages, long timeoutMs,
                                      ExecutionId executionId) {
        // Resolve topic and create consumer via factory
        String physicalTopic;
        Consumer<String, String> consumer;

        if (clusterName != null) {
            physicalTopic = clusterRegistry.resolveTopic(clusterName, topic);
            String effectiveGroupId = resolveGroupId(groupId, clusterName);
            ConsumerFactory<String, String> factory = clusterRegistry.consumerFactory(clusterName, effectiveGroupId);
            consumer = factory.createConsumer();
            log.info("action=consume_via_cluster clusterName={} logicalTopic={} physicalTopic={} groupId={} executionId={}",
                    clusterName, topic, physicalTopic, effectiveGroupId, executionId.value());
        } else {
            physicalTopic = topic;
            log.warn("action=consume_via_legacy_bootstrap bootstrapServers={} topic={} executionId={} "
                    + "consider migrating to named cluster reference",
                    bootstrapServers, topic, executionId.value());
            String effectiveGroupId = groupId != null && !groupId.isBlank() ? groupId : "perf-consumer-group";
            consumer = createEphemeralConsumerFactory(bootstrapServers, effectiveGroupId).createConsumer();
        }

        String executionKey = step.id().value();
        consumersByExecution.put(executionKey, consumer);

        try {
            log.info("action=consume_start topic={} maxMessages={} executionId={} stepId={}",
                    physicalTopic, maxMessages, executionId.value(), step.id().value());

            consumer.subscribe(List.of(physicalTopic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            int consumed = pollMessages(consumer, maxMessages, deadline, physicalTopic, executionId, step.id());

            consumer.commitSync();

            long lag = computeLag(consumer, physicalTopic, executionId);
            java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_CONSUMED, consumed,
                    OUTPUT_LAG, lag
            );

            log.info("action=consume_done topic={} messagesConsumed={} lag={} executionId={} stepId={}",
                    physicalTopic, consumed, lag, executionId.value(), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=consume_failed topic={} executionId={} stepId={}",
                    physicalTopic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "CONSUME failed on topic '" + physicalTopic + "': " + e.getMessage());
        } finally {
            consumersByExecution.remove(executionKey);
            closeConsumer(consumer);
        }
    }

    /**
     * Polls the consumer in a loop until {@code maxMessages} is reached or the
     * deadline expires. Returns the number of messages actually consumed.
     * <p>
     * The {@code toTake} cap is defense-in-depth: the broker may return a batch
     * larger than {@code maxMessages} depending on {@code max.poll.records}.
     */
    private int pollMessages(Consumer<String, String> consumer, int maxMessages,
                             long deadline, String topic, ExecutionId executionId, TaskId taskId) {
        int consumed = 0;
        while (consumed < maxMessages && System.currentTimeMillis() < deadline) {
            long remaining = Math.max(100, deadline - System.currentTimeMillis());
            var records = consumer.poll(java.time.Duration.ofMillis(Math.min(remaining, 5000)));

            int received = records.count();
            if (received > 0) {
                log.debug("action=consume_poll topic={} batchSize={} consumedSoFar={} executionId={} stepId={}",
                        topic, received, consumed, executionId.value(), taskId.value());
            }
            int toTake = Math.min(received, maxMessages - consumed);
            consumed += toTake;

            if (consumed >= maxMessages) {
                break;
            }
        }
        return consumed;
    }

    /**
     * COUNT: counts total available messages in the topic using beginning/end
     * offsets without consuming any messages.
     *
     * <p><b>CC-02</b>: method ~60 lines - cluster-vs-legacy resolution, consumer
     * lifecycle (create/assign/offsets/close), and output assembly form a single
     * logical unit.</p>
     */
    private TaskResult executeCount(StepDefinition step, long startNanos,
                                    String clusterName, String bootstrapServers,
                                    String topic,
                                    long timeoutMs, ExecutionId executionId) {
        // Resolve topic and create consumer via factory
        String physicalTopic;
        Consumer<String, String> consumer;

        if (clusterName != null) {
            physicalTopic = clusterRegistry.resolveTopic(clusterName, topic);
            // COUNT uses a unique ephemeral group to avoid affecting real consumer group offsets
            String countGroupId = "perf-count-" + System.currentTimeMillis();
            ConsumerFactory<String, String> factory = clusterRegistry.consumerFactory(clusterName, countGroupId);
            consumer = factory.createConsumer();
            log.info("action=count_via_cluster clusterName={} logicalTopic={} physicalTopic={} executionId={}",
                    clusterName, topic, physicalTopic, executionId.value());
        } else {
            physicalTopic = topic;
            log.warn("action=count_via_legacy_bootstrap bootstrapServers={} topic={} executionId={} "
                    + "consider migrating to named cluster reference",
                    bootstrapServers, topic, executionId.value());
            String countGroupId = "perf-count-" + System.currentTimeMillis();
            consumer = createEphemeralConsumerFactory(bootstrapServers, countGroupId).createConsumer();
        }

        try {
            log.info("action=count_start topic={} executionId={} stepId={}",
                    physicalTopic, executionId.value(), step.id().value());

            List<PartitionInfo> partitionInfos = consumer.partitionsFor(physicalTopic);
            if (partitionInfos == null || partitionInfos.isEmpty()) {
                return fail(step, startNanos, "No partitions found for topic: " + physicalTopic);
            }

            List<TopicPartition> partitions = partitionInfos.stream()
                    .map(pi -> new TopicPartition(physicalTopic, pi.partition()))
                    .toList();
            consumer.assign(partitions);

            long totalMessages = sumPartitionOffsets(consumer, partitions);

            java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_CONSUMED, totalMessages,
                    OUTPUT_LAG, 0
            );

            log.info("action=count_done topic={} totalMessages={} partitions={} executionId={} stepId={}",
                    physicalTopic, totalMessages, partitions.size(), executionId.value(), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=count_failed topic={} executionId={} stepId={}",
                    physicalTopic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "COUNT failed on topic '" + physicalTopic + "': " + e.getMessage());
        } finally {
            closeConsumer(consumer);
        }
    }

    /**
     * Resolves the effective consumer group ID:
     * <ol>
     *   <li>Explicit {@code groupId} from step params (highest priority)</li>
     *   <li>{@code consumerGroup} from the named cluster properties</li>
     *   <li>Fallback {@code "perf-consumer-group"}</li>
     * </ol>
     */
    private String resolveGroupId(String explicitGroupId, String clusterName) {
        if (explicitGroupId != null && !explicitGroupId.isBlank()) {
            return explicitGroupId;
        }
        KafkaClusterProperties cluster = clusterRegistry.get(clusterName);
        if (cluster != null && cluster.consumerGroup() != null && !cluster.consumerGroup().isBlank()) {
            return cluster.consumerGroup();
        }
        return "perf-consumer-group";
    }

    /**
     * Sums the available message count across all partitions using
     * {@code endOffsets - beginningOffsets}.
     */
    private long sumPartitionOffsets(Consumer<String, String> consumer,
                                     List<TopicPartition> partitions) {
        Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

        long totalMessages = 0;
        for (TopicPartition tp : partitions) {
            long begin = beginningOffsets.getOrDefault(tp, 0L);
            long end = endOffsets.getOrDefault(tp, 0L);
            totalMessages += Math.max(0, end - begin);
        }
        return totalMessages;
    }

    /**
     * Computes consumer group lag: sum of (endOffset - committedOffset) across
     * all partitions assigned to this consumer.
     */
    private long computeLag(Consumer<String, String> consumer, String topic, ExecutionId executionId) {
        try {
            var assignment = consumer.assignment();
            if (assignment.isEmpty()) {
                return 0;
            }
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(assignment);
            long lag = 0;
            for (TopicPartition tp : assignment) {
                OffsetAndMetadata committed = consumer.committed(Set.of(tp)).get(tp);
                long committedOffset = committed != null ? committed.offset() : 0L;
                long endOffset = endOffsets.getOrDefault(tp, 0L);
                lag += Math.max(0, endOffset - committedOffset);
            }
            return lag;
        } catch (Exception e) {
            log.warn("action=compute_lag_failed topic={} executionId={}", topic, executionId.value(), e);
            return -1;
        }
    }

    /**
     * Creates an ephemeral {@link ConsumerFactory} from inline bootstrap servers
     * (legacy path). Logs a WARN to encourage migration to named clusters.
     */
    private ConsumerFactory<String, String> createEphemeralConsumerFactory(String bootstrapServers, String groupId) {
        var config = new HashMap<String, Object>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    private int parseMaxMessages(StepDefinition step) {
        Object val = step.parameters().get("maxMessages");
        if (val instanceof Number num) {
            return Math.max(1, num.intValue());
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Math.max(1, Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_MESSAGES;
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private void closeConsumer(Consumer<String, String> consumer) {
        try {
            consumer.close(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("action=close_consumer_failed", e);
        }
    }

    /**
     * Interrupts any active consumer for the given execution (called on scenario restart).
     * If {@code executionId} is null, interrupts all active consumers.
     */
    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=cleanup_all activeConsumers={}", consumersByExecution.size());
            consumersByExecution.values().forEach(consumer -> {
                consumer.wakeup();
                closeConsumer(consumer);
            });
            consumersByExecution.clear();
        } else {
            Consumer<String, String> consumer = consumersByExecution.remove(executionId.value());
            if (consumer != null) {
                log.info("action=cleanup_execution executionId={}", executionId.value());
                consumer.wakeup();
                closeConsumer(consumer);
            }
        }
    }
}
