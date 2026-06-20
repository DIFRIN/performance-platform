package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TaskExecutor for Kafka consumer operations: CONSUME, COUNT.
 * <p>
 * Polls Kafka topics and counts available messages. Each operation creates a
 * short-lived {@link KafkaConsumer} and closes it after the call. A reference
 * is kept per execution so that {@link #cleanup(ExecutionId)} can interrupt a
 * stuck consumer (e.g. during a scenario restart).
 * <p>
 * Parameters required:
 * <ul>
 *   <li>{@code operation} — {@code CONSUME} (poll up to maxMessages) or
 *       {@code COUNT} (count available messages via offsets)</li>
 *   <li>{@code topic} — Kafka topic name</li>
 *   <li>{@code bootstrapServers} — broker addresses (e.g.
 *       {@code "localhost:9092"})</li>
 *   <li>{@code groupId} — consumer group id (used only for CONSUME)</li>
 *   <li>{@code maxMessages} — (optional, default 100) max messages to consume</li>
 *   <li>{@code timeout} — (optional, default 30s) poll timeout</li>
 * </ul>
 * <p>
 * Outputs: {@code {messagesConsumed: N, lag: M}}.
 * I/O blocking operations run under Virtual Threads.
 *
 * <p><b>CC-02 justification</b> — Class exceeds 300 lines due to the inherent
 * complexity of coordinating a stateful Kafka consumer lifecycle (create, poll,
 * commit, close, cleanup) within the Virtual Thread model. Splitting the
 * consumer management, offset computation, and cleanup into separate classes
 * would scatter cohesive I/O coordination logic across multiple files without
 * reducing total complexity.</p>
 */
@Preparation(name = "kafka-consumer", version = "1.0.0",
        description = "Kafka consume or count messages in a topic")
@Component
public class KafkaConsumerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerTaskExecutor.class);
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    // Output keys (CRAFT-08: extracted from string literals)
    static final String OUTPUT_MESSAGES_CONSUMED = "messagesConsumed";
    static final String OUTPUT_LAG = "lag";

    private final Map<String, KafkaConsumer<String, String>> consumersByExecution = new ConcurrentHashMap<>();

    public KafkaConsumerTaskExecutor() {
        // No external dependencies — bootstrap servers come from step parameters
    }

    @Override
    public String getSupportedTaskName() {
        return "kafka-consumer";
    }

    /**
     * Executes a kafka-consumer operation (CONSUME or COUNT).
     *
     * <p><b>CC-02</b>: method ~44 lines — parameter extraction, validation,
     * Virtual Thread dispatch, and exception handling form a single cohesive
     * control-flow unit that would lose clarity if split.</p>
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        ExecutionId executionId = context.executionId();
        String operation = Objects.toString(step.parameters().get("operation"), "CONSUME").toUpperCase().trim();
        String topic = Objects.toString(step.parameters().get("topic"), null);
        String bootstrapServers = Objects.toString(step.parameters().get("bootstrapServers"), null);
        String groupId = Objects.toString(step.parameters().get("groupId"), "perf-consumer-group");
        int maxMessages = parseMaxMessages(step);
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : DEFAULT_TIMEOUT_MS;

        if (topic == null || topic.isBlank()) {
            return fail(step, startNanos, "Required parameter 'topic' is missing or blank");
        }
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return fail(step, startNanos, "Required parameter 'bootstrapServers' is missing or blank");
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                try {
                    return switch (operation) {
                        case "CONSUME" -> executeConsume(step, startNanos, bootstrapServers, topic, groupId, maxMessages, timeoutMs, executionId);
                        case "COUNT" -> executeCount(step, startNanos, bootstrapServers, topic, timeoutMs, executionId);
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
     * <b>Polling behaviour</b>: the consumer is created with
     * {@code max.poll.records = min(maxMessages, 100)}, so the broker-side
     * batch is pre-limited to the desired ceiling. The {@code toTake} cap
     * inside {@link #pollMessages} is a defense-in-depth measure that ensures
     * the reported count never exceeds {@code maxMessages} even if the broker
     * returns a larger-than-configured batch (e.g. due to compaction).
     *
     * <p><b>CC-02</b>: method ~40 lines — setup, polling delegation, offset commit,
     * lag computation, and output assembly form a single logical unit.</p>
     */
    private TaskResult executeConsume(StepDefinition step, long startNanos,
                                      String bootstrapServers, String topic,
                                      String groupId, int maxMessages, long timeoutMs,
                                      ExecutionId executionId) {
        int pollBatchSize = Math.min(maxMessages, 100);
        KafkaConsumer<String, String> consumer = createConsumer(bootstrapServers, groupId, pollBatchSize);
        String executionKey = step.id().value();
        consumersByExecution.put(executionKey, consumer);

        try {
            log.info("action=consume_start topic={} groupId={} maxMessages={} executionId={} stepId={}",
                    topic, groupId, maxMessages, executionId.value(), step.id().value());

            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            int consumed = pollMessages(consumer, maxMessages, deadline, topic, executionId, step.id());

            consumer.commitSync();

            long lag = computeLag(consumer, topic, executionId);
            java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_CONSUMED, consumed,
                    OUTPUT_LAG, lag
            );

            log.info("action=consume_done topic={} messagesConsumed={} lag={} executionId={} stepId={}",
                    topic, consumed, lag, executionId.value(), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=consume_failed topic={} executionId={} stepId={}", topic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "CONSUME failed on topic '" + topic + "': " + e.getMessage());
        } finally {
            consumersByExecution.remove(executionKey);
            closeConsumer(consumer);
        }
    }

    /**
     * Polls the consumer in a loop until {@code maxMessages} is reached or the
     * deadline expires. Returns the number of messages actually consumed.
     * <p>
     * The {@code toTake} cap is defense-in-depth: the consumer was created with
     * {@code max.poll.records} already bounded, but compaction or broker
     * behaviour could still return a larger batch.
     */
    private int pollMessages(KafkaConsumer<String, String> consumer, int maxMessages,
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
     */
    private TaskResult executeCount(StepDefinition step, long startNanos,
                                    String bootstrapServers, String topic, long timeoutMs,
                                    ExecutionId executionId) {
        KafkaConsumer<String, String> consumer = createConsumer(bootstrapServers,
                "perf-count-" + System.currentTimeMillis(), 1);

        try {
            log.info("action=count_start topic={} executionId={} stepId={}", topic, executionId.value(), step.id().value());

            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
            if (partitionInfos == null || partitionInfos.isEmpty()) {
                return fail(step, startNanos, "No partitions found for topic: " + topic);
            }

            List<TopicPartition> partitions = partitionInfos.stream()
                    .map(pi -> new TopicPartition(topic, pi.partition()))
                    .toList();
            consumer.assign(partitions);

            long totalMessages = sumPartitionOffsets(consumer, partitions);

            java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_CONSUMED, totalMessages,
                    OUTPUT_LAG, 0
            );

            log.info("action=count_done topic={} totalMessages={} partitions={} executionId={} stepId={}",
                    topic, totalMessages, partitions.size(), executionId.value(), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=count_failed topic={} executionId={} stepId={}", topic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "COUNT failed on topic '" + topic + "': " + e.getMessage());
        } finally {
            closeConsumer(consumer);
        }
    }

    /**
     * Sums the available message count across all partitions using
     * {@code endOffsets - beginningOffsets}.
     */
    private long sumPartitionOffsets(KafkaConsumer<String, String> consumer,
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
    private long computeLag(KafkaConsumer<String, String> consumer, String topic, ExecutionId executionId) {
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

    private KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId,
                                                         int maxPollRecords) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        return new KafkaConsumer<>(props);
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

    private void closeConsumer(KafkaConsumer<String, String> consumer) {
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
            KafkaConsumer<String, String> consumer = consumersByExecution.remove(executionId.value());
            if (consumer != null) {
                log.info("action=cleanup_execution executionId={}", executionId.value());
                consumer.wakeup();
                closeConsumer(consumer);
            }
        }
    }
}
