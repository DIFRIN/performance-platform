package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskExecutor for Kafka producer operations: PRODUCE, PRELOAD.
 * <p>
 * Sends messages to a Kafka topic. Supports message templating ({@code {index}},
 * {@code {timestamp}}) and configurable batch sizes.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code operation} — {@code PRODUCE} or {@code PRELOAD} (default:
 *       PRODUCE)</li>
 *   <li>{@code topic} — Kafka topic name (required)</li>
 *   <li>{@code bootstrapServers} — broker addresses (required)</li>
 *   <li>{@code messageCount} — number of messages to produce (default: 1)</li>
 *   <li>{@code messageTemplate} — message body template with optional
 *       {@code {index}} and {@code {timestamp}} placeholders (default:
 *       {@code "perf-message-{index}"})</li>
 *   <li>{@code batchSize} — flush after this many messages (default: 100)</li>
 * </ul>
 * <p>
 * Outputs: {@code {messagesProduced: N}}.
 * I/O blocking operations run under Virtual Threads.
 */
@Preparation(name = "kafka-producer", version = "1.0.0",
        description = "Kafka produce or preload messages to a topic")
@Component
public class KafkaProducerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerTaskExecutor.class);
    private static final int DEFAULT_MESSAGE_COUNT = 1;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_TIMEOUT_MS = 120_000L;

    // Output keys (CRAFT-08: extracted from string literals)
    static final String OUTPUT_MESSAGES_PRODUCED = "messagesProduced";
    static final String OUTPUT_MESSAGES_FAILED = "messagesFailed";

    private final Map<String, KafkaProducer<String, String>> producersByExecution = new ConcurrentHashMap<>();

    public KafkaProducerTaskExecutor() {
        // No external dependencies — bootstrap servers come from step parameters
    }

    @Override
    public String getSupportedTaskName() {
        return "kafka-producer";
    }

    /**
     * Executes a kafka-producer operation (PRODUCE or PRELOAD).
     *
     * <p><b>CC-02</b>: method ~45 lines — parameter extraction, validation,
     * Virtual Thread dispatch, and exception handling form a single cohesive
     * control-flow unit that would lose clarity if split.</p>
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        ExecutionId executionId = context.executionId();
        String operation = Objects.toString(step.parameters().get("operation"), "PRODUCE").toUpperCase().trim();
        String topic = Objects.toString(step.parameters().get("topic"), null);
        String bootstrapServers = Objects.toString(step.parameters().get("bootstrapServers"), null);
        int messageCount = parseMessageCount(step);
        String messageTemplate = Objects.toString(step.parameters().get("messageTemplate"), "perf-message-{index}");
        int batchSize = parseBatchSize(step);
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
                        case "PRODUCE", "PRELOAD" ->
                                executeProduce(step, startNanos, bootstrapServers, topic, messageCount, messageTemplate, batchSize, timeoutMs, executionId);
                        default -> fail(step, startNanos, "Unknown kafka-producer operation: " + operation);
                    };
                } catch (Exception e) {
                    log.error("action=kafka_producer_error operation={} topic={} executionId={} stepId={}",
                            operation, topic, executionId.value(), step.id().value(), e);
                    return fail(step, startNanos, e.getMessage());
                }
            });
            return future.get(timeoutMs + 30_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("action=kafka_producer_timeout operation={} topic={} executionId={} stepId={} timeoutMs={}",
                    operation, topic, executionId.value(), step.id().value(), timeoutMs, e);
            return fail(step, startNanos, "Kafka producer operation timed out after " + timeoutMs + "ms");
        } catch (Exception e) {
            log.error("action=kafka_producer_unexpected_error operation={} topic={} executionId={} stepId={}",
                    operation, topic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * PRODUCE/PRELOAD: creates a producer, sends messages to the topic with
     * periodic flushes, and returns produced/failed counts.
     *
     * <p><b>CC-02</b>: method ~45 lines — producer lifecycle management
     * (create/send/flush/close) is a single cohesive I/O unit. The send loop
     * is delegated to {@link #sendMessages} to stay within the limit.</p>
     */
    private TaskResult executeProduce(StepDefinition step, long startNanos,
                                      String bootstrapServers, String topic,
                                      int messageCount, String messageTemplate,
                                      int batchSize, long timeoutMs,
                                      ExecutionId executionId) {
        KafkaProducer<String, String> producer = createProducer(bootstrapServers);
        String executionKey = step.id().value();
        producersByExecution.put(executionKey, producer);

        try {
            log.info("action=produce_start topic={} messageCount={} batchSize={} executionId={} stepId={}",
                    topic, messageCount, batchSize, executionId.value(), step.id().value());

            AtomicInteger produced = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            long deadline = System.currentTimeMillis() + timeoutMs;

            sendMessages(producer, topic, messageCount, messageTemplate, batchSize,
                    deadline, produced, failed, executionId, step.id());

            // Final flush to ensure all messages are sent
            producer.flush();

            java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_PRODUCED, produced.get(),
                    OUTPUT_MESSAGES_FAILED, failed.get()
            );

            if (failed.get() > 0) {
                log.warn("action=produce_done_with_errors topic={} produced={} failed={} executionId={} stepId={}",
                        topic, produced.get(), failed.get(), executionId.value(), step.id().value());
            } else {
                log.info("action=produce_done topic={} messagesProduced={} executionId={} stepId={}",
                        topic, produced.get(), executionId.value(), step.id().value());
            }

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=produce_failed topic={} executionId={} stepId={}", topic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "PRODUCE failed on topic '" + topic + "': " + e.getMessage());
        } finally {
            producersByExecution.remove(executionKey);
            closeProducer(producer);
        }
    }

    /**
     * Sends {@code messageCount} messages to the topic, flushing periodically
     * at each batch boundary. Tracks produced/failed via atomic counters.
     */
    private void sendMessages(KafkaProducer<String, String> producer, String topic,
                              int messageCount, String messageTemplate, int batchSize,
                              long deadline, AtomicInteger produced, AtomicInteger failed,
                              ExecutionId executionId, TaskId taskId) {
        for (int i = 1; i <= messageCount; i++) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("action=produce_timeout_partial topic={} produced={} of={} executionId={} stepId={}",
                        topic, produced.get(), messageCount, executionId.value(), taskId.value());
                break;
            }

            final int index = i;
            String message = resolveTemplate(messageTemplate, index);
            String key = "msg-" + index;
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    failed.incrementAndGet();
                    log.warn("action=produce_failed topic={} index={} partition={} executionId={} stepId={}",
                            topic, index, metadata != null ? metadata.partition() : -1,
                            executionId.value(), taskId.value(), exception);
                } else {
                    produced.incrementAndGet();
                }
            });

            // Flush periodically to avoid accumulating too many in-flight records
            if (index % batchSize == 0) {
                producer.flush();
                log.debug("action=produce_batch_flush topic={} batchEnd={} producedSoFar={} executionId={} stepId={}",
                        topic, index, produced.get(), executionId.value(), taskId.value());
            }
        }
    }

    /**
     * Resolves a message template. Supports two placeholders:
     * <ul>
     *   <li>{@code {index}} — replaced with the 1-based message index</li>
     *   <li>{@code {timestamp}} — replaced with {@code System.currentTimeMillis()}</li>
     * </ul>
     */
    static String resolveTemplate(String template, int index) {
        return template
                .replace("{index}", String.valueOf(index))
                .replace("{timestamp}", String.valueOf(System.currentTimeMillis()));
    }

    private KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new KafkaProducer<>(props);
    }

    private int parseMessageCount(StepDefinition step) {
        return parsePositiveInt(step.parameters().get("messageCount"), DEFAULT_MESSAGE_COUNT);
    }

    private int parseBatchSize(StepDefinition step) {
        int batch = parsePositiveInt(step.parameters().get("batchSize"), DEFAULT_BATCH_SIZE);
        return Math.max(1, batch);
    }

    private int parsePositiveInt(Object val, int defaultValue) {
        if (val instanceof Number num) {
            return Math.max(1, num.intValue());
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Math.max(1, Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private void closeProducer(KafkaProducer<String, String> producer) {
        try {
            producer.flush();
            producer.close(java.time.Duration.ofSeconds(10));
        } catch (Exception e) {
            log.warn("action=close_producer_failed", e);
        }
    }

    /**
     * Flushes and closes any active producer for the given execution
     * (called on scenario restart).
     * If {@code executionId} is null, flushes and closes all active producers.
     */
    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=cleanup_all activeProducers={}", producersByExecution.size());
            producersByExecution.values().forEach(this::closeProducer);
            producersByExecution.clear();
        } else {
            KafkaProducer<String, String> producer = producersByExecution.remove(executionId.value());
            if (producer != null) {
                log.info("action=cleanup_execution executionId={}", executionId.value());
                closeProducer(producer);
            }
        }
    }
}
