package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.plugin.template.DynamicValues;
import com.performance.platform.plugin.template.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskExecutor for Kafka producer operations: PRODUCE.
 * <p>
 * Sends messages to a Kafka topic. The record key, headers and payload are scenario-driven
 * templates resolved per message with the {@code DynamicValues} placeholders (see
 * {@link MessageTemplate}) and batch sizes are configurable. Uses Spring
 * {@link KafkaTemplate} through a named cluster reference.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code cluster} - named Kafka cluster in {@link KafkaClusterRegistry}
 *       (required)</li>
 *   <li>{@code topic} - logical topic name defined by the scenario; the physical name comes from
 *       the cluster configuration ({@code kafka-clusters.<cluster>.topics}) (required; resolved through
 *       {@code KafkaClusterRegistry.resolveTopic()})</li>
 *   <li>{@code operation} - {@code PRODUCE} (default)</li>
 *   <li>{@code messageCount} - number of messages to produce (default: 1)</li>
 *   <li>{@code payload} - message body: string or map/list
 *       (serialized as JSON), dynamic placeholders (default: {@code "perf-message-{index}"})</li>
 *   <li>{@code key} - record key template (default: {@code "msg-{index}"})</li>
 *   <li>{@code headers} - record headers, name -> value templates</li>
 * </ul>
 * Preparation only (fixed {@code messageCount}); rate/duration based load belongs to the
 * {@code gatling} step with {@code protocol: kafka}.
 * <ul>
 *   <li>{@code batchSize} - flush after this many messages (default: 100)</li>
 * </ul>
 * <p>
 * Outputs: {@code {messagesProduced: N, messagesFailed: N}}.
 * I/O blocking operations run under Virtual Threads.
 */
@Preparation(name = "kafka-producer", version = "2.0.0",
        description = "Kafka produce via named cluster reference")
@Component
public class KafkaProducerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerTaskExecutor.class);
    private static final int DEFAULT_MESSAGE_COUNT = 1;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_TIMEOUT_MS = 120_000L;
    private static final long SEND_TIMEOUT_SECONDS = 10L;

    // Output keys (CRAFT-08: extracted from string literals)
    static final String OUTPUT_MESSAGES_PRODUCED = "messagesProduced";
    static final String OUTPUT_MESSAGES_FAILED = "messagesFailed";

    private final KafkaClusterRegistry clusterRegistry;
    private final Map<String, KafkaTemplate<String, String>> templatesByExecution = new ConcurrentHashMap<>();

    public KafkaProducerTaskExecutor(KafkaClusterRegistry clusterRegistry) {
        this.clusterRegistry = Objects.requireNonNull(clusterRegistry, "clusterRegistry must not be null");
    }

    @Override
    public String getSupportedTaskName() {
        return "kafka-producer";
    }

    /**
     * Executes a kafka-producer operation (PRODUCE).
     *
     * <p><b>CC-02</b>: method ~55 lines - parameter extraction, validation,
     * Virtual Thread dispatch, and exception handling form a single cohesive
     * control-flow unit that would lose clarity if split.</p>
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        ExecutionId executionId = context.executionId();
        var operation = Objects.toString(step.parameters().get("operation"), "PRODUCE").toUpperCase().trim();
        var topic = Objects.toString(step.parameters().get("topic"), null);
        String clusterName = (String) step.parameters().get("cluster");
        int messageCount = parseMessageCount(step);
        int batchSize = parseBatchSize(step);
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : DEFAULT_TIMEOUT_MS;

        if (topic == null || topic.isBlank()) {
            return fail(step, startNanos, "Required parameter 'topic' is missing or blank");
        }
        if (clusterName == null || clusterName.isBlank()) {
            return fail(step, startNanos, "Required parameter 'cluster' is missing or blank");
        }

        MessageTemplate records;
        try {
            records = MessageTemplate.of(executionId.value(), step.id().value(), step.parameters());
        } catch (IllegalArgumentException e) {
            return fail(step, startNanos, e.getMessage());
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                try {
                    return switch (operation) {
                        case "PRODUCE" ->
                                executeProduce(step, startNanos, clusterName, topic, messageCount, records, batchSize, timeoutMs, executionId);
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
     * PRODUCE: resolves the KafkaTemplate from the named cluster, sends
     * messages to the topic with periodic flushes, and returns produced/failed
     * counts.
     *
     * <p><b>CC-02</b>: method ~45 lines - template lifecycle management
     * (create/send/flush/close) is a single cohesive I/O unit. The send loop
     * is delegated to {@link #sendMessages} to stay within the limit.</p>
     */
    private TaskResult executeProduce(StepDefinition step, long startNanos,
                                      String clusterName, String topic, int messageCount,
                                      MessageTemplate records, int batchSize, long timeoutMs,
                                      ExecutionId executionId) {
        String physicalTopic = clusterRegistry.resolveTopic(clusterName, topic);
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(
                clusterRegistry.producerFactory(clusterName, Duration.ofMillis(timeoutMs)));
        log.info("action=produce_via_cluster clusterName={} logicalTopic={} physicalTopic={} messageCount={} executionId={}",
                clusterName, topic, physicalTopic, messageCount, executionId.value());

        String executionKey = step.id().value();
        templatesByExecution.put(executionKey, kafkaTemplate);

        try {
            log.info("action=produce_start topic={} messageCount={} batchSize={} executionId={} stepId={}",
                    physicalTopic, messageCount, batchSize, executionId.value(), step.id().value());

            var produced = new AtomicInteger(0);
            var failed = new AtomicInteger(0);
            long deadline = System.currentTimeMillis() + timeoutMs;

            sendMessages(kafkaTemplate, physicalTopic, messageCount, records, batchSize,
                    deadline, produced, failed, executionId, step.id());

            // Final flush to ensure all messages are sent
            kafkaTemplate.flush();

            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_PRODUCED, produced.get(),
                    OUTPUT_MESSAGES_FAILED, failed.get()
            );

            if (failed.get() > 0) {
                log.warn("action=produce_done_with_errors topic={} produced={} failed={} executionId={} stepId={}",
                        physicalTopic, produced.get(), failed.get(), executionId.value(), step.id().value());
                return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                        "PRODUCE failed for " + failed.get() + " of " + messageCount
                                + " message(s) on topic '" + physicalTopic + "'", null);
            }
            log.info("action=produce_done topic={} messagesProduced={} executionId={} stepId={}",
                    physicalTopic, produced.get(), executionId.value(), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=produce_failed topic={} executionId={} stepId={}", physicalTopic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "PRODUCE failed on topic '" + physicalTopic + "': " + e.getMessage());
        } finally {
            templatesByExecution.remove(executionKey);
            closeTemplate(kafkaTemplate);
        }
    }

    /**
     * Sends {@code messageCount} messages to the topic, flushing periodically
     * at each batch boundary. Uses synchronous {@code KafkaTemplate.send().get()}
     * since the caller runs on a Virtual Thread. Tracks produced/failed via
     * atomic counters.
     */
    private void sendMessages(KafkaTemplate<String, String> kafkaTemplate, String topic,
                              int messageCount, MessageTemplate records, int batchSize,
                              long deadline, AtomicInteger produced, AtomicInteger failed,
                              ExecutionId executionId, TaskId taskId) {
        for (int i = 1; i <= messageCount; i++) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("action=produce_timeout_partial topic={} produced={} of={} executionId={} stepId={}",
                        topic, produced.get(), messageCount, executionId.value(), taskId.value());
                break;
            }

            final int index = i;
            try {
                var msg = records.render(index);
                kafkaTemplate.send(KafkaClusterPublisherFactory.record(topic, msg.key(), msg.value(), msg.headers()))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                produced.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
                log.warn("action=produce_failed topic={} index={} executionId={} stepId={}",
                        topic, index, executionId.value(), taskId.value(), e);
            }

            // Flush periodically to avoid accumulating too many in-flight records
            if (index % batchSize == 0) {
                kafkaTemplate.flush();
                log.debug("action=produce_batch_flush topic={} batchEnd={} producedSoFar={} executionId={} stepId={}",
                        topic, index, produced.get(), executionId.value(), taskId.value());
            }
        }
    }

    /** Resolves a template with the {@code DynamicValues} placeholders for the given 1-based index. */
    static String resolveTemplate(String template, int index) {
        return DynamicValues.resolve(template, new DynamicValues.Context("", "", () -> index));
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
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    /**
     * Closes the underlying {@link ProducerFactory} of the given template
     * to release connections.
     */
    private void closeTemplate(KafkaTemplate<String, String> kafkaTemplate) {
        try {
            kafkaTemplate.destroy();
        } catch (Exception e) {
            log.warn("action=close_template_failed", e);
        }
    }

    /**
     * Flushes and closes active templates for the given execution (called on
     * scenario restart). If {@code executionId} is null, flushes and closes all
     * active templates.
     */
    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=cleanup_all activeTemplates={}", templatesByExecution.size());
            templatesByExecution.values().forEach(this::closeTemplate);
            templatesByExecution.clear();
        } else {
            KafkaTemplate<String, String> template = templatesByExecution.remove(executionId.value());
            if (template != null) {
                log.info("action=cleanup_execution executionId={}", executionId.value());
                closeTemplate(template);
            }
        }
    }
}
