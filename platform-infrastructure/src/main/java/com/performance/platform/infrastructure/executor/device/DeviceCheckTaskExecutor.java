package com.performance.platform.infrastructure.executor.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.infrastructure.executor.database.DatasourceProvider;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskExecutor for device-check processing (SUT workload).
 * <p>
 * Consumes messages from a Kafka topic, extracts a {@code device_id} from each
 * JSON message, checks whether the device exists in PostgreSQL, and — if found —
 * sends an HTTP PUT request to a WireMock mock with a JSON payload containing
 * the device information.
 * <p>
 * This executor simulates a real SUT (System Under Test) processing pipeline
 * and is designed to be performance-tested at scale (up to 100k+ messages).
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code topic} — Kafka topic to consume from (required)</li>
 *   <li>{@code bootstrapServers} — Kafka broker addresses (required)</li>
 *   <li>{@code groupId} — consumer group id (default: device-check-consumer)</li>
 *   <li>{@code maxMessages} — max messages to consume (default: 1000)</li>
 *   <li>{@code datasource} — logical datasource name for DB lookup (required)</li>
 *   <li>{@code table} — table name for device lookup (required)</li>
 *   <li>{@code deviceIdColumn} — column name for device_id (default: device_id)</li>
 *   <li>{@code mockUrl} — WireMock base URL for HTTP PUT (required)</li>
 *   <li>{@code mockEndpoint} — HTTP endpoint path (default: /api/devices)</li>
 *   <li>{@code pollBatchMs} — Kafka poll batch timeout in ms (default: 500)</li>
 * </ul>
 * <p>
 * Outputs: {@code {messagesConsumed, dbHits, httpCalls, messagesFailed, messagesSkipped}}.
 * <p>
 * All I/O (Kafka poll, JDBC, HTTP) runs under Virtual Threads.
 * <p>
 * <b>CC-02 justification:</b> This class coordinates three I/O systems
 * (Kafka consumer, JDBC DataSource, HTTP client) in a single pipeline.
 * Extracting any subsystem would scatter cohesive pipeline logic across
 * files without reducing total complexity.
 */
@Injection(name = "device-check", version = "1.0.0",
        description = "Consume Kafka messages, check device in DB, HTTP PUT to WireMock")
@Component
public class DeviceCheckTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(DeviceCheckTaskExecutor.class);
    private static final int DEFAULT_MAX_MESSAGES = 1000;
    private static final int DEFAULT_POLL_BATCH_MS = 500;
    private static final long DEFAULT_TIMEOUT_MS = 600_000L;

    static final String OUTPUT_MESSAGES_CONSUMED = "messagesConsumed";
    static final String OUTPUT_DB_HITS = "dbHits";
    static final String OUTPUT_HTTP_CALLS = "httpCalls";
    static final String OUTPUT_MESSAGES_FAILED = "messagesFailed";
    static final String OUTPUT_MESSAGES_SKIPPED = "messagesSkipped";

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final DatasourceProvider datasourceProvider;
    private final HttpClient httpClient;
    private final Map<String, KafkaConsumer<String, String>> consumersByExecution = new ConcurrentHashMap<>();

    public DeviceCheckTaskExecutor(DatasourceProvider datasourceProvider) {
        this.datasourceProvider = Objects.requireNonNull(datasourceProvider,
                "datasourceProvider must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getSupportedTaskName() {
        return "device-check";
    }

    /**
     * Executes the device-check pipeline: consume Kafka, check DB, HTTP PUT.
     *
     * <p><b>CC-02:</b> method ~42 lines — parameter extraction, validation,
     * Virtual Thread dispatch, and exception handling form a single cohesive
     * control-flow unit.</p>
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        ExecutionId executionId = context.executionId();
        String topic = Objects.toString(step.parameters().get("topic"), null);
        String bootstrapServers = Objects.toString(step.parameters().get("bootstrapServers"), null);
        String groupId = Objects.toString(step.parameters().get("groupId"), "device-check-consumer");
        int maxMessages = parseParamInt(step, "maxMessages", DEFAULT_MAX_MESSAGES);
        String datasourceName = (String) step.parameters().get("datasource");
        String table = (String) step.parameters().get("table");
        String deviceIdColumn = Objects.toString(step.parameters().get("deviceIdColumn"), "device_id");
        String mockUrl = Objects.toString(step.parameters().get("mockUrl"), null);
        String mockEndpoint = Objects.toString(step.parameters().get("mockEndpoint"), "/api/devices");
        int pollBatchMs = parseParamInt(step, "pollBatchMs", DEFAULT_POLL_BATCH_MS);
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : DEFAULT_TIMEOUT_MS;

        // Validation
        if (topic == null || topic.isBlank())
            return fail(step, startNanos, "Required parameter 'topic' is missing or blank");
        if (bootstrapServers == null || bootstrapServers.isBlank())
            return fail(step, startNanos, "Required parameter 'bootstrapServers' is missing or blank");
        if (datasourceName == null || datasourceName.isBlank())
            return fail(step, startNanos, "Required parameter 'datasource' is missing or blank");
        if (table == null || table.isBlank())
            return fail(step, startNanos, "Required parameter 'table' is missing or blank");
        if (mockUrl == null || mockUrl.isBlank())
            return fail(step, startNanos, "Required parameter 'mockUrl' is missing or blank");
        if (!table.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
            return fail(step, startNanos, "Invalid table name: " + table);
        if (!deviceIdColumn.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
            return fail(step, startNanos, "Invalid column name: " + deviceIdColumn);

        DataSource ds = datasourceProvider.get(datasourceName);
        if (ds == null)
            return fail(step, startNanos, "No datasource registered for name: " + datasourceName);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                try {
                    return executePipeline(step, startNanos, bootstrapServers, topic, groupId,
                            maxMessages, ds, table, deviceIdColumn, mockUrl, mockEndpoint,
                            pollBatchMs, timeoutMs, executionId);
                } catch (Exception e) {
                    log.error("action=device_check_error topic={} executionId={} stepId={}",
                            topic, executionId.value(), step.id().value(), e);
                    return fail(step, startNanos, e.getMessage(), e);
                }
            });
            return future.get(timeoutMs + 30_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("action=device_check_timeout topic={} executionId={} stepId={} timeoutMs={}",
                    topic, executionId.value(), step.id().value(), timeoutMs);
            return fail(step, startNanos, "Device check pipeline timed out after " + timeoutMs + "ms", e);
        } catch (Exception e) {
            log.error("action=device_check_unexpected_error topic={} executionId={} stepId={}",
                    topic, executionId.value(), step.id().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Core pipeline: consume Kafka messages, for each check device in DB,
     * and call HTTP PUT to WireMock if found.
     *
     * <p><b>CC-02:</b> method ~45 lines — Kafka consumer lifecycle, poll loop,
     * per-message dispatch to {@link #processRecord}, and stats collection
     * form a single cohesive I/O unit.</p>
     */
    private TaskResult executePipeline(StepDefinition step, long startNanos,
                                       String bootstrapServers, String topic, String groupId,
                                       int maxMessages, DataSource ds, String table,
                                       String deviceIdColumn, String mockUrl, String mockEndpoint,
                                       int pollBatchMs, long timeoutMs, ExecutionId executionId) {
        KafkaConsumer<String, String> consumer = createKafkaConsumer(bootstrapServers, groupId);
        String executionKey = step.id().value();
        consumersByExecution.put(executionKey, consumer);

        try {
            log.info("action=device_check_start topic={} maxMessages={} mockUrl={} executionId={} stepId={}",
                    topic, maxMessages, mockUrl, executionId.value(), step.id().value());

            consumer.subscribe(List.of(topic));

            var messagesConsumed = new AtomicInteger(0);
            var dbHits = new AtomicInteger(0);
            var httpCalls = new AtomicInteger(0);
            var messagesFailed = new AtomicInteger(0);
            var messagesSkipped = new AtomicInteger(0);

            long deadline = System.currentTimeMillis() + timeoutMs;
            long pollTimeout = Math.max(100, pollBatchMs);

            while (messagesConsumed.get() < maxMessages && System.currentTimeMillis() < deadline) {
                long remaining = Math.max(100, deadline - System.currentTimeMillis());
                long actualPollTimeout = Math.min(pollTimeout, remaining);

                ConsumerRecords<String, String> records = consumer.poll(
                        java.time.Duration.ofMillis(actualPollTimeout));

                if (records.isEmpty()) {
                    // No more messages available — topic may be fully consumed
                    if (messagesConsumed.get() < maxMessages) {
                        log.debug("action=device_check_poll_empty topic={} consumedSoFar={} executionId={}",
                                topic, messagesConsumed.get(), executionId.value());
                    }
                }

                for (ConsumerRecord<String, String> record : records) {
                    if (messagesConsumed.get() >= maxMessages) break;
                    if (System.currentTimeMillis() > deadline) break;

                    try {
                        processRecord(record, ds, table, deviceIdColumn, mockUrl, mockEndpoint,
                                dbHits, httpCalls, executionId);
                        messagesConsumed.incrementAndGet();
                    } catch (Exception e) {
                        messagesFailed.incrementAndGet();
                        log.warn("action=device_check_record_failed topic={} offset={} key={} executionId={}",
                                topic, record.offset(), record.key(), executionId.value(), e);
                    }
                }
            }

            // Commit offsets after processing all messages
            try {
                consumer.commitSync();
            } catch (Exception e) {
                log.warn("action=device_check_commit_failed topic={} executionId={}",
                        topic, executionId.value(), e);
            }

            java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_MESSAGES_CONSUMED, messagesConsumed.get(),
                    OUTPUT_DB_HITS, dbHits.get(),
                    OUTPUT_HTTP_CALLS, httpCalls.get(),
                    OUTPUT_MESSAGES_FAILED, messagesFailed.get(),
                    OUTPUT_MESSAGES_SKIPPED, messagesSkipped.get()
            );

            log.info("action=device_check_done topic={} messagesConsumed={} dbHits={} httpCalls={} "
                     + "failed={} skipped={} executionId={} stepId={}",
                    topic, messagesConsumed.get(), dbHits.get(), httpCalls.get(),
                    messagesFailed.get(), messagesSkipped.get(),
                    executionId.value(), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } finally {
            consumersByExecution.remove(executionKey);
            closeConsumer(consumer);
        }
    }

    /**
     * Process a single Kafka record: extract device_id, check DB, send HTTP PUT.
     * <p>
     * The message value is expected to be a JSON object with a {@code device_id} field.
     * The DB check uses a prepared statement for safety. The HTTP PUT sends a JSON
     * payload with device metadata.
     */
    @SuppressWarnings("unchecked")
    private void processRecord(ConsumerRecord<String, String> record,
                               DataSource ds, String table, String deviceIdColumn,
                               String mockUrl, String mockEndpoint,
                               AtomicInteger dbHits, AtomicInteger httpCalls,
                               ExecutionId executionId) {
        // 1. Extract device_id from Kafka message JSON
        String deviceId;
        try {
            Map<String, Object> msgMap = jsonMapper.readValue(record.value(), Map.class);
            deviceId = Objects.toString(msgMap.get("device_id"), null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse message JSON: " + e.getMessage(), e);
        }

        if (deviceId == null || deviceId.isBlank()) {
            throw new RuntimeException("Message missing 'device_id' field, key=" + record.key());
        }

        // 2. Check device existence in database
        boolean exists = checkDeviceExists(ds, table, deviceIdColumn, deviceId);
        if (exists) {
            dbHits.incrementAndGet();
        }

        // 3. HTTP PUT to WireMock (always called when device exists)
        if (exists) {
            sendHttpPut(mockUrl, mockEndpoint, deviceId, record);
            httpCalls.incrementAndGet();
        }

        log.debug("action=device_check_record_processed deviceId={} dbHit={} key={} executionId={}",
                deviceId, exists, record.key(), executionId.value());
    }

    /**
     * Check if a device exists in the database by its device_id.
     */
    private boolean checkDeviceExists(DataSource ds, String table,
                                      String deviceIdColumn, String deviceId) {
        String sql = "SELECT 1 FROM " + table + " WHERE " + deviceIdColumn + " = ? LIMIT 1";
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, deviceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("DB check failed for device_id=" + deviceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Send HTTP PUT to WireMock with a JSON payload containing device metadata.
     */
    private void sendHttpPut(String mockUrl, String mockEndpoint, String deviceId,
                             ConsumerRecord<String, String> record) {
        String url = mockUrl.replaceAll("/+$", "") + mockEndpoint + "/" + deviceId;

        // Build JSON payload
        String payload;
        try {
            Map<String, Object> payloadMap = Map.of(
                    "device_id", deviceId,
                    "status", "processed",
                    "source_topic", record.topic(),
                    "source_offset", record.offset(),
                    "processed_at", System.currentTimeMillis()
            );
            payload = jsonMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload for device_id=" + deviceId, e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP PUT returned status " + response.statusCode()
                        + " for " + url);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("HTTP PUT failed for " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP PUT interrupted for " + url, e);
        }
    }

    // ─── Kafka helpers ──────────────────────────────────────────────────────

    private KafkaConsumer<String, String> createKafkaConsumer(String bootstrapServers,
                                                              String groupId) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new KafkaConsumer<>(props);
    }

    private void closeConsumer(KafkaConsumer<String, String> consumer) {
        try {
            consumer.close(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("action=device_check_close_consumer_failed", e);
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private int parseParamInt(StepDefinition step, String key, int defaultValue) {
        Object val = step.parameters().get(key);
        if (val instanceof Number num) return num.intValue();
        if (val instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return defaultValue;
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message, Throwable cause) {
        java.time.Duration elapsed = java.time.Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    // ─── cleanup ───────────────────────────────────────────────────────────

    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=device_check_cleanup_all activeConsumers={}", consumersByExecution.size());
            consumersByExecution.values().forEach(consumer -> {
                consumer.wakeup();
                closeConsumer(consumer);
            });
            consumersByExecution.clear();
        } else {
            KafkaConsumer<String, String> consumer = consumersByExecution.remove(executionId.value());
            if (consumer != null) {
                log.info("action=device_check_cleanup executionId={}", executionId.value());
                consumer.wakeup();
                closeConsumer(consumer);
            }
        }
    }
}
