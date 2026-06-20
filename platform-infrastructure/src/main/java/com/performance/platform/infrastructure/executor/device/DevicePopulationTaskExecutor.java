package com.performance.platform.infrastructure.executor.device;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.infrastructure.executor.database.DatasourceProvider;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TaskExecutor for device population: PURGE and POPULATE operations.
 * <p>
 * PURGE deletes all rows from the device table. POPULATE generates a configurable
 * number of device IDs and inserts them in batches. Uses JDBC batching for
 * efficient insertion of large datasets (tested with up to 100k rows).
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code operation} — PURGE or POPULATE (required)</li>
 *   <li>{@code datasource} — logical datasource name (required)</li>
 *   <li>{@code table} — table name (required)</li>
 *   <li>{@code count} — number of devices to insert, POPULATE only (default: 100000)</li>
 *   <li>{@code batchSize} — insert batch size, POPULATE only (default: 5000)</li>
 *   <li>{@code deviceIdPrefix} — prefix for generated device IDs (default: "DEV-")</li>
 * </ul>
 * <p>
 * Outputs:
 * <ul>
 *   <li>PURGE — {@code {purgedRows: N, duration: "X.Xs"}}</li>
 *   <li>POPULATE — {@code {devicesInserted: N, duration: "X.Xs"}}</li>
 * </ul>
 * <p>
 * All I/O operations run under Virtual Threads.
 */
@Preparation(name = "device-population", version = "1.0.0",
        description = "Device table population: purge and generate device IDs")
@Component
public class DevicePopulationTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(DevicePopulationTaskExecutor.class);
    private static final int DEFAULT_COUNT = 100_000;
    private static final int DEFAULT_BATCH_SIZE = 5_000;
    private static final String DEFAULT_PREFIX = "DEV-";

    static final String OUTPUT_PURGED_ROWS = "purgedRows";
    static final String OUTPUT_DEVICES_INSERTED = "devicesInserted";
    static final String OUTPUT_DURATION = "duration";

    private final DatasourceProvider datasourceProvider;

    public DevicePopulationTaskExecutor(DatasourceProvider datasourceProvider) {
        this.datasourceProvider = Objects.requireNonNull(datasourceProvider,
                "datasourceProvider must not be null");
    }

    @Override
    public String getSupportedTaskName() {
        return "device-population";
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        String operation = Objects.toString(step.parameters().get("operation"), "")
                .toUpperCase().trim();
        String datasourceName = (String) step.parameters().get("datasource");
        String table = (String) step.parameters().get("table");
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : 300_000L;

        if (datasourceName == null || datasourceName.isBlank()) {
            return fail(step, startNanos, "Required parameter 'datasource' is missing or blank");
        }
        if (table == null || table.isBlank()) {
            return fail(step, startNanos, "Required parameter 'table' is missing or blank");
        }
        if (!table.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return fail(step, startNanos, "Invalid table name: " + table);
        }

        DataSource ds = datasourceProvider.get(datasourceName);
        if (ds == null) {
            return fail(step, startNanos, "No datasource registered for name: " + datasourceName);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                try {
                    return switch (operation) {
                        case "PURGE" -> executePurge(step, startNanos, ds, table);
                        case "POPULATE" -> executePopulate(step, startNanos, ds, table);
                        default -> fail(step, startNanos,
                                "Unknown operation: " + operation + " (expected PURGE or POPULATE)");
                    };
                } catch (Exception e) {
                    log.error("action=device_population_error operation={} table={} executionId={} stepId={}",
                            operation, table, context.executionId().value(), step.id().value(), e);
                    return fail(step, startNanos, e.getMessage(), e);
                }
            });
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("action=device_population_timeout operation={} table={} executionId={} stepId={} timeoutMs={}",
                    operation, table, context.executionId().value(), step.id().value(), timeoutMs);
            return fail(step, startNanos, "Operation timed out after " + timeoutMs + "ms", e);
        } catch (Exception e) {
            log.error("action=device_population_unexpected_error operation={} table={} executionId={} stepId={}",
                    operation, table, context.executionId().value(), step.id().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Execute PURGE: delete all rows from the device table.
     * Uses JdbcTemplate (ADR-013 Spring-first).
     */
    private TaskResult executePurge(StepDefinition step, long startNanos,
                                    DataSource ds, String table) {
        String sql = "DELETE FROM " + table;

        try {
            log.info("action=device_purge_start table={} stepId={}", table, step.id().value());

            JdbcTemplate jdbc = new JdbcTemplate(ds);
            int rowsAffected = jdbc.update(sql);

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_PURGED_ROWS, rowsAffected,
                    OUTPUT_DURATION, formatDuration(elapsed)
            );

            log.info("action=device_purge_done table={} purgedRows={} duration={} stepId={}",
                    table, rowsAffected, formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=device_purge_failed table={} stepId={}", table, step.id().value(), e);
            return fail(step, startNanos, "PURGE failed on table '" + table + "': " + e.getMessage(), e);
        }
    }

    /**
     * Execute POPULATE: generate device IDs and insert them in batches.
     * <p>
     * Uses JDBC batch inserts for performance. Device IDs are generated as
     * {@code {prefix}{zero-padded-index}} (e.g., "DEV-000001").
     * A temporary sequence-based insert is not used to avoid database-vendor
     * lock-in; the batch approach works identically on PostgreSQL, MySQL, etc.
     */
    private TaskResult executePopulate(StepDefinition step, long startNanos,
                                       DataSource ds, String table) {
        int count = parseParamInt(step, "count", DEFAULT_COUNT);
        int batchSize = Math.max(1, parseParamInt(step, "batchSize", DEFAULT_BATCH_SIZE));
        String prefix = Objects.toString(step.parameters().get("deviceIdPrefix"), DEFAULT_PREFIX);

        if (count <= 0) {
            return fail(step, startNanos, "Parameter 'count' must be positive, got: " + count);
        }
        if (count > 10_000_000) {
            return fail(step, startNanos, "Parameter 'count' exceeds maximum (10,000,000): " + count);
        }

        // Calculate padding width for zero-padded device IDs
        int digitCount = (int) Math.log10(count) + 1;

        String sql = "INSERT INTO " + table + " (device_id, status) VALUES (?, 'active')"
                + " ON CONFLICT (device_id) DO NOTHING";

        log.info("action=device_populate_start table={} count={} batchSize={} stepId={}",
                table, count, batchSize, step.id().value());

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            int inserted = 0;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 1; i <= count; i++) {
                    String deviceId = prefix + String.format("%0" + digitCount + "d", i);
                    stmt.setString(1, deviceId);
                    stmt.addBatch();

                    if (i % batchSize == 0) {
                        int[] results = stmt.executeBatch();
                        for (int r : results) {
                            if (r > 0 || r == PreparedStatement.SUCCESS_NO_INFO) {
                                inserted++;
                            }
                        }
                        conn.commit();
                        log.debug("action=device_populate_batch table={} batchEnd={} inserted={} stepId={}",
                                table, i, inserted, step.id().value());
                    }
                }

                // Final batch for remaining rows
                if (count % batchSize != 0) {
                    int[] results = stmt.executeBatch();
                    for (int r : results) {
                        if (r > 0 || r == PreparedStatement.SUCCESS_NO_INFO) {
                            inserted++;
                        }
                    }
                    conn.commit();
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_DEVICES_INSERTED, inserted,
                    OUTPUT_DURATION, formatDuration(elapsed)
            );

            log.info("action=device_populate_done table={} devicesInserted={} duration={} stepId={}",
                    table, inserted, formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (SQLException e) {
            log.error("action=device_populate_failed table={} stepId={}", table, step.id().value(), e);
            return fail(step, startNanos,
                    "POPULATE failed on table '" + table + "': " + e.getMessage(), e);
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private int parseParamInt(StepDefinition step, String key, int defaultValue) {
        Object val = step.parameters().get(key);
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message, Throwable cause) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }

    @Override
    public void cleanup(ExecutionId executionId) {
        // No persistent resources to clean up — connections are managed per-execute.
        log.debug("action=device_population_cleanup executionId={} (no-op)",
                executionId != null ? executionId.value() : "ALL");
    }
}
