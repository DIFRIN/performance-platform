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

import java.util.List;

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
        var flyway = Flyway.configure()
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
            var config = new HikariConfig();
            config.setJdbcUrl(postgres.getJdbcUrl());
            config.setUsername(postgres.getUsername());
            config.setPassword(postgres.getPassword());
            return new HikariDataSource(config);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan("com.performance.platform.infrastructure.persistence");
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            // Hibernate 6.6 SessionFactory implements EntityManagerFactory;
            // avoid proxy interface conflict with Spring 7.0 EntityManagerFactoryInfo mixin
            emf.setEntityManagerFactoryInterface(jakarta.persistence.EntityManagerFactory.class);
            var props = new Properties();
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
                                                        TaskResultMapper taskResultMapper,
                                                        PlatformTransactionManager transactionManager) {
            return new JpaExecutionRepository(stateRepo, taskResultRepo, stateMapper, taskResultMapper,
                    transactionManager);
        }
    }

    // --- Test helpers ---

    private ExecutionState createTestState(ExecutionId executionId, ScenarioId scenarioId) {
        Map<Phase, PhaseStatus> phases = new EnumMap<>(Phase.class);
        phases.put(Phase.PREPARATION, PhaseStatus.COMPLETED);
        phases.put(Phase.INJECTION, PhaseStatus.RUNNING);

        var ctx = ExecutionContext.initial(executionId, scenarioId);

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
            var id = ExecutionId.generate();
            var scenarioId = ScenarioId.of("scenario-" + ExecutionId.generate().value());
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
            var unknownId = ExecutionId.generate();

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
            var id = ExecutionId.generate();
            var scenarioId = ScenarioId.of("scenario-" + ExecutionId.generate().value());
            ExecutionState original = createTestState(id, scenarioId);

            repository.save(original);

            // Re-save with different status and updated timestamp
            Map<Phase, PhaseStatus> updatedPhases = new EnumMap<>(Phase.class);
            updatedPhases.put(Phase.PREPARATION, PhaseStatus.COMPLETED);
            updatedPhases.put(Phase.INJECTION, PhaseStatus.COMPLETED);
            updatedPhases.put(Phase.ASSERTION, PhaseStatus.RUNNING);

            var ctx2 = ExecutionContext.initial(id, scenarioId);
            var updated = new ExecutionState(
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
            var id = ExecutionId.generate();
            var scenarioId = ScenarioId.of("scenario-" + ExecutionId.generate().value());
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
            var unknownId = ExecutionId.generate();

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
            var executionId = ExecutionId.generate();
            var taskId = TaskId.of("task-load-inject");
            var agent1 = AgentId.generate();
            var agent2 = AgentId.generate();

            var result1 = TaskResult.success(
                    taskId, "load-inject", Duration.ofMillis(1500),
                    Map.of("throughput", 500.0));
            var result2 = TaskResult.success(
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
            var executionId = ExecutionId.generate();
            var taskId = TaskId.of("task-retry");
            var agent = AgentId.generate();

            var first = TaskResult.failed(
                    taskId, "task-retry", Duration.ofMillis(500),
                    "timeout", new RuntimeException("timeout"));
            repository.saveTaskResult(executionId, taskId, agent, first);

            // Upsert with success
            var second = TaskResult.success(
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
            var executionId = ExecutionId.generate();
            var taskId = TaskId.of("task-nonexistent");

            Map<AgentId, TaskResult> results = repository.getTaskResults(executionId, taskId);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should not return results for different execution or task")
        void shouldNotCrossPolluteResults() {
            var exec1 = ExecutionId.generate();
            var exec2 = ExecutionId.generate();
            var taskA = TaskId.of("task-a");
            var taskB = TaskId.of("task-b");
            var agent = AgentId.generate();

            var result = TaskResult.success(
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

    @Nested
    @DisplayName("findAll(limit)")
    class FindAll {

        @Test
        @DisplayName("should return the N most recent executions sorted desc by startedAt")
        void shouldReturnMostRecentSortedDesc() {
            // Given: 3 executions with distinct startedAt timestamps
            var scenarioId = ScenarioId.of("scenario-findall");

            var id1 = ExecutionId.generate();
            var state1 = new ExecutionState(
                    id1, scenarioId, ExecutionStatus.RUNNING,
                    new java.util.EnumMap<>(Phase.class),
                    ExecutionContext.initial(id1, scenarioId),
                    Instant.parse("2026-01-01T08:00:00Z"),
                    Instant.parse("2026-01-01T08:01:00Z"));

            var id2 = ExecutionId.generate();
            var state2 = new ExecutionState(
                    id2, scenarioId, ExecutionStatus.RUNNING,
                    new java.util.EnumMap<>(Phase.class),
                    ExecutionContext.initial(id2, scenarioId),
                    Instant.parse("2026-01-01T10:00:00Z"),
                    Instant.parse("2026-01-01T10:01:00Z"));

            var id3 = ExecutionId.generate();
            var state3 = new ExecutionState(
                    id3, scenarioId, ExecutionStatus.RUNNING,
                    new java.util.EnumMap<>(Phase.class),
                    ExecutionContext.initial(id3, scenarioId),
                    Instant.parse("2026-01-01T09:00:00Z"),
                    Instant.parse("2026-01-01T09:01:00Z"));

            repository.save(state1);
            repository.save(state2);
            repository.save(state3);

            // When: findAll with limit=2
            List<ExecutionState> results = repository.findAll(2);

            // Then: 2 most recent, sorted desc
            assertThat(results).hasSize(2);
            assertThat(results.get(0).id()).isEqualTo(id2); // most recent: 10:00
            assertThat(results.get(1).id()).isEqualTo(id3); // second most recent: 09:00
        }

        @Test
        @DisplayName("should return all when count is less than limit")
        void shouldReturnAllWhenCountLessThanLimit() {
            var scenarioId = ScenarioId.of("scenario-findall-small");
            var id = ExecutionId.generate();
            var state = new ExecutionState(
                    id, scenarioId, ExecutionStatus.RUNNING,
                    new java.util.EnumMap<>(Phase.class),
                    ExecutionContext.initial(id, scenarioId),
                    Instant.parse("2026-01-01T12:00:00Z"),
                    Instant.parse("2026-01-01T12:01:00Z"));
            repository.save(state);

            List<ExecutionState> results = repository.findAll(50);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo(id);
        }

        @Test
        @DisplayName("should return empty list when no executions exist")
        void shouldReturnEmptyListWhenNoExecutions() {
            List<ExecutionState> results = repository.findAll(10);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("should delete execution and its task results")
        void shouldDeleteExecutionAndResults() {
            var id = ExecutionId.generate();
            var scenarioId = ScenarioId.of("scenario-delete");
            ExecutionState state = createTestState(id, scenarioId);
            repository.save(state);

            var taskId = TaskId.of("task-to-delete");
            var agent = AgentId.generate();
            var result = TaskResult.success(taskId, "task-to-delete",
                    Duration.ofMillis(100), Map.of("x", 1));
            repository.saveTaskResult(id, taskId, agent, result);

            // Pre-condition: execution and result exist
            assertThat(repository.findById(id)).isPresent();
            assertThat(repository.getTaskResults(id, taskId)).hasSize(1);

            repository.deleteById(id);

            // Post-condition: both are gone
            assertThat(repository.findById(id)).isEmpty();
            assertThat(repository.getTaskResults(id, taskId)).isEmpty();
        }

        @Test
        @DisplayName("deleteById should be no-op when id is unknown")
        void shouldBeNoOpWhenIdUnknown() {
            var unknownId = ExecutionId.generate();

            // Must not throw
            repository.deleteById(unknownId);

            assertThat(repository.findById(unknownId)).isEmpty();
        }

        @Test
        @DisplayName("should not delete other executions")
        void shouldNotDeleteOtherExecutions() {
            var id1 = ExecutionId.generate();
            var id2 = ExecutionId.generate();
            var scenarioId = ScenarioId.of("scenario-delete-isolation");

            repository.save(createTestState(id1, scenarioId));
            repository.save(createTestState(id2, scenarioId));

            repository.deleteById(id1);

            assertThat(repository.findById(id1)).isEmpty();
            assertThat(repository.findById(id2)).isPresent();
        }
    }
}
