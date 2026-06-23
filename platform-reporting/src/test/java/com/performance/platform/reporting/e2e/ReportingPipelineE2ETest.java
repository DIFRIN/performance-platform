package com.performance.platform.reporting.e2e;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.engine.DefaultReportEngine;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.output.ReportFileWriter;
import com.performance.platform.reporting.output.ReportProperties;
import com.performance.platform.reporting.render.HtmlReportRenderer;
import com.performance.platform.reporting.render.JsonReportRenderer;
import com.performance.platform.reporting.render.PdfReportRenderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'integration end-to-end du pipeline de reporting complet.
 * <p>
 * Flux teste : ExecutionState -> DefaultReportEngine.generate() -> CampaignReport
 * -> HtmlReportRenderer / JsonReportRenderer / PdfReportRenderer -> ReportFileWriter
 * -> fichiers sur disque.
 * <p>
 * Aucune infrastructure externe requise : le module reporting est purement
 * computationnel + I/O fichiers.
 */
@DisplayName("Reporting Pipeline E2E")
class ReportingPipelineE2ETest {

    // ── Stubs ──

    static class StubExecutionRepository implements ExecutionRepository {
        private final ConcurrentHashMap<ExecutionId, ExecutionState> states = new ConcurrentHashMap<>();

        void put(ExecutionState state) {
            states.put(state.id(), state);
        }

        @Override
        public void save(ExecutionState state) {
            states.put(state.id(), state);
        }

        @Override
        public Optional<ExecutionState> findById(ExecutionId id) {
            return Optional.ofNullable(states.get(id));
        }

        @Override
        public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
        }

        @Override
        public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
            return Map.of();
        }

        @Override
        public List<ExecutionState> findAll(int limit) {
            return List.of();
        }

        @Override
        public void deleteById(ExecutionId id) { /* no-op */ }
    }

    static class StubEventPublisher implements ApplicationEventPublisher {
        final List<Object> published = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            published.add(event);
        }
    }

    // ── State ──

    @TempDir
    Path tempDir;

    private StubExecutionRepository repository;
    private StubEventPublisher eventPublisher;
    private DefaultReportEngine engine;
    private HtmlReportRenderer htmlRenderer;
    private JsonReportRenderer jsonRenderer;
    private PdfReportRenderer pdfRenderer;
    private ExecutionId executionId;
    private ScenarioId scenarioId;
    private Instant now;

    @BeforeEach
    void setUp() {
        repository = new StubExecutionRepository();
        eventPublisher = new StubEventPublisher();
        engine = new DefaultReportEngine(repository, eventPublisher);
        htmlRenderer = new HtmlReportRenderer();
        jsonRenderer = new JsonReportRenderer();
        pdfRenderer = new PdfReportRenderer(htmlRenderer);
        executionId = ExecutionId.of("e2e-exec-001");
        scenarioId = ScenarioId.of("e2e-scenario");
        now = Instant.now();
    }

    @AfterEach
    void cleanup() throws IOException {
        // Clean up any "reports/" directory created by default output tests
        var defaultReports = Path.of("reports");
        if (Files.exists(defaultReports)) {
            deleteRecursively(defaultReports);
        }
    }

    // ══════════════════════════════════════════════
    // E2E: Scenario simple (preparation only)
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Simple scenario (preparation only)")
    class SimpleScenario {

        @Test
        @DisplayName("should execute full pipeline: state -> report -> 3 formats on disk")
        void shouldExecuteFullPipeline() throws IOException {
            // Given: an ExecutionState with one preparation task
            var taskId = TaskId.of("prep-1");
            var result = TaskResult.success(taskId, "database-purge",
                    Duration.ofSeconds(3), Map.of("rows_deleted", 500));
            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(5));

            // When: generate report
            CampaignReport report = engine.generate(state);

            // Then: report is correct
            assertNotNull(report);
            assertEquals(scenarioId, report.scenarioId());
            assertEquals(Verdict.SUCCESS, report.verdict());
            assertEquals(1, report.preparationResults().size());
            assertEquals("database-purge", report.preparationResults().get(0).taskName());
            assertEquals(1, report.executionSummary().totalTasks());

            // When: render to all 3 formats
            byte[] htmlBytes = htmlRenderer.render(report);
            byte[] jsonBytes = jsonRenderer.render(report);
            byte[] pdfBytes = pdfRenderer.render(report);

            // Then: all formats are valid
            assertTrue(htmlBytes.length > 0);
            assertTrue(jsonBytes.length > 0);
            assertTrue(pdfBytes.length > 4);

            // When: write to disk
            var props = new ReportProperties(tempDir.toString(),
                    List.of(ReportFormat.HTML, ReportFormat.JSON, ReportFormat.PDF));
            var writer = new ReportFileWriter(
                    List.of(htmlRenderer, jsonRenderer, pdfRenderer), props);
            Path outputDir = writer.write(executionId, report);

            // Then: files exist on disk with expected structure
            assertTrue(Files.exists(outputDir));
            assertEquals(tempDir.resolve("e2e-exec-001"), outputDir);
            assertTrue(Files.exists(outputDir.resolve("campaign.html")));
            assertTrue(Files.exists(outputDir.resolve("campaign.json")));
            assertTrue(Files.exists(outputDir.resolve("campaign.pdf")));

            // Then: HTML file contains report data
            var htmlContent = Files.readString(outputDir.resolve("campaign.html"));
            assertTrue(htmlContent.contains("database-purge"));
            assertTrue(htmlContent.contains("SUCCESS"));

            // Then: JSON file contains report data
            var jsonContent = Files.readString(outputDir.resolve("campaign.json"));
            assertTrue(jsonContent.contains("database-purge"));
            assertTrue(jsonContent.contains("SUCCESS"));

            // Then: PDF starts with magic bytes
            byte[] pdfOnDisk = Files.readAllBytes(outputDir.resolve("campaign.pdf"));
            byte[] pdfMagic = "%PDF".getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < pdfMagic.length; i++) {
                assertEquals(pdfMagic[i], pdfOnDisk[i], "PDF magic mismatch at byte " + i);
            }
        }

        @Test
        @DisplayName("should generate SUCCESS verdict when no assertions present")
        void shouldGenerateSuccessWhenNoAssertions() {
            var taskId = TaskId.of("prep-1");
            var result = TaskResult.success(taskId, "setup", Duration.ofSeconds(1), Map.of());
            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(2));

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.SUCCESS, report.verdict());
            assertEquals("0 passed, 0 failed, 0 error, 0 skipped", report.verdictReason());
        }
    }

    // ══════════════════════════════════════════════
    // E2E: Scenario multi-phase (prep + injection + assertion)
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-phase scenario")
    class MultiPhaseScenario {

        @Test
        @DisplayName("should handle preparation + injection + assertion phases with correct classification")
        void shouldHandleAllThreePhases() throws IOException {
            // Given: 2 prep tasks, 1 injection, 2 assertions
            var prepA = TaskId.of("prep-a");
            var prepResultA = TaskResult.success(prepA, "db-setup",
                    Duration.ofSeconds(1), Map.of("rows", 42));

            var prepB = TaskId.of("prep-b");
            var prepResultB = TaskResult.success(prepB, "kafka-monitor",
                    Duration.ofMillis(500), Map.of());

            var injId = TaskId.of("inj-1");
            var injResult = new InjectionResult(
                    injId, "com.example.ApiSimulation", Duration.ofSeconds(15),
                    5000, 4950, 50, 1.0, 333.3,
                    20, 35, 50, 60, 80, 200, 5, 45.0,
                    tempDir.resolve("gatling-src"), Map.of("scenario", "api-test"));
            var injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(15), Map.of("injectionResult", injResult));

            var assertA = TaskId.of("assert-a");
            var assertResultA = new AssertionResult(
                    assertA, AssertionStatus.PASSED, "p95 < 100ms",
                    null, Duration.ofMillis(10), now);
            var assertTaskResultA = TaskResult.success(assertA, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", assertResultA));

            var assertB = TaskId.of("assert-b");
            var assertResultB = new AssertionResult(
                    assertB, AssertionStatus.PASSED, "errorRate < 2%",
                    null, Duration.ofMillis(5), now);
            var assertTaskResultB = TaskResult.success(assertB, "gatling-metric",
                    Duration.ofMillis(5), Map.of("assertionResult", assertResultB));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepA.value(), "agent-1", prepResultA)
                    .with(prepB.value(), "agent-1", prepResultB)
                    .with(injId.value(), "agent-1", injTaskResult)
                    .with(assertA.value(), "agent-1", assertTaskResultA)
                    .with(assertB.value(), "agent-1", assertTaskResultB);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(20));

            // When
            CampaignReport report = engine.generate(state);

            // Then: Phase classification correct
            assertEquals(2, report.preparationResults().size());
            assertEquals(1, report.injectionResults().size());
            assertEquals(2, report.assertionResults().size());

            // Then: Execution summary correct
            var summary = report.executionSummary();
            assertEquals(5, summary.totalTasks());
            assertEquals(5, summary.successfulTasks());
            assertEquals(0, summary.failedTasks());
            assertEquals(0, summary.skippedTasks());
            // Duration must show prep, injection, assertion separately
            assertFalse(summary.preparationDuration().isNegative());
            assertFalse(summary.injectionDuration().isNegative());
            assertFalse(summary.assertionDuration().isNegative());

            // Then: Verdict is SUCCESS (all assertions passed)
            assertEquals(Verdict.SUCCESS, report.verdict());

            // Then: Render to all formats
            byte[] html = htmlRenderer.render(report);
            byte[] json = jsonRenderer.render(report);
            byte[] pdf = pdfRenderer.render(report);

            assertTrue(html.length > 0);
            assertTrue(json.length > 0);
            assertTrue(pdf.length > 4);

            // Then: Write to disk and verify all 3 files
            var props = new ReportProperties(tempDir.toString(),
                    List.of(ReportFormat.HTML, ReportFormat.JSON, ReportFormat.PDF));
            var writer = new ReportFileWriter(
                    List.of(htmlRenderer, jsonRenderer, pdfRenderer), props);
            Path outputDir = writer.write(executionId, report);

            assertEquals(3, countFilesWithPrefix(outputDir, "campaign."),
                    "All 3 format files must exist");
        }

        @Test
        @DisplayName("should produce WARNING verdict when any assertion FAILED")
        void shouldProduceWarningVerdict() {
            var assertId = TaskId.of("assert-1");
            var assertResult = new AssertionResult(
                    assertId, AssertionStatus.FAILED, "p99 > threshold",
                    null, Duration.ofMillis(10), now);
            var assertTaskResult = TaskResult.success(assertId, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", assertResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertId.value(), "agent-1", assertTaskResult);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1));

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.WARNING, report.verdict());
            assertTrue(report.verdictReason().contains("failed"));
            assertTrue(report.verdictReason().contains("1 failed"));

            // Render HTML must use warning badge
            byte[] html = htmlRenderer.render(report);
            var htmlContent = new String(html, StandardCharsets.UTF_8);
            assertTrue(htmlContent.contains("WARNING") || htmlContent.contains("warning"));
        }

        @Test
        @DisplayName("should produce FAILED verdict when any assertion ERROR (priority over FAILED)")
        void shouldProduceFailedVerdictWithErrorPriority() {
            var assertA = TaskId.of("assert-a");
            var passedResult = new AssertionResult(
                    assertA, AssertionStatus.PASSED, "all good", null,
                    Duration.ofMillis(10), now);
            var passedTaskResult = TaskResult.success(assertA, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", passedResult));

            var assertB = TaskId.of("assert-b");
            var failedResult = new AssertionResult(
                    assertB, AssertionStatus.FAILED, "threshold breached", null,
                    Duration.ofMillis(10), now);
            var failedTaskResult = TaskResult.success(assertB, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", failedResult));

            var assertC = TaskId.of("assert-c");
            var errorResult = new AssertionResult(
                    assertC, AssertionStatus.ERROR, "evaluation crashed", null,
                    Duration.ofMillis(10), now);
            var errorTaskResult = TaskResult.success(assertC, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", errorResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertA.value(), "agent-1", passedTaskResult)
                    .with(assertB.value(), "agent-1", failedTaskResult)
                    .with(assertC.value(), "agent-1", errorTaskResult);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1));

            CampaignReport report = engine.generate(state);

            // ERROR has priority over FAILED -> FAILED verdict
            assertEquals(Verdict.FAILED, report.verdict());
            assertTrue(report.verdictReason().contains("error"));
            assertTrue(report.verdictReason().contains("1 error"));

            // Render HTML must use failed badge
            byte[] html = htmlRenderer.render(report);
            var htmlContent = new String(html, StandardCharsets.UTF_8);
            assertTrue(htmlContent.contains("FAILED") || htmlContent.contains("failed"));
        }
    }

    // ══════════════════════════════════════════════
    // E2E: Multi-agent scenario
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-agent scenario")
    class MultiAgentScenario {

        @Test
        @DisplayName("should collect distinct agent IDs from multiple agents")
        void shouldCollectDistinctAgentIds() {
            var taskA = TaskId.of("task-a");
            var resultA = TaskResult.success(taskA, "check-db",
                    Duration.ofSeconds(1), Map.of());

            var taskB = TaskId.of("task-b");
            var resultB = TaskResult.success(taskB, "check-kafka",
                    Duration.ofSeconds(1), Map.of());

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskA.value(), "agent-alpha", resultA)
                    .with(taskB.value(), "agent-beta", resultB)
                    .with(taskA.value(), "agent-gamma", resultA); // same task, different agent

            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(2));

            CampaignReport report = engine.generate(state);

            var agentIds = report.environment().agentIds();
            assertEquals(3, agentIds.size());
            assertTrue(agentIds.contains("agent-alpha"));
            assertTrue(agentIds.contains("agent-beta"));
            assertTrue(agentIds.contains("agent-gamma"));
        }

        @Test
        @DisplayName("should generate report with tasks distributed across agents")
        void shouldHandleTasksAcrossMultipleAgents() throws IOException {
            var injId = TaskId.of("inj-1");
            var injResult = new InjectionResult(
                    injId, "Sim", Duration.ofSeconds(5),
                    100, 100, 0, 0.0, 20.0,
                    5, 10, 15, 20, 25, 30, 3, 8.0,
                    tempDir.resolve("gatling-multi"), Map.of());
            var injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(5), Map.of("injectionResult", injResult));

            // Same task executed by 2 different agents (multi-claim scenario)
            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(injId.value(), "agent-east", injTaskResult)
                    .with(injId.value(), "agent-west", injTaskResult);

            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(5));

            CampaignReport report = engine.generate(state);

            // Both agent IDs must appear
            assertTrue(report.environment().agentIds().contains("agent-east"));
            assertTrue(report.environment().agentIds().contains("agent-west"));

            // Render to all formats
            byte[] html = htmlRenderer.render(report);
            byte[] json = jsonRenderer.render(report);
            byte[] pdf = pdfRenderer.render(report);

            assertTrue(html.length > 0);
            assertTrue(json.length > 0);
            assertTrue(pdf.length > 4);

            // Write to disk
            var props = new ReportProperties(tempDir.toString(),
                    List.of(ReportFormat.HTML, ReportFormat.JSON, ReportFormat.PDF));
            var writer = new ReportFileWriter(
                    List.of(htmlRenderer, jsonRenderer, pdfRenderer), props);
            Path outputDir = writer.write(executionId, report);

            assertTrue(Files.exists(outputDir.resolve("campaign.html")));
            assertTrue(Files.exists(outputDir.resolve("campaign.json")));
            assertTrue(Files.exists(outputDir.resolve("campaign.pdf")));
        }
    }

    // ══════════════════════════════════════════════
    // E2E: Edge cases
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty execution state gracefully")
        void shouldHandleEmptyState() throws IOException {
            var context = ExecutionContext.initial(executionId, scenarioId);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1));

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.SUCCESS, report.verdict());
            assertEquals(0, report.executionSummary().totalTasks());
            assertTrue(report.preparationResults().isEmpty());
            assertTrue(report.injectionResults().isEmpty());
            assertTrue(report.assertionResults().isEmpty());

            // Should render and write to disk without error
            byte[] html = htmlRenderer.render(report);
            byte[] json = jsonRenderer.render(report);
            byte[] pdf = pdfRenderer.render(report);
            assertTrue(html.length > 0);
            assertTrue(json.length > 0);
            assertTrue(pdf.length > 4);

            var props = new ReportProperties(tempDir.toString(),
                    List.of(ReportFormat.HTML, ReportFormat.JSON, ReportFormat.PDF));
            var writer = new ReportFileWriter(
                    List.of(htmlRenderer, jsonRenderer, pdfRenderer), props);
            Path outputDir = writer.write(executionId, report);
            assertTrue(Files.exists(outputDir.resolve("campaign.html")));
        }

        @Test
        @DisplayName("should handle scenario with FAILED and SKIPPED tasks")
        void shouldHandleFailedAndSkippedTasks() {
            var failId = TaskId.of("fail-1");
            var failResult = TaskResult.failed(failId, "crash-task",
                    Duration.ofSeconds(1), "connection refused", new RuntimeException("boom"));

            var skipId = TaskId.of("skip-1");
            var skipResult = TaskResult.skipped(skipId, "optional-task",
                    "dependency failed");

            var okId = TaskId.of("ok-1");
            var okResult = TaskResult.success(okId, "db-check",
                    Duration.ofMillis(500), Map.of());

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(failId.value(), "agent-1", failResult)
                    .with(skipId.value(), "agent-1", skipResult)
                    .with(okId.value(), "agent-1", okResult);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(3));

            CampaignReport report = engine.generate(state);

            var summary = report.executionSummary();
            assertEquals(3, summary.totalTasks());
            assertEquals(1, summary.successfulTasks());
            assertEquals(1, summary.failedTasks());
            assertEquals(1, summary.skippedTasks());

            // Verdict SUCCESS because no assertions present
            assertEquals(Verdict.SUCCESS, report.verdict());

            // HTML must contain all statuses
            byte[] html = htmlRenderer.render(report);
            var htmlContent = new String(html, StandardCharsets.UTF_8);
            assertTrue(htmlContent.contains("FAILED"));
            assertTrue(htmlContent.contains("SKIPPED"));
            assertTrue(htmlContent.contains("SUCCESS"));
        }

        @Test
        @DisplayName("should handle report generation for scenario with many tasks (no OOM)")
        void shouldHandleLargeState() {
            var context = ExecutionContext.initial(executionId, scenarioId);
            int taskCount = 50;
            for (int i = 0; i < taskCount; i++) {
                var tid = TaskId.of("task-" + i);
                var result = TaskResult.success(tid, "prep-" + i,
                        Duration.ofMillis(10), Map.of("index", i));
                context = context.with(tid.value(), "agent-1", result);
            }
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1));

            CampaignReport report = engine.generate(state);

            assertEquals(taskCount, report.preparationResults().size());
            assertEquals(taskCount, report.executionSummary().totalTasks());

            // Render all formats (must not OOM)
            byte[] html = htmlRenderer.render(report);
            byte[] json = jsonRenderer.render(report);
            byte[] pdf = pdfRenderer.render(report);

            assertTrue(html.length > 0);
            assertTrue(json.length > 0);
            assertTrue(pdf.length > 4);
        }

        @Test
        @DisplayName("should include JVM version in environment info")
        void shouldIncludeJvmVersion() {
            var taskId = TaskId.of("t1");
            var result = TaskResult.success(taskId, "setup",
                    Duration.ofMillis(10), Map.of());
            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1));

            CampaignReport report = engine.generate(state);

            // JVM version comes from System.getProperty("java.version")
            assertNotNull(report.environment().jvmVersion());
            assertFalse(report.environment().jvmVersion().isBlank());

            // Must appear in HTML
            byte[] html = htmlRenderer.render(report);
            var htmlContent = new String(html, StandardCharsets.UTF_8);
            assertTrue(htmlContent.contains(report.environment().jvmVersion()));
        }

        @Test
        @DisplayName("should write report to custom output directory")
        void shouldWriteToCustomOutputDirectory() throws IOException {
            Path customDir = tempDir.resolve("custom-reports");
            var taskId = TaskId.of("t1");
            var result = TaskResult.success(taskId, "setup",
                    Duration.ofMillis(10), Map.of());
            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1));

            CampaignReport report = engine.generate(state);

            var props = new ReportProperties(customDir.toString(),
                    List.of(ReportFormat.HTML, ReportFormat.JSON));
            var writer = new ReportFileWriter(
                    List.of(htmlRenderer, jsonRenderer), props);
            Path outputDir = writer.write(executionId, report);

            assertTrue(outputDir.startsWith(customDir));
            assertTrue(Files.exists(outputDir.resolve("campaign.html")));
            assertTrue(Files.exists(outputDir.resolve("campaign.json")));
        }

        @Test
        @DisplayName("should handle scenario with Gatling directory copy")
        void shouldHandleGatlingDirectoryCopy() throws IOException {
            // Create a fake Gatling output directory
            Path gatlingSource = tempDir.resolve("gatling-output");
            Files.createDirectories(gatlingSource);
            Files.writeString(gatlingSource.resolve("simulation.log"), "RUN\tcom.example.Sim\t1577836800\tOK");
            Files.createDirectories(gatlingSource.resolve("js"));
            Files.writeString(gatlingSource.resolve("js").resolve("stats.json"), "{\"meanResponseTime\": 50}");

            var injId = TaskId.of("inj-1");
            var injResult = new InjectionResult(
                    injId, "com.example.ApiSimulation", Duration.ofSeconds(10),
                    100, 100, 0, 0.0, 10.0,
                    5, 10, 15, 20, 25, 30, 3, 8.0,
                    gatlingSource, Map.of());
            var injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(10), Map.of("injectionResult", injResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(injId.value(), "agent-1", injTaskResult);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(10));

            CampaignReport report = engine.generate(state);

            var props = new ReportProperties(tempDir.toString(),
                    List.of(ReportFormat.HTML));
            var writer = new ReportFileWriter(List.of(htmlRenderer), props);
            Path outputDir = writer.write(executionId, report);

            // Gatling directory must be copied
            Path copiedGatling = outputDir.resolve("gatling")
                    .resolve("com.example.ApiSimulation");
            assertTrue(Files.exists(copiedGatling));
            assertTrue(Files.exists(copiedGatling.resolve("simulation.log")));
            assertTrue(Files.exists(copiedGatling.resolve("js").resolve("stats.json")));
            assertEquals("RUN\tcom.example.Sim\t1577836800\tOK",
                    Files.readString(copiedGatling.resolve("simulation.log")));
        }

        @Test
        @DisplayName("should handle TIMEOUT task status in summary")
        void shouldHandleTimeoutStatus() {
            var timeoutId = TaskId.of("timeout-1");
            // Create a TIMEOUT result using the full constructor
            var timeoutResult = new TaskResult(
                    timeoutId, "slow-task", TaskStatus.TIMEOUT,
                    Duration.ofSeconds(30), Map.of(), "Operation timed out",
                    new java.util.concurrent.TimeoutException("timeout"), Instant.now());

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(timeoutId.value(), "agent-1", timeoutResult);
            var state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(30));

            CampaignReport report = engine.generate(state);

            // TIMEOUT mapped to FAILED in summary
            assertEquals(1, report.executionSummary().failedTasks());
            assertEquals(0, report.executionSummary().successfulTasks());
        }
    }

    // ══════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════

    private static long countFilesWithPrefix(Path dir, String prefix) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith(prefix)).count();
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
                for (Path p : paths) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
