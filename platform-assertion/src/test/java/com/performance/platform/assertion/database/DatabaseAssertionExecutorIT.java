package com.performance.platform.assertion.database;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static com.performance.platform.domain.scenario.Phase.ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DatabaseAssertionExecutor IT")
@Testcontainers
@Tag("integration-tests")
class DatabaseAssertionExecutorIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private static GenericApplicationContext applicationContext;
    private static DatabaseAssertionExecutor executor;
    private static DataSource dataSource;

    private static final ExecutionId EXEC_ID = new ExecutionId("exec-001");
    private static final ScenarioId SCENARIO_ID = new ScenarioId("scenario-001");
    private static final TaskId ASSERTION_ID = new TaskId("assertion-001");

    @BeforeAll
    static void setUp() {
        var pgDataSource = new PGSimpleDataSource();
        pgDataSource.setUrl(postgres.getJdbcUrl());
        pgDataSource.setUser(postgres.getUsername());
        pgDataSource.setPassword(postgres.getPassword());
        dataSource = pgDataSource;

        applicationContext = new GenericApplicationContext();
        applicationContext.getBeanFactory()
                .registerSingleton("testdb", dataSource);
        applicationContext.refresh();

        executor = new DatabaseAssertionExecutor(applicationContext);
    }

    @AfterAll
    static void tearDown() {
        applicationContext.close();
    }

    @BeforeEach
    void resetTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS metrics CASCADE");
            stmt.execute("""
                CREATE TABLE metrics (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    value DOUBLE PRECISION NOT NULL
                )
                """);
            // Insérer 5 lignes pour les tests de comptage
            stmt.execute("INSERT INTO metrics (name, value) VALUES ('alpha', 10.0)");
            stmt.execute("INSERT INTO metrics (name, value) VALUES ('beta', 20.0)");
            stmt.execute("INSERT INTO metrics (name, value) VALUES ('gamma', 30.0)");
            stmt.execute("INSERT INTO metrics (name, value) VALUES ('delta', 40.0)");
            stmt.execute("INSERT INTO metrics (name, value) VALUES ('epsilon', 50.0)");
        }
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID);
    }

    private static StepDefinition step(Map<String, Object> params) {
        return new StepDefinition(ASSERTION_ID, "database", ASSERTION,
                params, null, null, null, null);
    }

    // --- Nominal ---

    @Test
    @DisplayName("should pass COUNT equals expected")
    void shouldPassCountEq() {
        StepDefinition step = step(Map.of(
                "datasource", "testdb",
                "query", "SELECT COUNT(*) FROM metrics",
                "operator", "EQ",
                "value", 5));

        AssertionResult result = executor.evaluate(emptyContext(), step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.evidence().actualValue()).isEqualTo(5.0);
        assertThat(result.evidence().expectedValue()).isEqualTo(5.0);
        assertThat(result.evidence().operator()).isEqualTo(AssertionOperator.EQ);
    }

    @Test
    @DisplayName("should fail COUNT less than expected")
    void shouldFailCountLt() {
        StepDefinition step = step(Map.of(
                "datasource", "testdb",
                "query", "SELECT COUNT(*) FROM metrics",
                "operator", "LT",
                "value", 3));

        AssertionResult result = executor.evaluate(emptyContext(), step);

        assertThat(result.status()).isEqualTo(AssertionStatus.FAILED);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.description()).contains("FAILED", "5.00");
    }

    @Test
    @DisplayName("should pass with aggregate SUM query")
    void shouldPassSumQuery() {
        StepDefinition step = step(Map.of(
                "datasource", "testdb",
                "query", "SELECT SUM(value) FROM metrics",
                "operator", "EQ",
                "value", 150.0));

        AssertionResult result = executor.evaluate(emptyContext(), step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().actualValue()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("should pass AVG greater than threshold")
    void shouldPassAvgGt() {
        StepDefinition step = step(Map.of(
                "datasource", "testdb",
                "query", "SELECT AVG(value) FROM metrics",
                "operator", "GT",
                "value", 20.0));

        AssertionResult result = executor.evaluate(emptyContext(), step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(result.evidence().actualValue()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("should pass MAX less than or equal threshold")
    void shouldPassMaxLte() {
        StepDefinition step = step(Map.of(
                "datasource", "testdb",
                "query", "SELECT MAX(value) FROM metrics",
                "operator", "LTE",
                "value", 50.0));

        AssertionResult result = executor.evaluate(emptyContext(), step);

        assertThat(result.status()).isEqualTo(AssertionStatus.PASSED);
    }

    @Test
    @DisplayName("should use custom unit in evidence")
    void shouldUseCustomUnit() {
        StepDefinition step = step(Map.of(
                "datasource", "testdb",
                "query", "SELECT COUNT(*) FROM metrics",
                "operator", "EQ",
                "value", 5,
                "unit", "records"));

        AssertionResult result = executor.evaluate(emptyContext(), step);

        assertThat(result.evidence().unit()).isEqualTo("records");
    }

    @Test
    @DisplayName("should support all assertion operators with COUNT")
    void shouldSupportAllOperators() {
        String[][] testCases = {
                {"GT", "4"},
                {"GTE", "5"},
                {"LT", "6"},
                {"LTE", "5"},
                {"EQ", "5"},
                {"NEQ", "0"},
        };
        for (String[] tc : testCases) {
            StepDefinition step = step(Map.of(
                    "datasource", "testdb",
                    "query", "SELECT COUNT(*) FROM metrics",
                    "operator", tc[0],
                    "value", Integer.parseInt(tc[1])));

            AssertionResult result = executor.evaluate(emptyContext(), step);
            assertThat(result.status())
                    .as("operator=%s value=%s", tc[0], tc[1])
                    .isEqualTo(AssertionStatus.PASSED);
        }
    }

    // --- Error cases ---

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should return ERROR when datasource not found")
        void shouldErrorOnMissingDatasource() {
            StepDefinition step = step(Map.of(
                    "datasource", "nonexistent",
                    "query", "SELECT COUNT(*) FROM metrics",
                    "operator", "EQ",
                    "value", 5));

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("nonexistent");
        }

        @Test
        @DisplayName("should return ERROR when query returns no rows")
        void shouldErrorOnEmptyResult() throws Exception {
            // Supprimer toutes les lignes pour vider la table
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM metrics");
            }
            // Requete non-agregee sur table vide → rs.next() == false → SQLException
            StepDefinition step = step(Map.of(
                    "datasource", "testdb",
                    "query", "SELECT value FROM metrics",
                    "operator", "EQ",
                    "value", 0));

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("no rows");
        }

        @Test
        @DisplayName("should return ERROR for invalid SQL")
        void shouldErrorOnInvalidSql() {
            StepDefinition step = step(Map.of(
                    "datasource", "testdb",
                    "query", "SELECT * FROM nonexistent_table",
                    "operator", "EQ",
                    "value", 5));

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("SQL error");
        }

        @Test
        @DisplayName("should return ERROR for unsupported operator")
        void shouldErrorOnUnsupportedOperator() {
            StepDefinition step = step(Map.of(
                    "datasource", "testdb",
                    "query", "SELECT COUNT(*) FROM metrics",
                    "operator", "BETWEEN",
                    "value", 5));

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("BETWEEN");
        }

        @Test
        @DisplayName("should return ERROR when datasource param missing")
        void shouldErrorOnMissingDatasourceParam() {
            StepDefinition step = step(Map.of(
                    "query", "SELECT COUNT(*) FROM metrics",
                    "operator", "EQ",
                    "value", 5));

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("datasource");
        }

        @Test
        @DisplayName("should return ERROR when query param missing")
        void shouldErrorOnMissingQueryParam() {
            StepDefinition step = step(Map.of(
                    "datasource", "testdb",
                    "operator", "EQ",
                    "value", 5));

            AssertionResult result = executor.evaluate(emptyContext(), step);

            assertThat(result.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(result.description()).contains("query");
        }
    }

    // --- Null safety ---

    @Nested
    @DisplayName("Null safety")
    class NullSafety {

        @Test
        @DisplayName("should throw NPE when context is null")
        void shouldThrowNpeOnNullContext() {
            StepDefinition step = step(Map.of(
                    "datasource", "testdb",
                    "query", "SELECT COUNT(*) FROM metrics",
                    "operator", "EQ",
                    "value", 5));

            assertThatThrownBy(() -> executor.evaluate(null, step))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when step is null")
        void shouldThrowNpeOnNullStep() {
            assertThatThrownBy(() -> executor.evaluate(emptyContext(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when applicationContext is null")
        void shouldThrowNpeOnNullAppContext() {
            assertThatThrownBy(() -> new DatabaseAssertionExecutor(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("should return supported assertion name")
    void shouldReturnSupportedAssertionName() {
        assertThat(executor.getSupportedAssertionName()).isEqualTo("database");
    }
}
