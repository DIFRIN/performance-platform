package com.performance.platform.infrastructure.persistence;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.infrastructure.persistence.mapper.ExecutionStateMapper;
import com.performance.platform.infrastructure.persistence.mapper.TaskResultMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
// Transaction management is handled at the Spring Data JPA repository layer
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JpaExecutionRepository IT")
@Testcontainers
@Tag("integration-tests")
class JpaExecutionRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static AnnotationConfigApplicationContext context;
    private JpaExecutionRepository repository;

    @BeforeAll
    static void setUp() {
        // 1. Run Flyway migrations before Hibernate validates the schema
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // 2. Start Spring context with JPA configuration
        context = new AnnotationConfigApplicationContext();
        context.register(IntegrationConfig.class);
        context.refresh();
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void init() {
        repository = context.getBean(JpaExecutionRepository.class);
    }

    @AfterEach
    void cleanup() {
        // Clean up tables after each test for isolation
        TaskResultJpaRepository taskResultRepo = context.getBean(TaskResultJpaRepository.class);
        ExecutionStateJpaRepository stateRepo = context.getBean(ExecutionStateJpaRepository.class);
        taskResultRepo.deleteAll();
        stateRepo.deleteAll();
    }

    /**
     * Spring configuration for the integration test.
     * Creates a minimal context with DataSource (Testcontainers),
     * JPA EntityManagerFactory, transaction management, and
     * Spring Data JPA repositories.
     */
    @Configuration
    @EnableJpaRepositories(basePackages = "com.performance.platform.infrastructure.persistence")
    static class IntegrationConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(postgres.getJdbcUrl());
            config.setUsername(postgres.getUsername());
            config.setPassword(postgres.getPassword());
            return new HikariDataSource(config);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan("com.performance.platform.infrastructure.persistence");
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            // Hibernate 6.6 SessionFactory implements EntityManagerFactory;
            // avoid proxy interface conflict with Spring 7.0 EntityManagerFactoryInfo mixin
            emf.setEntityManagerFactoryInterface(jakarta.persistence.EntityManagerFactory.class);
            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "validate");
            props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            props.setProperty("hibernate.format_sql", "true");
            props.setProperty("hibernate.show_sql", "false");
            emf.setJpaProperties(props);
            return emf;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean
        ExecutionStateMapper executionStateMapper() {
            return new ExecutionStateMapper();
        }

        @Bean
        TaskResultMapper taskResultMapper() {
            return new TaskResultMapper();
        }

        @Bean
        JpaExecutionRepository jpaExecutionRepository(ExecutionStateJpaRepository stateRepo,
                                                        TaskResultJpaRepository taskResultRepo,
                                                        ExecutionStateMapper stateMapper,
                                                        TaskResultMapper taskResultMapper) {
            return new JpaExecutionRepository(stateRepo, taskResultRepo, stateMapper, taskResultMapper);
        }
    }

    // --- Test helpers ---

    private ExecutionState createTestState(ExecutionId executionId, ScenarioId scenarioId) {
        Map<Phase, PhaseStatus> phases = new EnumMap<>(Phase.class);
        phases.put(Phase.PREPARATION, PhaseStatus.COMPLETED);
        phases.put(Phase.INJECTION, PhaseStatus.RUNNING);

        ExecutionContext ctx = ExecutionContext.initial(executionId, scenarioId);

        return new ExecutionState(
                executionId,
                scenarioId,
                ExecutionStatus.RUNNING,
                phases,
                ctx,
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:05:00Z")
        );
    }

    // --- Tests ---

    @Nested
    @DisplayName("save and findById round-trip")
    class SaveAndFind {

        @Test
        @DisplayName("should persist and retrieve an execution state")
        void shouldPersistAndRetrieve() {
            ExecutionId id = ExecutionId.generate();
            ScenarioId scenarioId = ScenarioId.of("scenario-" + ExecutionId.generate().value());
            ExecutionState state = createTestState(id, scenarioId);

            repository.save(state);

            Optional<ExecutionState> found = repository.findById(id);
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(id);
            assertThat(found.get().scenarioId()).isEqualTo(scenarioId);
            assertThat(found.get().status()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(found.get().phaseStatuses())
                    .containsEntry(Phase.PREPARATION, PhaseStatus.COMPLETED)
                    .containsEntry(Phase.INJECTION, PhaseStatus.RUNNING);
            assertThat(found.get().startedAt()).isEqualTo(Instant.parse("2026-06-19T10:00:00Z"));
            assertThat(found.get().updatedAt()).isEqualTo(Instant.parse("2026-06-19T10:05:00Z"));
        }

        @Test
        @DisplayName("should return empty optional for unknown execution")
        void shouldReturnEmptyForUnknownId() {
            ExecutionId unknownId = ExecutionId.generate();

            Optional<ExecutionState> found = repository.findById(unknownId);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("save idempotency")
    class SaveIdempotency {

        @Test
        @DisplayName("re-saving same ID should update, not duplicate")
        void shouldUpdateOnReSave() {
            ExecutionId id = ExecutionId.generate();
            ScenarioId scenarioId = ScenarioId.of("scenario-" + ExecutionId.generate().value());
            ExecutionState original = createTestState(id, scenarioId);

            repository.save(original);

            // Re-save with different status and updated timestamp
            Map<Phase, PhaseStatus> updatedPhases = new EnumMap<>(Phase.class);
            updatedPhases.put(Phase.PREPARATION, PhaseStatus.COMPLETED);
            updatedPhases.put(Phase.INJECTION, PhaseStatus.COMPLETED);
            updatedPhases.put(Phase.ASSERTION, PhaseStatus.RUNNING);

            ExecutionContext ctx2 = ExecutionContext.initial(id, scenarioId);
            ExecutionState updated = new ExecutionState(
                    id, scenarioId,
                    ExecutionStatus.RUNNING,
                    updatedPhases,
                    ctx2,
                    Instant.parse("2026-06-19T10:00:00Z"),
                    Instant.parse("2026-06-19T10:10:00Z")
            );
            repository.save(updated);

            // Verify only one row exists and it reflects the update
            Optional<ExecutionState> found = repository.findById(id);
            assertThat(found).isPresent();
            assertThat(found.get().phaseStatuses()).hasSize(3);
            assertThat(found.get().phaseStatuses())
                    .containsEntry(Phase.ASSERTION, PhaseStatus.RUNNING);
            assertThat(found.get().updatedAt()).isEqualTo(Instant.parse("2026-06-19T10:10:00Z"));
        }
    }

    @Nested
    @DisplayName("updatePhase")
    class UpdatePhase {

        @Test
        @DisplayName("should update a specific phase status")
        void shouldUpdatePhase() {
            ExecutionId id = ExecutionId.generate();
            ScenarioId scenarioId = ScenarioId.of("scenario-" + ExecutionId.generate().value());
            ExecutionState state = createTestState(id, scenarioId);
            repository.save(state);

            repository.updatePhase(id, Phase.INJECTION, PhaseStatus.COMPLETED);

            Optional<ExecutionState> found = repository.findById(id);
            assertThat(found).isPresent();
            assertThat(found.get().phaseStatuses())
                    .containsEntry(Phase.INJECTION, PhaseStatus.COMPLETED)
                    .containsEntry(Phase.PREPARATION, PhaseStatus.COMPLETED);
            assertThat(found.get().updatedAt()).isAfter(state.updatedAt());
        }

        @Test
        @DisplayName("should throw when execution not found")
        void shouldThrowWhenExecutionNotFound() {
            ExecutionId unknownId = ExecutionId.generate();

            assertThatThrownBy(() -> repository.updatePhase(unknownId, Phase.PREPARATION, PhaseStatus.COMPLETED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(unknownId.value());
        }
    }

    @Nested
    @DisplayName("saveTaskResult and getTaskResults (multi-claim)")
    class TaskResults {

        @Test
        @DisplayName("should store and retrieve task results by execution and task")
        void shouldStoreAndRetrieveTaskResults() {
            ExecutionId executionId = ExecutionId.generate();
            TaskId taskId = TaskId.of("task-load-inject");
            AgentId agent1 = AgentId.generate();
            AgentId agent2 = AgentId.generate();

            TaskResult result1 = TaskResult.success(
                    taskId, "load-inject", Duration.ofMillis(1500),
                    Map.of("throughput", 500.0));
            TaskResult result2 = TaskResult.success(
                    taskId, "load-inject", Duration.ofMillis(1200),
                    Map.of("throughput", 480.0));

            repository.saveTaskResult(executionId, taskId, agent1, result1);
            repository.saveTaskResult(executionId, taskId, agent2, result2);

            Map<AgentId, TaskResult> results = repository.getTaskResults(executionId, taskId);

            assertThat(results).hasSize(2);
            assertThat(results.keySet()).containsExactlyInAnyOrder(agent1, agent2);
            assertThat(results.get(agent1).status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(results.get(agent2).status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(results.get(agent1).outputs()).containsEntry("throughput", 500.0);
            assertThat(results.get(agent2).outputs()).containsEntry("throughput", 480.0);
        }

        @Test
        @DisplayName("saveTaskResult upsert should replace existing result for same agent")
        void shouldUpsertTaskResult() {
            ExecutionId executionId = ExecutionId.generate();
            TaskId taskId = TaskId.of("task-retry");
            AgentId agent = AgentId.generate();

            TaskResult first = TaskResult.failed(
                    taskId, "task-retry", Duration.ofMillis(500),
                    "timeout", new RuntimeException("timeout"));
            repository.saveTaskResult(executionId, taskId, agent, first);

            // Upsert with success
            TaskResult second = TaskResult.success(
                    taskId, "task-retry", Duration.ofMillis(800),
                    Map.of("rows", 100));
            repository.saveTaskResult(executionId, taskId, agent, second);

            Map<AgentId, TaskResult> results = repository.getTaskResults(executionId, taskId);

            assertThat(results).hasSize(1);
            assertThat(results.get(agent).status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(results.get(agent).outputs()).containsEntry("rows", 100);
            assertThat(results.get(agent).taskName()).isEqualTo("task-retry");
        }

        @Test
        @DisplayName("should return empty map when no task results exist")
        void shouldReturnEmptyMapForNoResults() {
            ExecutionId executionId = ExecutionId.generate();
            TaskId taskId = TaskId.of("task-nonexistent");

            Map<AgentId, TaskResult> results = repository.getTaskResults(executionId, taskId);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should not return results for different execution or task")
        void shouldNotCrossPolluteResults() {
            ExecutionId exec1 = ExecutionId.generate();
            ExecutionId exec2 = ExecutionId.generate();
            TaskId taskA = TaskId.of("task-a");
            TaskId taskB = TaskId.of("task-b");
            AgentId agent = AgentId.generate();

            TaskResult result = TaskResult.success(
                    taskA, "task-a", Duration.ofMillis(100), Map.of("val", 1));
            repository.saveTaskResult(exec1, taskA, agent, result);

            // Query for different execution
            Map<AgentId, TaskResult> wrongExec = repository.getTaskResults(exec2, taskA);
            assertThat(wrongExec).isEmpty();

            // Query for different task
            Map<AgentId, TaskResult> wrongTask = repository.getTaskResults(exec1, taskB);
            assertThat(wrongTask).isEmpty();
        }
    }
}
