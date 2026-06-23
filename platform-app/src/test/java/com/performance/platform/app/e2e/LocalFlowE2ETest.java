package com.performance.platform.app.e2e;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.in.CancelExecutionUseCase;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GenerateReportUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.app.api.ApiExceptionHandler;
import com.performance.platform.app.api.ScenarioController;
import com.performance.platform.assertion.file.FileAssertionExecutor;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.local.LocalExecutionEngine;
import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.engine.plan.DefaultExecutionPlanBuilder;
import com.performance.platform.engine.plan.ExecutionPlanBuilder;
import com.performance.platform.engine.retry.DefaultRetryExecutor;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.infrastructure.executor.fs.FilesystemTaskExecutor;
import com.performance.platform.infrastructure.executor.shell.ShellTaskExecutor;
import com.performance.platform.infrastructure.persistence.ExecutionStateEntity;
import com.performance.platform.infrastructure.persistence.TaskResultEntity;
import com.performance.platform.infrastructure.persistence.mapper.ExecutionStateMapper;
import com.performance.platform.infrastructure.persistence.mapper.TaskResultMapper;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.reporting.ReportEngine;
import com.performance.platform.reporting.engine.DefaultReportEngine;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.scenario.parser.YamlScenarioParser;
import com.performance.platform.scenario.usecase.DefaultScenarioParsingService;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test E2E du flux complet en mode LOCAL.
 * <p>
 * Flux teste : soumission YAML via API REST {@literal ->} parsing
 * {@literal ->} execution 3 phases (PREPARATION filesystem
 * {@literal ->} INJECTION shell {@literal ->} ASSERTION file)
 * {@literal ->} persistance PostgreSQL {@literal ->} generation de rapport
 * {@literal ->} verification du verdict.
 * <p>
 * Utilise Testcontainers PostgreSQL pour la persistance et MockMvc pour
 * l'API REST. Les composants sont cables manuellement (pas de
 * {@code @SpringBootTest} — incompatible Spring Boot 4.0.0 + JUnit 5.11.4).
 */
@DisplayName("LocalFlowE2E")
@Testcontainers
class LocalFlowE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static SessionFactory sessionFactory;
    static MockMvc mockMvc;
    static ExecutionRepository executionRepository;
    static DefaultReportEngine reportEngine;

    @BeforeAll
    static void setUp() throws Exception {
        // 1. PostgreSQL: Flyway migrations
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // 2. Hibernate SessionFactory
        var cfg = new Configuration();
        cfg.setProperty(AvailableSettings.JAKARTA_JDBC_URL, postgres.getJdbcUrl());
        cfg.setProperty(AvailableSettings.JAKARTA_JDBC_USER, postgres.getUsername());
        cfg.setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, postgres.getPassword());
        cfg.setProperty(AvailableSettings.HBM2DDL_AUTO, "validate");
        cfg.setProperty(AvailableSettings.SHOW_SQL, "false");
        cfg.addAnnotatedClass(ExecutionStateEntity.class);
        cfg.addAnnotatedClass(TaskResultEntity.class);
        sessionFactory = cfg.buildSessionFactory();

        // 3. ExecutionRepository (raw JPA, no Spring Data dependency)
        var stateMapper = new ExecutionStateMapper();
        var taskResultMapper = new TaskResultMapper();
        executionRepository = new RawJpaExecutionRepository(
                sessionFactory, stateMapper, taskResultMapper);

        // 4. Task executors (no-arg constructors, no dependencies)
        var filesystemExecutor = new FilesystemTaskExecutor();
        var shellExecutor = new ShellTaskExecutor();
        var fileAssertionExecutor = new FileAssertionExecutor();

        // 5. TaskExecutorLookup (simple map lookup)
        var lookup = new TaskExecutorLookup() {
            @Override
            public TaskExecutor findTaskExecutor(String taskName) {
                return switch (taskName) {
                    case "filesystem" -> filesystemExecutor;
                    case "shell" -> shellExecutor;
                    default -> null;
                };
            }

            @Override
            public AssertionExecutor findAssertionExecutor(String assertionName) {
                if ("file".equals(assertionName)) return fileAssertionExecutor;
                return null;
            }
        };

        // 6. Supporting components
        var planBuilder = new DefaultExecutionPlanBuilder();
        var retryExecutor = new DefaultRetryExecutor();
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher eventPublisher = publishedEvents::add;

        // 7. LocalExecutionEngine
        var engine = new LocalExecutionEngine(
                planBuilder, retryExecutor, executionRepository,
                eventPublisher, lookup);

        // 8. Scenario parsing
        var parser = new YamlScenarioParser();
        var validator = new DefaultScenarioValidator();
        var parsingUseCase = new DefaultScenarioParsingService(parser, validator);

        // 9. Use cases
        ExecuteScenarioUseCase executeUseCase = scenario -> {
            try {
                return engine.execute(scenario);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        };

        var statusUseCase = new GetExecutionStatusUseCase() {
            @Override
            public ExecutionStatus getStatus(ExecutionId id) {
                return executionRepository.findById(id)
                        .map(ExecutionState::status)
                        .orElse(ExecutionStatus.STARTED);
            }

            @Override
            public Optional<ExecutionState> getState(ExecutionId id) {
                return executionRepository.findById(id);
            }
        };

        CancelExecutionUseCase cancelUseCase = id -> {
            // no-op for E2E test
        };

        // 10. Report engine
        reportEngine = new DefaultReportEngine(executionRepository, eventPublisher);

        ReportEngine reportEnginePort = reportEngine; // same instance, interface type

        GenerateReportUseCase reportUseCase = id -> {
            ExecutionState state = executionRepository.findById(id)
                    .orElseThrow(() -> new ReportGenerationException(
                            "Execution " + id.value() + " not found", null));
            try {
                CampaignReport report = reportEnginePort.generate(state);
                return report.id();
            } catch (ReportGenerationException e) {
                throw e;
            } catch (Exception e) {
                throw new ReportGenerationException("Report generation failed", e);
            }
        };

        // 11. ScenarioController with MockMvc
        var controller = new ScenarioController(
                parsingUseCase, executeUseCase, statusUseCase,
                cancelUseCase, reportUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    @DisplayName("should complete full E2E flow: submit YAML -> execute 3 phases -> persist COMPLETED -> generate report with verdict")
    void shouldCompleteFullE2EFlow() throws Exception {
        // ---- Phase 1: Submit YAML via API ----

        var yaml = Files.readString(
                Path.of("src/test/resources/scenarios/e2e-local.yaml"));

        String responseJson = mockMvc.perform(post("/api/v1/scenarios")
                        .contentType("text/plain")
                        .content(yaml))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").exists())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String executionId = extractJsonString(responseJson, "executionId");
        assertThat(executionId).isNotBlank();

        // ---- Phase 2: Poll until execution completes ----

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    String statusJson = mockMvc
                            .perform(get("/api/v1/executions/" + executionId))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    String status = extractJsonString(statusJson, "status");
                    return "COMPLETED".equals(status) || "FAILED".equals(status);
                });

        // ---- Phase 3: Verify execution status (3 phases all COMPLETED) ----

        mockMvc.perform(get("/api/v1/executions/" + executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(executionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.phaseStatuses.PREPARATION").value("COMPLETED"))
                .andExpect(jsonPath("$.phaseStatuses.INJECTION").value("COMPLETED"))
                .andExpect(jsonPath("$.phaseStatuses.ASSERTION").value("COMPLETED"));

        // ---- Phase 4: Generate report via API ----

        String reportResponse = mockMvc
                .perform(get("/api/v1/executions/" + executionId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String reportId = extractJsonString(reportResponse, "reportId");
        assertThat(reportId).isNotBlank();

        // ---- Phase 5: Verify persisted ExecutionState ----

        var execId = ExecutionId.of(executionId);
        Optional<ExecutionState> persisted = executionRepository.findById(execId);
        assertThat(persisted).isPresent();
        ExecutionState state = persisted.get();
        assertThat(state.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(state.scenarioId().value()).isEqualTo("e2e-local");
        assertThat(state.phaseStatuses().get(Phase.PREPARATION))
                .isEqualTo(PhaseStatus.COMPLETED);
        assertThat(state.phaseStatuses().get(Phase.INJECTION))
                .isEqualTo(PhaseStatus.COMPLETED);
        assertThat(state.phaseStatuses().get(Phase.ASSERTION))
                .isEqualTo(PhaseStatus.COMPLETED);

        // ---- Phase 6: Verify report contains a verdict ----

        CampaignReport report = reportEngine.generate(state);
        assertThat(report.verdict()).isNotNull();
        assertThat(report.verdict()).isIn(Verdict.SUCCESS, Verdict.WARNING);
        assertThat(report.id()).isNotNull();
        // API call and direct engine call generate different ReportIds
        // (each generate() creates a new UUID via ReportId.generate())
        assertThat(reportId).isNotBlank();
    }

    // ---- Helpers ----

    /**
     * Extrait une valeur de champ depuis un JSON simple (format {@code {"key":"value"}}).
     */
    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            pattern = "\"" + key + "\": \"";
            start = json.indexOf(pattern);
        }
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf('"', start);
        if (end < 0) return json.substring(start);
        return json.substring(start, end);
    }

    // ---- Raw JPA ExecutionRepository (no Spring Data dependency) ----

    /**
     * Implementation brute de {@link ExecutionRepository} utilisant JPA/Hibernate
     * directement, sans Spring Data. Evite la dependance au contexte Spring pour
     * la creation des proxies de repository Spring Data.
     */
    static class RawJpaExecutionRepository implements ExecutionRepository {

        private final EntityManagerFactory emf;
        private final ExecutionStateMapper stateMapper;
        private final TaskResultMapper taskResultMapper;

        RawJpaExecutionRepository(EntityManagerFactory emf,
                                  ExecutionStateMapper stateMapper,
                                  TaskResultMapper taskResultMapper) {
            this.emf = emf;
            this.stateMapper = stateMapper;
            this.taskResultMapper = taskResultMapper;
        }

        @Override
        public void save(ExecutionState state) {
            executeInTransaction(em -> {
                ExecutionStateEntity entity = stateMapper.toEntity(state);
                em.merge(entity);
            });
        }

        @Override
        public Optional<ExecutionState> findById(ExecutionId id) {
            EntityManager em = emf.createEntityManager();
            try {
                ExecutionStateEntity entity = em.find(
                        ExecutionStateEntity.class, id.value());
                if (entity == null) return Optional.empty();
                return Optional.of(stateMapper.toDomain(entity));
            } finally {
                em.close();
            }
        }

        @Override
        public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {
            executeInTransaction(em -> {
                ExecutionStateEntity entity = em.find(
                        ExecutionStateEntity.class, id.value());
                if (entity == null) return;

                // Immutable entity — create new with updated phases
                java.util.LinkedHashMap<String, String> phases =
                        new java.util.LinkedHashMap<>(entity.phases());
                phases.put(phase.name(), status.name());

                var updated = new ExecutionStateEntity(
                        entity.id(), entity.scenarioId(), entity.status(),
                        phases, entity.context(),
                        entity.startedAt(), java.time.Instant.now());
                em.merge(updated);
            });
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId,
                                    AgentId agentId, TaskResult result) {
            executeInTransaction(em -> {
                TaskResultEntity entity = taskResultMapper.toEntity(
                        id, taskId, agentId, result);
                em.merge(entity);
            });
        }

        @Override
        public java.util.Map<AgentId, TaskResult> getTaskResults(
                ExecutionId executionId, TaskId taskId) {
            EntityManager em = emf.createEntityManager();
            try {
                var results = em.createQuery(
                                "SELECT t FROM TaskResultEntity t "
                                + "WHERE t.id.executionId = :execId "
                                + "AND t.id.taskId = :taskId",
                                TaskResultEntity.class)
                        .setParameter("execId", executionId.value())
                        .setParameter("taskId", taskId.value())
                        .getResultList();
                java.util.Map<AgentId, TaskResult> map =
                        new java.util.LinkedHashMap<>();
                for (TaskResultEntity entity : results) {
                    TaskResult domain = taskResultMapper.toDomain(entity);
                    map.put(new AgentId(entity.id().agentId()), domain);
                }
                return map;
            } finally {
                em.close();
            }
        }

        @Override
        public java.util.List<ExecutionState> findAll(int limit) {
            EntityManager em = emf.createEntityManager();
            try {
                return em.createQuery(
                                "SELECT e FROM ExecutionStateEntity e ORDER BY e.startedAt DESC",
                                ExecutionStateEntity.class)
                        .setMaxResults(limit)
                        .getResultList()
                        .stream()
                        .map(stateMapper::toDomain)
                        .collect(java.util.stream.Collectors.toList());
            } finally {
                em.close();
            }
        }

        @Override
        public void deleteById(ExecutionId id) {
            executeInTransaction(em -> {
                em.createQuery(
                        "DELETE FROM TaskResultEntity t WHERE t.id.executionId = :execId")
                        .setParameter("execId", id.value())
                        .executeUpdate();
                ExecutionStateEntity entity = em.find(
                        ExecutionStateEntity.class, id.value());
                if (entity != null) {
                    em.remove(entity);
                }
            });
        }

        private void executeInTransaction(
                java.util.function.Consumer<EntityManager> action) {
            EntityManager em = emf.createEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                action.accept(em);
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            } finally {
                em.close();
            }
        }
    }
}
