package com.performance.platform.e2e;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.in.CancelExecutionUseCase;
import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GenerateReportUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.agent.local.LocalAgent;
import com.performance.platform.app.api.AgentController;
import com.performance.platform.app.api.ApiExceptionHandler;
import com.performance.platform.app.api.ExecutionController;
import com.performance.platform.app.api.ReportController;
import com.performance.platform.app.api.ScenarioController;
import com.performance.platform.app.api.ScenarioUploadController;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.local.LocalExecutionEngine;
import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.engine.plan.DefaultExecutionPlanBuilder;
import com.performance.platform.engine.retry.DefaultRetryExecutor;
import com.performance.platform.infrastructure.persistence.ExecutionStateEntity;
import com.performance.platform.infrastructure.persistence.TaskResultEntity;
import com.performance.platform.infrastructure.persistence.mapper.ExecutionStateMapper;
import com.performance.platform.infrastructure.persistence.mapper.TaskResultMapper;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.engine.DefaultReportEngine;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.output.ReportFileWriter;
import com.performance.platform.reporting.output.ReportProperties;
import com.performance.platform.reporting.render.HtmlReportRenderer;
import com.performance.platform.reporting.render.JsonReportRenderer;
import com.performance.platform.scenario.parser.YamlScenarioParser;
import com.performance.platform.scenario.usecase.DefaultScenarioParsingService;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E Testcontainers test covering Web UI / API endpoints.
 * <p>
 * Verifies:
 * <ol>
 *   <li>{@code GET /executions} — list with progress</li>
 *   <li>{@code GET /executions/{id}/tasks} — task summaries</li>
 *   <li>{@code DELETE /executions/{id}} — 204 on completed execution</li>
 *   <li>{@code GET /agents} — agent list</li>
 *   <li>{@code GET /executions/{id}/report?format=html} — 404 before generation, 200 after</li>
 *   <li>{@code POST /scenarios/upload} — 202 on valid upload, 400 on invalid</li>
 * </ol>
 * <p>
 * Uses Testcontainers PostgreSQL for persistence and MockMvc for the REST API.
 * All components are wired manually (no {@code @SpringBootTest}).
 */
@DisplayName("WebUiApiE2E")
@Testcontainers
@Tag("integration")
class WebUiApiE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static SessionFactory sessionFactory;
    static MockMvc mockMvc;
    static ExecutionRepository executionRepository;
    static DefaultReportEngine reportEngine;
    static ReportFileWriter reportFileWriter;
    static Path outputDir;
    static InMemoryExecutionTransport transport;
    static LocalAgent localAgent;
    static StubAgentRegistry agentRegistry;
    static ExecutionProgressCalculator progressCalculator;

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

        // 3. ExecutionRepository (raw JPA)
        var stateMapper = new ExecutionStateMapper();
        var taskResultMapper = new TaskResultMapper();
        executionRepository = new RawJpaExecutionRepository(
                sessionFactory, stateMapper, taskResultMapper);
        progressCalculator = new ExecutionProgressCalculator();

        // 4. Stub TaskExecutors
        var stubExecutor = new StubTaskExecutor("stub-task",
                Map.of("result", "ok"));
        var taskExecutorList = List.<TaskExecutor>of(stubExecutor);

        // 5. Stub AssertionExecutor
        var stubAssertion = new StubAssertionExecutor("stub-assertion");

        // 6. TaskExecutorLookup
        var lookup = new TaskExecutorLookup() {
            @Override
            public TaskExecutor findTaskExecutor(String taskName) {
                return "stub-task".equals(taskName) ? stubExecutor : null;
            }

            @Override
            public AssertionExecutor findAssertionExecutor(String assertionName) {
                return "stub-assertion".equals(assertionName) ? stubAssertion : null;
            }
        };

        // 7. Supporting components
        var planBuilder = new DefaultExecutionPlanBuilder();
        var retryExecutor = new DefaultRetryExecutor();
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher eventPublisher = publishedEvents::add;

        // 8. LocalExecutionEngine
        var engine = new LocalExecutionEngine(
                planBuilder, retryExecutor, executionRepository,
                eventPublisher, lookup);

        // 9. Scenario parsing
        var parser = new YamlScenarioParser();
        var validator = new DefaultScenarioValidator();
        var parsingUseCase = new DefaultScenarioParsingService(parser, validator);

        // 10. Use cases
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

        CancelExecutionUseCase cancelUseCase = id -> { /* no-op */ };

        ListExecutionsUseCase listUseCase = limit -> {
            int effectiveLimit = limit > 0 ? limit : 50;
            return executionRepository.findAll(effectiveLimit);
        };

        DeleteExecutionUseCase deleteUseCase = id -> {
            var state = executionRepository.findById(id);
            if (state.isEmpty()) {
                throw new ExecutionException("Execution not found: " + id.value());
            }
            var status = state.get().status();
            if (status == ExecutionStatus.STARTED || status == ExecutionStatus.RUNNING) {
                throw new com.performance.platform.application.usecase
                        .ExecutionNotDeletableException(id, status);
            }
            executionRepository.deleteById(id);
        };

        // 11. Report engine
        reportEngine = new DefaultReportEngine(executionRepository, eventPublisher);

        GenerateReportUseCase reportUseCase = id -> {
            ExecutionState state = executionRepository.findById(id)
                    .orElseThrow(() -> new ReportGenerationException(
                            "Execution " + id.value() + " not found", null));
            try {
                CampaignReport report = reportEngine.generate(state);
                return report.id();
            } catch (ReportGenerationException e) {
                throw e;
            } catch (Exception e) {
                throw new ReportGenerationException("Report generation failed", e);
            }
        };

        // 11b. Report file output
        outputDir = Files.createTempDirectory("e2e-reports-");
        var htmlRenderer = new HtmlReportRenderer();
        var jsonRenderer = new JsonReportRenderer();
        var reportProps = new ReportProperties(
                outputDir.toAbsolutePath().toString(),
                List.of(ReportFormat.HTML, ReportFormat.JSON));
        reportFileWriter = new ReportFileWriter(
                List.<ReportRenderer>of(htmlRenderer, jsonRenderer), reportProps);

        // 12. Agent registry stub
        agentRegistry = new StubAgentRegistry();

        // Store an agent in the registry for GET /agents
        var agentDescriptor = new AgentDescriptor(
                AgentId.generate(), "test-agent", "localhost", 8080, null,
                Set.of("stub-task"),
                new AgentCapabilities(10, "1.0.0"),
                AgentState.IDLE, Instant.now(), Instant.now(), Duration.ofMinutes(5));
        agentRegistry.addAgent(agentDescriptor);

        // 13. Controllers
        var scenarioController = new ScenarioController(
                parsingUseCase, executeUseCase, statusUseCase,
                cancelUseCase, executionRepository, progressCalculator);

        var reportController = new ReportController(reportProps);

        var uploadController = new ScenarioUploadController(
                parsingUseCase, executeUseCase);

        var executionController = new ExecutionController(
                listUseCase, deleteUseCase, executionRepository, progressCalculator);

        var agentController = new AgentController(agentRegistry);

        // 14. MockMvc with all controllers
        mockMvc = MockMvcBuilders.standaloneSetup(
                        scenarioController,
                        reportController,
                        uploadController,
                        executionController,
                        agentController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        // 15. Transport InMemory + LocalAgent
        transport = new InMemoryExecutionTransport();
        transport.connect();

        var agentDesc = new AgentDescriptor(
                AgentId.generate(), "local-agent", "localhost", 8080, null,
                Set.of("stub-task"), new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

        localAgent = new LocalAgent(transport, agentDesc,
                Duration.ofMinutes(5), taskExecutorList, List.of());
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (transport != null) {
            transport.disconnect();
        }
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (outputDir != null && Files.exists(outputDir)) {
            try (var stream = Files.walk(outputDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) { }
                        });
            }
        }
    }

    @BeforeEach
    void cleanDb() {
        // Clean database between test methods for isolation
        executeInTransaction(em -> {
            em.createQuery("DELETE FROM TaskResultEntity").executeUpdate();
            em.createQuery("DELETE FROM ExecutionStateEntity").executeUpdate();
        });
    }

    // =====================================================================
    // Nested tests
    // =====================================================================

    @Nested
    @DisplayName("GET /executions")
    class ListExecutions {

        @Test
        @DisplayName("should return empty list when no executions exist")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/executions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should return executions with progress after submission")
        void shouldReturnExecutionsWithProgress() throws Exception {
            // Submit a scenario
            var yaml = minimalYaml();
            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType("text/plain")
                            .content(yaml))
                    .andExpect(status().isAccepted());

            // Wait for completion
            await().atMost(Duration.ofSeconds(15))
                    .pollInterval(Duration.ofMillis(300))
                    .until(() -> {
                        var all = executionRepository.findAll(10);
                        return !all.isEmpty()
                                && (all.get(0).status() == ExecutionStatus.COMPLETED
                                || all.get(0).status() == ExecutionStatus.FAILED);
                    });

            // List executions
            mockMvc.perform(get("/api/v1/executions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].executionId").exists())
                    .andExpect(jsonPath("$[0].status").exists())
                    .andExpect(jsonPath("$[0].progress").exists())
                    .andExpect(jsonPath("$[0].progress.total").isNumber())
                    .andExpect(jsonPath("$[0].startedAt").exists());
        }
    }

    @Nested
    @DisplayName("GET /executions/{id}/tasks")
    class ListTasks {

        @Test
        @DisplayName("should return task list after execution completes")
        void shouldReturnTaskList() throws Exception {
            var yaml = minimalYaml();
            var responseJson = mockMvc.perform(post("/api/v1/scenarios")
                            .contentType("text/plain")
                            .content(yaml))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String executionId = extractJsonString(responseJson, "executionId");
            assertThat(executionId).isNotBlank();

            await().atMost(Duration.ofSeconds(15))
                    .pollInterval(Duration.ofMillis(300))
                    .until(() -> {
                        var state = executionRepository.findById(ExecutionId.of(executionId));
                        return state.isPresent()
                                && (state.get().status() == ExecutionStatus.COMPLETED
                                || state.get().status() == ExecutionStatus.FAILED);
                    });

            mockMvc.perform(get("/api/v1/executions/" + executionId + "/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(executionId))
                    .andExpect(jsonPath("$.total").isNumber())
                    .andExpect(jsonPath("$.tasks").isArray());
        }
    }

    @Nested
    @DisplayName("DELETE /executions/{id}")
    class DeleteExecution {

        @Test
        @DisplayName("should return 204 when deleting a completed execution")
        void shouldReturn204OnDelete() throws Exception {
            var yaml = minimalYaml();
            var responseJson = mockMvc.perform(post("/api/v1/scenarios")
                            .contentType("text/plain")
                            .content(yaml))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String executionId = extractJsonString(responseJson, "executionId");

            await().atMost(Duration.ofSeconds(15))
                    .pollInterval(Duration.ofMillis(300))
                    .until(() -> {
                        var state = executionRepository.findById(ExecutionId.of(executionId));
                        return state.isPresent()
                                && (state.get().status() == ExecutionStatus.COMPLETED
                                || state.get().status() == ExecutionStatus.FAILED);
                    });

            mockMvc.perform(delete("/api/v1/executions/" + executionId))
                    .andExpect(status().isNoContent());

            // Verify execution is gone
            var deleted = executionRepository.findById(ExecutionId.of(executionId));
            assertThat(deleted).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /agents")
    class ListAgents {

        @Test
        @DisplayName("should return registered agents")
        void shouldReturnRegisteredAgents() throws Exception {
            mockMvc.perform(get("/api/v1/agents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].agentId").exists())
                    .andExpect(jsonPath("$[0].name").exists())
                    .andExpect(jsonPath("$[0].state").exists())
                    .andExpect(jsonPath("$[0].supportedTasks").isArray())
                    .andExpect(jsonPath("$[0].lastHeartbeatAt").exists());
        }
    }

    @Nested
    @DisplayName("GET /executions/{id}/report")
    class ViewReport {

        @Test
        @DisplayName("should return 404 before report generation, then 200 after")
        void shouldReturn404Then200AfterGeneration() throws Exception {
            var yaml = minimalYaml();
            var responseJson = mockMvc.perform(post("/api/v1/scenarios")
                            .contentType("text/plain")
                            .content(yaml))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String executionId = extractJsonString(responseJson, "executionId");

            // Wait for completion
            await().atMost(Duration.ofSeconds(15))
                    .pollInterval(Duration.ofMillis(300))
                    .until(() -> {
                        var state = executionRepository.findById(ExecutionId.of(executionId));
                        return state.isPresent()
                                && (state.get().status() == ExecutionStatus.COMPLETED
                                || state.get().status() == ExecutionStatus.FAILED);
                    });

            // Before generation: report should not exist (404)
            mockMvc.perform(get("/api/v1/executions/" + executionId + "/report")
                            .param("format", "html"))
                    .andExpect(status().isNotFound());

            // Generate report programmatically (simulates automatic generation at end of lifecycle)
            var state = executionRepository.findById(ExecutionId.of(executionId));
            assertThat(state).isPresent();
            CampaignReport report = reportEngine.generate(state.get());
            assertThat(report).isNotNull();
            reportFileWriter.write(ExecutionId.of(executionId), report);

            // After generation: report should be available (200 with HTML)
            mockMvc.perform(get("/api/v1/executions/" + executionId + "/report")
                            .param("format", "html"))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String contentType = result.getResponse().getContentType();
                        assertThat(contentType).contains("text/html");
                    });

            // JSON format should also be available
            mockMvc.perform(get("/api/v1/executions/" + executionId + "/report")
                            .param("format", "json"))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String contentType = result.getResponse().getContentType();
                        assertThat(contentType).contains("application/json");
                    });
        }
    }

    @Nested
    @DisplayName("POST /scenarios/upload")
    class UploadScenario {

        @Test
        @DisplayName("should return 202 when uploading a valid YAML via form param")
        void shouldReturn202OnValidUpload() throws Exception {
            var yaml = minimalYaml();

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .param("yaml", yaml)
                            .contentType("application/x-www-form-urlencoded"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").exists())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("should return 400 when neither file nor yaml is provided")
        void shouldReturn400OnMissingInput() throws Exception {
            mockMvc.perform(post("/api/v1/scenarios/upload"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details").isArray());
        }

        @Test
        @DisplayName("should return 400 when YAML content is empty")
        void shouldReturn400OnEmptyContent() throws Exception {
            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .param("yaml", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when YAML fails validation (missing id)")
        void shouldReturn400OnInvalidYaml() throws Exception {
            // YAML that parses but fails scenario validation (missing required "id")
            var invalidYaml = """
                    scenario:
                      name: "Missing ID"
                      version: "1.0.0"
                      execution:
                        mode: LOCAL
                      steps:
                        - id: step1
                          task: stub-task
                          phase: INJECTION
                          parameters:
                            action: doSomething
                          timeout: 30s
                    """;
            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .param("yaml", invalidYaml))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_PARSING_FAILED"));
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String minimalYaml() {
        return """
                scenario:
                  id: e2e-minimal
                  name: "E2E Minimal"
                  version: "1.0.0"
                  execution:
                    mode: LOCAL
                  steps:
                    - id: inject-step
                      task: stub-task
                      phase: INJECTION
                      parameters:
                        action: doSomething
                      timeout: 30s
                    - id: assert-step
                      task: stub-assertion
                      phase: ASSERTION
                      dependsOn: [inject-step]
                      parameters:
                        metric: result
                        operator: EQ
                        value: 1
                      timeout: 30s
                """;
    }

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

    private void executeInTransaction(
            java.util.function.Consumer<EntityManager> action) {
        EntityManager em = sessionFactory.createEntityManager();
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

    // =====================================================================
    // Stubs
    // =====================================================================

    /** Stub TaskExecutor that always returns SUCCESS. */
    static class StubTaskExecutor implements TaskExecutor {
        private final String taskName;
        private final Map<String, Object> outputs;

        StubTaskExecutor(String taskName, Map<String, Object> outputs) {
            this.taskName = taskName;
            this.outputs = Map.copyOf(outputs);
        }

        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), taskName, Duration.ofMillis(5), outputs);
        }

        @Override
        public String getSupportedTaskName() {
            return taskName;
        }
    }

    /** Stub AssertionExecutor that always returns PASSED. */
    static class StubAssertionExecutor implements AssertionExecutor {
        private final String assertionName;

        StubAssertionExecutor(String assertionName) {
            this.assertionName = assertionName;
        }

        @Override
        public com.performance.platform.domain.assertion.AssertionResult evaluate(
                ExecutionContext context, StepDefinition step) {
            var evidence = new com.performance.platform.domain.assertion.Evidence(
                    1L, 1L,
                    com.performance.platform.domain.assertion.AssertionOperator.EQ,
                    null, Map.of("stub", true));
            return new com.performance.platform.domain.assertion.AssertionResult(
                    step.id(),
                    com.performance.platform.domain.assertion.AssertionStatus.PASSED,
                    "stub assertion passed",
                    evidence,
                    Duration.ofMillis(1),
                    Instant.now());
        }

        @Override
        public String getSupportedAssertionName() {
            return assertionName;
        }
    }

    /** In-memory AgentRegistryPort for testing. */
    static class StubAgentRegistry implements AgentRegistryPort {
        private final List<AgentDescriptor> agents = new ArrayList<>();

        void addAgent(AgentDescriptor agent) {
            agents.add(agent);
        }

        @Override
        public void onAgentRegistered(AgentDescriptor descriptor) {
            agents.removeIf(a -> a.id().equals(descriptor.id()));
            agents.add(descriptor);
        }

        @Override
        public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) { /* no-op */ }

        @Override
        public void onAgentExpired(AgentId agentId) {
            agents.removeIf(a -> a.id().equals(agentId));
        }

        @Override
        public void onAgentDeregistered(AgentId agentId) {
            agents.removeIf(a -> a.id().equals(agentId));
        }

        @Override
        public List<AgentDescriptor> findByTaskName(String taskName) {
            return agents.stream()
                    .filter(a -> a.supportedTaskNames().contains(taskName))
                    .toList();
        }

        @Override
        public boolean hasAgentFor(String taskName) {
            return agents.stream()
                    .anyMatch(a -> a.supportedTaskNames().contains(taskName));
        }

        @Override
        public Optional<AgentDescriptor> findById(AgentId agentId) {
            return agents.stream()
                    .filter(a -> a.id().equals(agentId))
                    .findFirst();
        }

        @Override
        public List<AgentDescriptor> findAll() {
            return List.copyOf(agents);
        }
    }

    // =====================================================================
    // Raw JPA ExecutionRepository (no Spring Data dependency)
    // =====================================================================

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
        public void updatePhase(ExecutionId id, Phase phase,
                                com.performance.platform.domain.execution.PhaseStatus status) {
            executeInTransaction(em -> {
                ExecutionStateEntity entity = em.find(
                        ExecutionStateEntity.class, id.value());
                if (entity == null) return;

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
        public void saveTaskResult(ExecutionId id,
                                   com.performance.platform.domain.id.TaskId taskId,
                                   AgentId agentId, TaskResult result) {
            executeInTransaction(em -> {
                var entity = taskResultMapper.toEntity(id, taskId, agentId, result);
                em.merge(entity);
            });
        }

        @Override
        public java.util.Map<AgentId, TaskResult> getTaskResults(
                ExecutionId executionId,
                com.performance.platform.domain.id.TaskId taskId) {
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
                java.util.Map<AgentId, TaskResult> map = new java.util.LinkedHashMap<>();
                for (var entity : results) {
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

        @Override
        public java.util.Map<com.performance.platform.domain.id.TaskId,
                java.util.Map<com.performance.platform.domain.id.AgentId,
                        com.performance.platform.domain.task.TaskResult>>
                findAllTaskResults(ExecutionId id) {
            EntityManager em = emf.createEntityManager();
            try {
                var results = em.createQuery(
                                "SELECT t FROM TaskResultEntity t "
                                + "WHERE t.id.executionId = :execId",
                                TaskResultEntity.class)
                        .setParameter("execId", id.value())
                        .getResultList();
                java.util.Map<com.performance.platform.domain.id.TaskId,
                        java.util.Map<com.performance.platform.domain.id.AgentId,
                                com.performance.platform.domain.task.TaskResult>> map =
                        new java.util.LinkedHashMap<>();
                for (var entity : results) {
                    var taskResult = taskResultMapper.toDomain(entity);
                    map.computeIfAbsent(
                            new com.performance.platform.domain.id.TaskId(entity.id().taskId()),
                            k -> new java.util.LinkedHashMap<>())
                            .put(new AgentId(entity.id().agentId()), taskResult);
                }
                return map;
            } finally {
                em.close();
            }
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
