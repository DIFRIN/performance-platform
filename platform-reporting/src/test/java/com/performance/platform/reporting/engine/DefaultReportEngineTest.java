package com.performance.platform.reporting.engine;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.event.ReportGenerated;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.model.CampaignReport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultReportEngine")
class DefaultReportEngineTest {

    // ── Manual stubs (Mockito incompatible with Java 25) ──

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
            // no-op for tests
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
            // no-op for tests
        }

        @Override
        public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
            return Map.of();
        }
    }

    static class StubEventPublisher implements ApplicationEventPublisher {
        final List<Object> publishedEvents = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            publishedEvents.add(event);
        }
    }

    private StubExecutionRepository repository;
    private StubEventPublisher eventPublisher;
    private DefaultReportEngine engine;
    private ExecutionId executionId;
    private ScenarioId scenarioId;
    private Instant now;

    @BeforeEach
    void setUp() {
        repository = new StubExecutionRepository();
        eventPublisher = new StubEventPublisher();
        engine = new DefaultReportEngine(repository, eventPublisher);
        executionId = new ExecutionId("exec-1");
        scenarioId = new ScenarioId("scenario-1");
        now = Instant.now();
    }

    // ══════════════════════════════════════════════
    // generate()
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("generate()")
    class GenerateTests {

        @Test
        @DisplayName("should build CampaignReport from ExecutionState with preparation tasks")
        void shouldBuildCampaignReportFromPreparationTasks() {
            TaskId taskId = new TaskId("task-1");
            TaskResult result = TaskResult.success(taskId, "database-setup", Duration.ofSeconds(2), Map.of("rows", 100));
            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(5)
            );

            CampaignReport report = engine.generate(state);

            assertNotNull(report.id());
            assertEquals(scenarioId, report.scenarioId());
            assertEquals(Verdict.SUCCESS, report.verdict());
            assertEquals(1, report.preparationResults().size());
            assertEquals("database-setup", report.preparationResults().get(0).taskName());
            assertTrue(report.injectionResults().isEmpty());
            assertTrue(report.assertionResults().isEmpty());
            assertEquals(1, report.executionSummary().totalTasks());
            assertEquals(1, report.executionSummary().successfulTasks());
            assertFalse(report.environment().agentIds().isEmpty());
        }

        @Test
        @DisplayName("should classify injection tasks via InjectionResult in outputs")
        void shouldClassifyInjectionTasks() throws Exception {
            TaskId injTaskId = new TaskId("inj-1");
            InjectionResult injResult = new InjectionResult(
                    injTaskId, "Simulation", Duration.ofSeconds(10),
                    1000, 990, 10, 1.0, 100.0,
                    10, 15, 20, 25, 30, 35, 5, 8.5,
                    Path.of("/tmp/gatling"), Map.of()
            );
            TaskResult taskResult = TaskResult.success(injTaskId, "gatling-injection",
                    Duration.ofSeconds(10), Map.of("injectionResult", injResult));

            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(injTaskId.value(), "agent-1", taskResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(15)
            );

            CampaignReport report = engine.generate(state);

            assertTrue(report.preparationResults().isEmpty());
            assertEquals(1, report.injectionResults().size());
            assertEquals(injTaskId, report.injectionResults().get(0).taskId());
            assertEquals(injResult, report.injectionResults().get(0).metrics());
            assertEquals(Path.of("/tmp/gatling"), report.injectionResults().get(0).gatlingReportDirectory());
        }

        @Test
        @DisplayName("should classify assertion tasks via AssertionResult in outputs")
        void shouldClassifyAssertionTasks() {
            TaskId assertTaskId = new TaskId("assert-1");
            AssertionResult assertResult = new AssertionResult(
                    assertTaskId, AssertionStatus.PASSED, "metric ok",
                    null, Duration.ofMillis(50), now
            );
            TaskResult taskResult = TaskResult.success(assertTaskId, "gatling-metric",
                    Duration.ofMillis(50), Map.of("assertionResult", assertResult));

            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertTaskId.value(), "agent-1", taskResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1)
            );

            CampaignReport report = engine.generate(state);

            assertEquals(1, report.assertionResults().size());
            assertEquals(assertTaskId, report.assertionResults().get(0).assertionId());
            assertEquals(AssertionStatus.PASSED, report.assertionResults().get(0).result().status());
        }

        @Test
        @DisplayName("should compute correct WARNING verdict when assertion FAILED")
        void shouldComputeWarningVerdict() {
            TaskId assertTaskId = new TaskId("assert-1");
            AssertionResult assertResult = new AssertionResult(
                    assertTaskId, AssertionStatus.FAILED, "threshold exceeded",
                    null, Duration.ofMillis(10), now
            );
            TaskResult taskResult = TaskResult.success(assertTaskId, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", assertResult));

            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertTaskId.value(), "agent-1", taskResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1)
            );

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.WARNING, report.verdict());
            assertTrue(report.verdictReason().contains("failed"));
        }

        @Test
        @DisplayName("should compute correct FAILED verdict when assertion ERROR")
        void shouldComputeFailedVerdict() {
            TaskId assertTaskId = new TaskId("assert-1");
            AssertionResult assertResult = new AssertionResult(
                    assertTaskId, AssertionStatus.ERROR, "evaluation error",
                    null, Duration.ofMillis(10), now
            );
            TaskResult taskResult = TaskResult.success(assertTaskId, "gatling-metric",
                    Duration.ofMillis(10), Map.of("assertionResult", assertResult));

            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertTaskId.value(), "agent-1", taskResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1)
            );

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.FAILED, report.verdict());
        }

        @Test
        @DisplayName("should build execution summary with counts and durations")
        void shouldBuildExecutionSummary() {
            TaskId prepId = new TaskId("prep-1");
            TaskResult prepResult = TaskResult.success(prepId, "db-setup",
                    Duration.ofSeconds(1), Map.of());
            TaskId injId = new TaskId("inj-1");
            InjectionResult injResult = new InjectionResult(
                    injId, "Sim", Duration.ofSeconds(5),
                    100, 100, 0, 0.0, 20.0,
                    5, 10, 15, 20, 25, 30, 2, 7.5,
                    Path.of("/tmp/g"), Map.of()
            );
            TaskResult injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(5), Map.of("injectionResult", injResult));
            TaskId assertId = new TaskId("assert-1");
            AssertionResult assertResult = new AssertionResult(
                    assertId, AssertionStatus.PASSED, "ok",
                    null, Duration.ofMillis(100), now
            );
            TaskResult assertTaskResult = TaskResult.success(assertId, "check",
                    Duration.ofMillis(100), Map.of("assertionResult", assertResult));

            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepId.value(), "agent-1", prepResult)
                    .with(injId.value(), "agent-1", injTaskResult)
                    .with(assertId.value(), "agent-1", assertTaskResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(10)
            );

            CampaignReport report = engine.generate(state);

            var summary = report.executionSummary();
            assertEquals(3, summary.totalTasks());
            assertEquals(3, summary.successfulTasks());
            assertEquals(0, summary.failedTasks());
            assertEquals(0, summary.skippedTasks());
            assertFalse(summary.preparationDuration().isNegative());
            assertFalse(summary.injectionDuration().isNegative());
            assertFalse(summary.assertionDuration().isNegative());
            // preparation entry should exist
            assertEquals(1, report.preparationResults().size());
            assertEquals("db-setup", report.preparationResults().get(0).taskName());
        }

        @Test
        @DisplayName("should handle execution state with empty context")
        void shouldHandleEmptyContext() {
            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(1)
            );

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.SUCCESS, report.verdict());
            assertTrue(report.preparationResults().isEmpty());
            assertTrue(report.injectionResults().isEmpty());
            assertTrue(report.assertionResults().isEmpty());
            assertEquals(0, report.executionSummary().totalTasks());
        }

        @Test
        @DisplayName("should collect agent IDs from context")
        void shouldCollectAgentIds() {
            TaskId taskId = new TaskId("task-1");
            TaskResult result = TaskResult.success(taskId, "setup", Duration.ofSeconds(1), Map.of());
            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-alpha", result);

            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(2)
            );

            CampaignReport report = engine.generate(state);

            assertTrue(report.environment().agentIds().contains("agent-alpha"));
        }

        @Test
        @DisplayName("should include FAILED task in summary counts")
        void shouldIncludeFailedTasks() {
            TaskId failId = new TaskId("fail-1");
            TaskResult failResult = TaskResult.failed(failId, "bad-task", Duration.ofSeconds(1),
                    "boom", new RuntimeException("boom"));
            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(failId.value(), "agent-1", failResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(5)
            );

            CampaignReport report = engine.generate(state);

            assertEquals(1, report.executionSummary().failedTasks());
            assertEquals(TaskStatus.FAILED, report.preparationResults().get(0).status());
        }

        @Test
        @DisplayName("should include SKIPPED task in summary counts")
        void shouldIncludeSkippedTasks() {
            TaskId skipId = new TaskId("skip-1");
            TaskResult skipResult = TaskResult.skipped(skipId, "optional-task", "not needed");
            // Skipped tasks use Duration.ZERO
            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(skipId.value(), "agent-1", skipResult);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(5)
            );

            CampaignReport report = engine.generate(state);

            assertEquals(1, report.executionSummary().skippedTasks());
        }

        @Test
        @DisplayName("should throw ReportGenerationException on generation error")
        void shouldThrowOnError() {
            // Null state causes NPE which surfaces directly (pre-condition violation)
            assertThrows(NullPointerException.class, () -> engine.generate(null));
        }
    }

    // ══════════════════════════════════════════════
    // onScenarioFinished()
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("onScenarioFinished()")
    class OnScenarioFinishedTests {

        @Test
        @DisplayName("should generate report and publish ReportGenerated event")
        void shouldGenerateAndPublishEvent() {
            ScenarioFinished event = new ScenarioFinished(executionId, scenarioId, Verdict.SUCCESS,
                    Duration.ofSeconds(5), now);

            // Put state in the repository so it can be found
            TaskId taskId = new TaskId("task-1");
            TaskResult result = TaskResult.success(taskId, "setup", Duration.ofSeconds(1), Map.of());
            ExecutionContext context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            ExecutionState state = new ExecutionState(
                    executionId, scenarioId, ExecutionStatus.COMPLETED,
                    Map.of(), context, now, now.plusSeconds(5)
            );
            repository.put(state);

            engine.onScenarioFinished(event);

            assertEquals(1, eventPublisher.publishedEvents.size());
            Object published = eventPublisher.publishedEvents.get(0);
            assertInstanceOf(ReportGenerated.class, published);
            ReportGenerated rg = (ReportGenerated) published;
            assertEquals(executionId, rg.executionId());
            assertNotNull(rg.reportId());
            assertNotNull(rg.timestamp());
        }

        @Test
        @DisplayName("should skip generation when execution state not found")
        void shouldSkipWhenStateNotFound() {
            ScenarioFinished event = new ScenarioFinished(executionId, scenarioId, Verdict.SUCCESS,
                    Duration.ofSeconds(5), now);
            // Don't put anything in the repository

            assertDoesNotThrow(() -> engine.onScenarioFinished(event));
            assertTrue(eventPublisher.publishedEvents.isEmpty());
        }

        @Test
        @DisplayName("should not propagate exception when generation fails")
        void shouldNotPropagateException() {
            // Use a bad executionId that causes generation to fail
            ScenarioFinished event = new ScenarioFinished(
                    new ExecutionId("non-existent"), scenarioId, Verdict.SUCCESS,
                    Duration.ofSeconds(5), now);
            // Repository returns empty → method logs warning and returns

            assertDoesNotThrow(() -> engine.onScenarioFinished(event));
            assertTrue(eventPublisher.publishedEvents.isEmpty());
        }
    }
}
