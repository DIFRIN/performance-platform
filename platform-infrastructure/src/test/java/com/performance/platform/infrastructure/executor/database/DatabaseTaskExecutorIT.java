package com.performance.platform.infrastructure.executor.database;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DatabaseTaskExecutor IT")
@Testcontainers
@Tag("integration-tests")
class DatabaseTaskExecutorIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private static DatasourceProvider datasourceProvider;
    private static DatabaseTaskExecutor executor;
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void setUp() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        datasourceProvider = new DatasourceProvider();
        datasourceProvider.register("testdb", dataSource);

        executor = new DatabaseTaskExecutor(datasourceProvider);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (dataSource != null) {
            try (var conn = dataSource.getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS orders CASCADE");
                stmt.execute("DROP TABLE IF EXISTS products CASCADE");
            }
        }
    }

    @BeforeEach
    void resetTables() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS orders CASCADE");
            stmt.execute("DROP TABLE IF EXISTS products CASCADE");

            stmt.execute("""
                CREATE TABLE orders (
                    id SERIAL PRIMARY KEY,
                    customer_name VARCHAR(255) NOT NULL,
                    amount DECIMAL(10,2) NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE products (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    price DECIMAL(10,2) NOT NULL
                )
            """);
            // Seed data
            stmt.execute("INSERT INTO orders (customer_name, amount) VALUES ('Alice', 100.00)");
            stmt.execute("INSERT INTO orders (customer_name, amount) VALUES ('Bob', 200.00)");
            stmt.execute("INSERT INTO orders (customer_name, amount) VALUES ('Charlie', 300.00)");
        }
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"),
                ScenarioId.of("scenario-001"));
    }

    @Nested
    @DisplayName("PURGE operation")
    class PurgeOperation {

        @Test
        @DisplayName("should delete all rows and return rowsAffected")
        void shouldDeleteAllRows() {
            var step = new StepDefinition(
                    TaskId.of("step-001"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "datasource", "testdb", "table", "orders"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.taskName()).isEqualTo("database");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey("rowsAffected");
            assertThat(result.outputs()).containsKey("duration");
            assertThat(result.outputs().get("rowsAffected")).isEqualTo(3);
            assertThat((String) result.outputs().get("duration")).endsWith("s");
        }

        @Test
        @DisplayName("should return 0 rowsAffected for empty table")
        void shouldReturnZeroForEmptyTable() throws Exception {
            try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM orders");
            }

            var step = new StepDefinition(
                    TaskId.of("step-002"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "datasource", "testdb", "table", "orders"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get("rowsAffected")).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject invalid table name")
        void shouldRejectInvalidTableName() {
            var step = new StepDefinition(
                    TaskId.of("step-003"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "datasource", "testdb", "table", "orders; DROP TABLE products"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Invalid table name");
        }

        @Test
        @DisplayName("should fail when table parameter is missing")
        void shouldFailWhenTableMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-004"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "datasource", "testdb"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("table");
        }
    }

    @Nested
    @DisplayName("POPULATE operation")
    class PopulateOperation {

        @Test
        @DisplayName("should execute script and return scriptExecuted")
        void shouldExecuteScript() throws Exception {
            Path script = Files.createTempFile("populate-orders", ".sql");
            script.toFile().deleteOnExit();
            Files.writeString(script, """
                    INSERT INTO orders (customer_name, amount) VALUES ('Diana', 400.00);
                    INSERT INTO orders (customer_name, amount) VALUES ('Eve', 500.00);
                    INSERT INTO orders (customer_name, amount) VALUES ('Frank', 600.00);
                    """);

            var step = new StepDefinition(
                    TaskId.of("step-010"), "database", Phase.PREPARATION,
                    Map.of("operation", "POPULATE", "datasource", "testdb", "scriptPath", "file:" + script.toString()),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            // ADR-013 : ResourceDatabasePopulator ne retourne pas rowsAffected → scriptExecuted
            assertThat(result.outputs()).containsKey("scriptExecuted");
            assertThat(result.outputs()).containsKey("duration");
            assertThat((String) result.outputs().get("scriptExecuted")).isEqualTo("file:" + script.toString());
            assertThat((String) result.outputs().get("duration")).endsWith("s");
        }

        @Test
        @DisplayName("should fail when script file is not found")
        void shouldFailWhenScriptNotFound() {
            var step = new StepDefinition(
                    TaskId.of("step-011"), "database", Phase.PREPARATION,
                    Map.of("operation", "POPULATE", "datasource", "testdb", "scriptPath", "/nonexistent/script.sql"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("script");
        }

        @Test
        @DisplayName("should fail when scriptPath parameter is missing")
        void shouldFailWhenScriptPathMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-012"), "database", Phase.PREPARATION,
                    Map.of("operation", "POPULATE", "datasource", "testdb"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("scriptPath");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return failed when datasource name is missing")
        void shouldFailWhenDatasourceMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-020"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "table", "orders"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("datasource");
        }

        @Test
        @DisplayName("should return failed when datasource is not registered")
        void shouldFailWhenDatasourceNotRegistered() {
            var step = new StepDefinition(
                    TaskId.of("step-021"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "datasource", "unknown-db", "table", "orders"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("No datasource registered");
        }

        @Test
        @DisplayName("should return failed for unknown operation")
        void shouldFailForUnknownOperation() {
            var step = new StepDefinition(
                    TaskId.of("step-022"), "database", Phase.PREPARATION,
                    Map.of("operation", "UNKNOWN_OP", "datasource", "testdb"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown database operation");
        }
    }

    @Nested
    @DisplayName("TaskExecutor contract")
    class TaskExecutorContract {

        @Test
        @DisplayName("should return 'database' as supported task name")
        void shouldReturnDatabaseAsSupportedTaskName() {
            assertThat(executor.getSupportedTaskName()).isEqualTo("database");
        }

        @Test
        @DisplayName("should include correct taskName in TaskResult")
        void shouldIncludeTaskNameInResult() {
            var step = new StepDefinition(
                    TaskId.of("step-030"), "database", Phase.PREPARATION,
                    Map.of("operation", "PURGE", "datasource", "testdb", "table", "orders"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.taskName()).isEqualTo("database");
            assertThat(result.taskId()).isEqualTo(TaskId.of("step-030"));
        }
    }
}
