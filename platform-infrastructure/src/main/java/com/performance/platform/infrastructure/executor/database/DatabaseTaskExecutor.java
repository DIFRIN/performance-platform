package com.performance.platform.infrastructure.executor.database;

import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TaskExecutor pour opérations base de données : PURGE, POPULATE, MIGRATION, BACKUP, RESTORE.
 * <p>
 * Référence une datasource par son nom logique via {@link DatasourceProvider}.
 * Toute opération I/O bloquante s'exécute sous Virtual Threads.
 * <p>
 * Paramètres de step :
 * <ul>
 *   <li>{@code operation} — obligatoire : PURGE, POPULATE</li>
 *   <li>{@code datasource} — obligatoire : nom logique de la datasource</li>
 *   <li>{@code table} — obligatoire pour PURGE : nom de la table à vider</li>
 *   <li>{@code scriptPath} — obligatoire pour POPULATE : chemin du script SQL</li>
 * </ul>
 * <p>
 * Outputs : {@code {rowsAffected: N, duration: "X.Xs"}}.
 * <p>
 * Implémente {@link StatefulResourceCleaner} pour libérer les connexions lors d'un restart.
 */
@Preparation(name = "database", version = "1.0.0", description = "DB operations: purge, populate, migrate, backup, restore")
@Component
public class DatabaseTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTaskExecutor.class);

    private final DatasourceProvider datasourceProvider;
    private final Map<String, Connection> connectionsByExecution = new ConcurrentHashMap<>();

    public DatabaseTaskExecutor(DatasourceProvider datasourceProvider) {
        this.datasourceProvider = Objects.requireNonNull(datasourceProvider, "datasourceProvider must not be null");
    }

    @Override
    public String getSupportedTaskName() {
        return "database";
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        var operation = Objects.toString(step.parameters().get("operation"), "").toUpperCase().trim();
        String datasourceName = (String) step.parameters().get("datasource");
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : 30_000L;

        if (datasourceName == null || datasourceName.isBlank()) {
            return fail(step, startNanos, "Required parameter 'datasource' is missing or blank", null);
        }

        DataSource ds = datasourceProvider.get(datasourceName);
        if (ds == null) {
            return fail(step, startNanos, "No datasource registered for name: " + datasourceName, null);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                try {
                    return switch (operation) {
                        case "PURGE" -> executePurge(step, startNanos, ds);
                        case "POPULATE" -> executePopulate(step, startNanos, ds);
                        default -> fail(step, startNanos, "Unknown database operation: " + operation, null);
                    };
                } catch (Exception e) {
                    log.error("action=database_error operation={} datasource={} stepId={}",
                            operation, datasourceName, step.id().value(), e);
                    return fail(step, startNanos, e.getMessage(), e);
                }
            });
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("action=database_timeout operation={} datasource={} stepId={} timeoutMs={}",
                    operation, datasourceName, step.id().value(), timeoutMs, e);
            return fail(step, startNanos, "Operation timed out after " + timeoutMs + "ms", e);
        } catch (Exception e) {
            log.error("action=database_unexpected_error operation={} datasource={} stepId={}",
                    operation, datasourceName, step.id().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Exécute PURGE : supprime toutes les lignes de la table spécifiée.
     * Utilise DELETE (et non TRUNCATE) pour obtenir rowsAffected.
     * Utilise {@link JdbcTemplate} (ADR-013 Spring-first).
     */
    private TaskResult executePurge(StepDefinition step, long startNanos, DataSource ds) {
        String table = (String) step.parameters().get("table");
        if (table == null || table.isBlank()) {
            return fail(step, startNanos, "Required parameter 'table' is missing or blank for PURGE operation", null);
        }

        // Protection basique contre injection SQL sur le nom de table
        if (!table.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
            return fail(step, startNanos, "Invalid table name: " + table, null);
        }

        String sql = "DELETE FROM " + table;

        try {
            log.info("action=purge_start table={} datasource={} stepId={}",
                    table, step.parameters().get("datasource"), step.id().value());

            var jdbc = new JdbcTemplate(ds);
            int rowsAffected = jdbc.update(sql);

            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    "rowsAffected", rowsAffected,
                    "duration", formatDuration(elapsed)
            );

            log.info("action=purge_done table={} rowsAffected={} duration={} stepId={}",
                    table, rowsAffected, formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=purge_failed table={} stepId={}", table, step.id().value(), e);
            return fail(step, startNanos, "PURGE failed on table '" + table + "': " + e.getMessage(), e);
        }
    }

    /**
     * Exécute POPULATE : lit et exécute un script SQL depuis {@code scriptPath}.
     * <p>
     * Utilise {@link ResourceDatabasePopulator} (ADR-013 Spring-first) qui gère
     * correctement les séparateurs, commentaires SQL ({@code --}, {@code /*}),
     * blocs procéduraux et encodage.
     * <p>
     * {@link DefaultResourceLoader} résout nativement les préfixes {@code classpath:}
     * et les chemins filesystem.
     * <p>
     * Output : {@code {scriptExecuted, duration}} (pas de {@code rowsAffected} —
     * {@code ResourceDatabasePopulator} n'agrège pas les update counts).
     */
    private TaskResult executePopulate(StepDefinition step, long startNanos, DataSource ds) {
        String scriptPath = (String) step.parameters().get("scriptPath");
        if (scriptPath == null || scriptPath.isBlank()) {
            return fail(step, startNanos, "Required parameter 'scriptPath' is missing or blank for POPULATE operation", null);
        }

        // DefaultResourceLoader gère "classpath:" et les chemins filesystem nativement (ADR-013).
        var resourceLoader = new DefaultResourceLoader();
        Resource script = resourceLoader.getResource(scriptPath);
        if (!script.exists()) {
            return fail(step, startNanos, "Script not found: " + scriptPath, null);
        }

        var populator = new ResourceDatabasePopulator(script);
        populator.setSeparator(";");
        populator.setCommentPrefixes("--");
        populator.setContinueOnError(false);

        try (Connection conn = ds.getConnection()) {
            log.info("action=populate_start scriptPath={} datasource={} stepId={}",
                    scriptPath, step.parameters().get("datasource"), step.id().value());

            populator.populate(conn);

            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    "scriptExecuted", scriptPath,
                    "duration", formatDuration(elapsed)
            );

            log.info("action=populate_done scriptPath={} duration={} stepId={}",
                    scriptPath, formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=populate_failed scriptPath={} stepId={}", scriptPath, step.id().value(), e);
            return fail(step, startNanos, "POPULATE failed on script '" + scriptPath + "': " + e.getMessage(), e);
        }
    }

    /**
     * Produit un {@link TaskResult} d'échec avec le message et la cause donnés.
     */
    private TaskResult fail(StepDefinition step, long startNanos, String message, Throwable cause) {
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    /**
     * Formate une Duration en chaîne lisible (ex: "1.234s").
     */
    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }

    /**
     * Libère les connexions actives associées à l'executionId donné.
     * Si executionId est null, libère toutes les connexions.
     */
    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=cleanup_all activeConnections={}", connectionsByExecution.size());
            connectionsByExecution.values().forEach(this::closeConnection);
            connectionsByExecution.clear();
        } else {
            String key = executionId.value();
            Connection conn = connectionsByExecution.remove(key);
            if (conn != null) {
                log.info("action=cleanup_execution executionId={}", key);
                closeConnection(conn);
            }
        }
    }

    private void closeConnection(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            log.warn("action=close_connection_failed", e);
        }
    }
}
