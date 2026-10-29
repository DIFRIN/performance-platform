package com.performance.platform.infrastructure.executor.database;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DatabaseTaskExecutor")
class DatabaseTaskExecutorTest {

    private DataSource dataSource;
    private DatasourceProvider datasourceProvider;
    private DatabaseTaskExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        var h2Ds = new JdbcDataSource();
        h2Ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        h2Ds.setUser("sa");
        h2Ds.setPassword("");
        this.dataSource = h2Ds;

        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test_table (id INT, val VARCHAR)");
            stmt.execute("INSERT INTO test_table VALUES (1, 'a')");
            stmt.execute("INSERT INTO test_table VALUES (2, 'b')");
            stmt.execute("INSERT INTO test_table VALUES (3, 'c')");
        }

        this.datasourceProvider = new DatasourceProvider();
        this.datasourceProvider.register("app-db", dataSource);

        this.executor = new DatabaseTaskExecutor(datasourceProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_table");
        }
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"),
                ScenarioId.of("scenario-001"));
    }

    @Nested
    @DisplayName("QUERY operation")
    class QueryOperation {

        @Test
        @DisplayName("should execute SELECT query and return rows with rowCount")
        void shouldExecuteSelectQuery() {
            var step = new StepDefinition(
                    TaskId.of("step-001"), "database", Phase.PREPARATION,
                    Map.of("operation", "QUERY", "datasource", "app-db",
                            "query", "SELECT COUNT(*) FROM test_table", "queryType", "SELECT"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.taskName()).isEqualTo("database");
            assertThat(result.outputs()).containsKey("rows");
            assertThat(result.outputs()).containsKey("rowCount");
            assertThat(result.outputs()).containsKey("duration");
            assertThat(result.outputs().get("rowCount")).isEqualTo(1);
            assertThat((String) result.outputs().get("duration")).endsWith("s");
        }

        @Test
        @DisplayName("should execute UPDATE query and return rowsAffected")
        void shouldExecuteUpdateQuery() {
            var step = new StepDefinition(
                    TaskId.of("step-002"), "database", Phase.PREPARATION,
                    Map.of("operation", "QUERY", "datasource", "app-db",
                            "query", "UPDATE test_table SET val = 'updated'", "queryType", "UPDATE"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey("rowsAffected");
            assertThat(result.outputs()).containsKey("duration");
            assertThat((Integer) result.outputs().get("rowsAffected")).isGreaterThan(0);
        }

        @Test
        @DisplayName("should default to UPDATE when queryType is not specified")
        void shouldDefaultToUpdateWhenNoQueryType() {
            var step = new StepDefinition(
                    TaskId.of("step-003"), "database", Phase.PREPARATION,
                    Map.of("operation", "QUERY", "datasource", "app-db",
                            "query", "DELETE FROM test_table"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey("rowsAffected");
            assertThat(result.outputs()).containsKey("duration");
            assertThat((Integer) result.outputs().get("rowsAffected")).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should fail when query parameter is missing")
        void shouldFailOnMissingQuery() {
            var step = new StepDefinition(
                    TaskId.of("step-004"), "database", Phase.PREPARATION,
                    Map.of("operation", "QUERY", "datasource", "app-db"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Required parameter 'query'");
        }

        @Test
        @DisplayName("should fail when datasource parameter is missing")
        void shouldFailOnMissingDatasourceForQuery() {
            var step = new StepDefinition(
                    TaskId.of("step-005"), "database", Phase.PREPARATION,
                    Map.of("operation", "QUERY", "query", "SELECT 1"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Required parameter 'datasource'");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should fail for unknown operation")
        void shouldFailOnUnknownOperation() {
            var step = new StepDefinition(
                    TaskId.of("step-010"), "database", Phase.PREPARATION,
                    Map.of("operation", "INVALID_OP", "datasource", "app-db"),
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
    }
}
