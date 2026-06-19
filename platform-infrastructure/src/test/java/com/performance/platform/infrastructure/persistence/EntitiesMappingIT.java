package com.performance.platform.infrastructure.persistence;

import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskStatus;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EntitiesMappingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static SessionFactory sessionFactory;

    @BeforeAll
    static void setUp() {
        // Run Flyway migrations
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Build Hibernate SessionFactory programmatically
        Configuration cfg = new Configuration();
        cfg.setProperty(AvailableSettings.JAKARTA_JDBC_URL, postgres.getJdbcUrl());
        cfg.setProperty(AvailableSettings.JAKARTA_JDBC_USER, postgres.getUsername());
        cfg.setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, postgres.getPassword());
        cfg.setProperty(AvailableSettings.HBM2DDL_AUTO, "validate");
        cfg.setProperty(AvailableSettings.SHOW_SQL, "false");
        cfg.setProperty(AvailableSettings.FORMAT_SQL, "true");
        cfg.addAnnotatedClass(ExecutionStateEntity.class);
        cfg.addAnnotatedClass(TaskResultEntity.class);

        sessionFactory = cfg.buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    void shouldPersistAndReadExecutionStateEntity() {
        EntityManager em = sessionFactory.createEntityManager();
        em.getTransaction().begin();

        var entity = new ExecutionStateEntity(
                ExecutionId.generate().value(),
                "scenario-abc",
                ExecutionStatus.RUNNING,
                Map.of("PREPARATION", "COMPLETED", "INJECTION", "RUNNING"),
                Map.of("executionId", "exec-001", "scenarioId", "sc-001", "store", Map.of()),
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:05:00Z")
        );
        em.persist(entity);
        em.getTransaction().commit();
        String persistedId = entity.id();
        em.close();

        // Read back
        EntityManager readEm = sessionFactory.createEntityManager();
        ExecutionStateEntity found = readEm.find(ExecutionStateEntity.class, persistedId);
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo(persistedId);
        assertThat(found.scenarioId()).isEqualTo("scenario-abc");
        assertThat(found.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(found.phases()).containsEntry("PREPARATION", "COMPLETED");
        assertThat(found.phases()).containsEntry("INJECTION", "RUNNING");
        assertThat(found.context()).containsEntry("executionId", "exec-001");
        assertThat(found.startedAt()).isEqualTo(Instant.parse("2026-06-19T10:00:00Z"));
        assertThat(found.updatedAt()).isEqualTo(Instant.parse("2026-06-19T10:05:00Z"));
        readEm.close();
    }

    @Test
    void shouldPersistAndReadTaskResultEntity() {
        EntityManager em = sessionFactory.createEntityManager();
        em.getTransaction().begin();

        var id = new TaskResultId(
                ExecutionId.generate().value(),
                TaskId.of("task-data-prep").value(),
                AgentId.generate().value()
        );
        var entity = new TaskResultEntity(
                id,
                TaskStatus.SUCCESS,
                Map.of("rowsProcessed", 1500, "durationMs", 230),
                Instant.parse("2026-06-19T10:02:00Z")
        );
        em.persist(entity);
        em.getTransaction().commit();
        em.close();

        // Read back via JPQL
        EntityManager readEm = sessionFactory.createEntityManager();
        List<TaskResultEntity> results = readEm.createQuery(
                        "SELECT t FROM TaskResultEntity t WHERE t.id.executionId = :execId",
                        TaskResultEntity.class)
                .setParameter("execId", id.executionId())
                .getResultList();
        assertThat(results).hasSize(1);
        TaskResultEntity found = results.get(0);
        assertThat(found.executionId()).isEqualTo(id.executionId());
        assertThat(found.taskId()).isEqualTo("task-data-prep");
        assertThat(found.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(found.outputs()).containsEntry("rowsProcessed", 1500);
        assertThat(found.completedAt()).isEqualTo(Instant.parse("2026-06-19T10:02:00Z"));
        readEm.close();
    }

    @Test
    void compositeKeyShouldAllowMultipleAgentsForSameTask() {
        ExecutionId executionId = ExecutionId.generate();
        TaskId taskId = TaskId.of("task-load-inject");
        AgentId agent1 = AgentId.generate();
        AgentId agent2 = AgentId.generate();

        EntityManager em = sessionFactory.createEntityManager();
        em.getTransaction().begin();

        var result1 = new TaskResultEntity(
                new TaskResultId(executionId.value(), taskId.value(), agent1.value()),
                TaskStatus.SUCCESS,
                Map.of("throughput", 500.0),
                Instant.parse("2026-06-19T10:03:00Z")
        );
        var result2 = new TaskResultEntity(
                new TaskResultId(executionId.value(), taskId.value(), agent2.value()),
                TaskStatus.SUCCESS,
                Map.of("throughput", 480.0),
                Instant.parse("2026-06-19T10:03:05Z")
        );

        em.persist(result1);
        em.persist(result2);
        em.getTransaction().commit();
        em.close();

        // Verify both rows exist for the same (executionId, taskId)
        EntityManager readEm = sessionFactory.createEntityManager();
        List<TaskResultEntity> results = readEm.createQuery(
                        "SELECT t FROM TaskResultEntity t " +
                        "WHERE t.id.executionId = :execId AND t.id.taskId = :taskId",
                        TaskResultEntity.class)
                .setParameter("execId", executionId.value())
                .setParameter("taskId", taskId.value())
                .getResultList();

        assertThat(results).hasSize(2);
        assertThat(results.stream().map(TaskResultEntity::agentId))
                .containsExactlyInAnyOrder(agent1.value(), agent2.value());
        readEm.close();
    }

    @Test
    void shouldHandleNullCompletedAt() {
        EntityManager em = sessionFactory.createEntityManager();
        em.getTransaction().begin();

        var id = new TaskResultId(
                ExecutionId.generate().value(),
                TaskId.of("task-in-progress").value(),
                AgentId.generate().value()
        );
        var entity = new TaskResultEntity(
                id,
                TaskStatus.FAILED,
                Map.of("error", "timeout"),
                null  // not completed yet
        );
        em.persist(entity);
        em.getTransaction().commit();
        em.close();

        EntityManager readEm = sessionFactory.createEntityManager();
        TaskResultEntity found = readEm.find(TaskResultEntity.class, id);
        assertThat(found).isNotNull();
        assertThat(found.completedAt()).isNull();
        assertThat(found.status()).isEqualTo(TaskStatus.FAILED);
        readEm.close();
    }

    @Test
    void executionStatePhasesShouldSerializeAsEmptyMapByDefault() {
        EntityManager em = sessionFactory.createEntityManager();
        em.getTransaction().begin();

        var entity = new ExecutionStateEntity(
                ExecutionId.generate().value(),
                "scenario-empty",
                ExecutionStatus.STARTED,
                Map.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        em.persist(entity);
        em.getTransaction().commit();
        String persistedId = entity.id();
        em.close();

        EntityManager readEm = sessionFactory.createEntityManager();
        ExecutionStateEntity found = readEm.find(ExecutionStateEntity.class, persistedId);
        assertThat(found).isNotNull();
        assertThat(found.phases()).isEmpty();
        assertThat(found.context()).isEmpty();
        readEm.close();
    }
}
