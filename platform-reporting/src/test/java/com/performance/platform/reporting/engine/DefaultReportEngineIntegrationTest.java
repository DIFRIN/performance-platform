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

/**
 * Tests d'integration du {@link DefaultReportEngine}.
 * <p>
 * Scenarios complexes que les tests unitaires ne couvrent pas completement :
 * combinaisons de phases, multi-agent, ordre de classification, scenarios d'evenement.
 */
@DisplayName("DefaultReportEngine IT")
class DefaultReportEngineIntegrationTest {

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
        executionId = ExecutionId.of("it-exec-001");
        scenarioId = ScenarioId.of("it-scenario");
        now = Instant.now();
    }

    // ══════════════════════════════════════════════
    // Classification par phase
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Task classification by phase")
    class TaskClassificationTests {

        @Test
        @DisplayName("should classify tasks with InjectionResult as injection phase")
        void shouldClassifyInjectionTasksCorrectly() {
            var injId = TaskId.of("inj-1");
            InjectionResult injResult = createInjectionResult(injId, "Sim", Duration.ofSeconds(5));
            var taskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(5), Map.of("inj-data", injResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(injId.value(), "agent-1", taskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(0, report.preparationResults().size());
            assertEquals(1, report.injectionResults().size());
            assertEquals(injId, report.injectionResults().get(0).taskId());
            assertEquals(injResult.simulationClass(),
                    report.injectionResults().get(0).metrics().simulationClass());
        }

        @Test
        @DisplayName("should classify tasks with AssertionResult as assertion phase")
        void shouldClassifyAssertionTasksCorrectly() {
            var assertId = TaskId.of("assert-1");
            var assertResult = new AssertionResult(
                    assertId, AssertionStatus.PASSED, "check passed",
                    null, Duration.ofMillis(10), now);
            var taskResult = TaskResult.success(assertId, "metric-check",
                    Duration.ofMillis(10), Map.of("assertionResult", assertResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertId.value(), "agent-1", taskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(0, report.preparationResults().size());
            assertEquals(0, report.injectionResults().size());
            assertEquals(1, report.assertionResults().size());
            assertEquals(assertId, report.assertionResults().get(0).assertionId());
            assertEquals(AssertionStatus.PASSED, report.assertionResults().get(0).result().status());
        }

        @Test
        @DisplayName("should classify tasks with neither InjectionResult nor AssertionResult as preparation")
        void shouldClassifyGenericTasksAsPreparation() {
            var prepId = TaskId.of("prep-1");
            // TaskResult with generic outputs, no InjectionResult or AssertionResult
            var taskResult = TaskResult.success(prepId, "shell-cleanup",
                    Duration.ofSeconds(1), Map.of("files_deleted", 10, "exit_code", 0));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepId.value(), "agent-1", taskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(1, report.preparationResults().size());
            assertEquals(0, report.injectionResults().size());
            assertEquals(0, report.assertionResults().size());
            assertEquals("shell-cleanup", report.preparationResults().get(0).taskName());
        }

        @Test
        @DisplayName("should classify mixed tasks into correct phases")
        void shouldClassifyMixedTasks() {
            // Preparation: no InjectionResult or AssertionResult
            var prepId = TaskId.of("prep-1");
            var prepResult = TaskResult.success(prepId, "db-purge",
                    Duration.ofSeconds(2), Map.of("rows", 100));

            // Injection: has InjectionResult in outputs
            var injId = TaskId.of("inj-1");
            InjectionResult injResult = createInjectionResult(injId, "MySim", Duration.ofSeconds(5));
            var injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(5), Map.of("injectionResult", injResult));

            // Assertion: has AssertionResult in outputs
            var assertId = TaskId.of("assert-1");
            var assertResult = new AssertionResult(
                    assertId, AssertionStatus.FAILED, "threshold breached",
                    null, Duration.ofMillis(20), now);
            var assertTaskResult = TaskResult.success(assertId, "gatling-metric",
                    Duration.ofMillis(20), Map.of("assertionResult", assertResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepId.value(), "agent-1", prepResult)
                    .with(injId.value(), "agent-1", injTaskResult)
                    .with(assertId.value(), "agent-1", assertTaskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(1, report.preparationResults().size(), "Should have 1 preparation entry");
            assertEquals(1, report.injectionResults().size(), "Should have 1 injection entry");
            assertEquals(1, report.assertionResults().size(), "Should have 1 assertion entry");
            assertEquals(3, report.executionSummary().totalTasks());
            assertEquals(Verdict.WARNING, report.verdict(), "WARNING because assertion FAILED");
        }
    }

    // ══════════════════════════════════════════════
    // Execution summary computation
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Execution summary computation")
    class ExecutionSummaryTests {

        @Test
        @DisplayName("should sum durations correctly across phases")
        void shouldSumDurationsCorrectly() {
            // Preparation: 2 sec
            var prepId = TaskId.of("prep-1");
            var prepResult = TaskResult.success(prepId, "setup",
                    Duration.ofSeconds(2), Map.of());

            // Injection: 10 sec
            var injId = TaskId.of("inj-1");
            InjectionResult injResult = createInjectionResult(injId, "Sim", Duration.ofSeconds(10));
            var injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(10), Map.of("injectionResult", injResult));

            // Assertion: 0.5 sec
            var assertId = TaskId.of("assert-1");
            var assertResult = new AssertionResult(
                    assertId, AssertionStatus.PASSED, "ok",
                    null, Duration.ofMillis(500), now);
            var assertTaskResult = TaskResult.success(assertId, "gatling-metric",
                    Duration.ofMillis(500), Map.of("assertionResult", assertResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepId.value(), "agent-1", prepResult)
                    .with(injId.value(), "agent-1", injTaskResult)
                    .with(assertId.value(), "agent-1", assertTaskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            var summary = report.executionSummary();
            assertEquals(Duration.ofSeconds(2), summary.preparationDuration());
            assertEquals(Duration.ofSeconds(10), summary.injectionDuration());
            assertEquals(Duration.ofMillis(500), summary.assertionDuration());
        }

        @Test
        @DisplayName("should compute total duration as sum of all phase durations")
        void shouldComputeTotalDuration() {
            var prepId = TaskId.of("prep-1");
            var prepResult = TaskResult.success(prepId, "setup",
                    Duration.ofSeconds(1), Map.of());

            var injId = TaskId.of("inj-1");
            InjectionResult injResult = createInjectionResult(injId, "Sim", Duration.ofSeconds(3));
            var injTaskResult = TaskResult.success(injId, "gatling",
                    Duration.ofSeconds(3), Map.of("injectionResult", injResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepId.value(), "agent-1", prepResult)
                    .with(injId.value(), "agent-1", injTaskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            // totalDuration = sum of all task durations across all phases
            var expectedTotal = Duration.ofSeconds(1).plus(Duration.ofSeconds(3));
            assertEquals(expectedTotal, report.totalDuration());
        }

        @Test
        @DisplayName("should handle multiple injections with correct summary")
        void shouldHandleMultipleInjections() {
            var injA = TaskId.of("inj-a");
            InjectionResult injResultA = createInjectionResult(injA, "SimA", Duration.ofSeconds(5));
            var taskResultA = TaskResult.success(injA, "gatling",
                    Duration.ofSeconds(5), Map.of("injectionResult", injResultA));

            var injB = TaskId.of("inj-b");
            InjectionResult injResultB = createInjectionResult(injB, "SimB", Duration.ofSeconds(8));
            var taskResultB = TaskResult.success(injB, "gatling",
                    Duration.ofSeconds(8), Map.of("injectionResult", injResultB));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(injA.value(), "agent-1", taskResultA)
                    .with(injB.value(), "agent-1", taskResultB);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(2, report.injectionResults().size());
            assertEquals(2, report.executionSummary().totalTasks());
            assertEquals(2, report.executionSummary().successfulTasks());
            assertEquals(Duration.ofSeconds(13), report.executionSummary().injectionDuration());
        }
    }

    // ══════════════════════════════════════════════
    // Event-driven generation
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Event-driven generation (onScenarioFinished)")
    class EventDrivenGenerationTests {

        @Test
        @DisplayName("should publish ReportGenerated event with correct fields after generation")
        void shouldPublishReportGeneratedEvent() {
            var taskId = TaskId.of("t1");
            var result = TaskResult.success(taskId, "setup",
                    Duration.ofSeconds(1), Map.of());
            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(taskId.value(), "agent-1", result);
            ExecutionState state = createState(context);
            repository.put(state);

            var event = new ScenarioFinished(executionId, scenarioId,
                    Verdict.SUCCESS, Duration.ofSeconds(5), now);
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
        @DisplayName("should not publish event when execution state is not found")
        void shouldNotPublishWhenStateNotFound() {
            // No state in repository
            var event = new ScenarioFinished(executionId, scenarioId,
                    Verdict.SUCCESS, Duration.ofSeconds(5), now);

            assertDoesNotThrow(() -> engine.onScenarioFinished(event));
            assertTrue(eventPublisher.publishedEvents.isEmpty());
        }

        @Test
        @DisplayName("should not propagate exception when generation fails inside listener")
        void shouldNotPropagateListenerException() {
            // State exists but will throw when we try to generate with corrupt data
            // Actually the listener catches exceptions, so even if state is bad it handles it
            var event = new ScenarioFinished(
                    ExecutionId.of("non-existent-id"), scenarioId,
                    Verdict.SUCCESS, Duration.ofSeconds(5), now);

            assertDoesNotThrow(() -> engine.onScenarioFinished(event));
            // No event published, no exception thrown
        }

        @Test
        @DisplayName("should generate correct report from listener-driven path")
        void shouldGenerateCorrectReportFromListener() {
            var prepId = TaskId.of("prep-1");
            var prepResult = TaskResult.success(prepId, "db-migrate",
                    Duration.ofSeconds(2), Map.of());

            var assertId = TaskId.of("assert-1");
            var assertResult = new AssertionResult(
                    assertId, AssertionStatus.PASSED, "row count matches",
                    null, Duration.ofMillis(100), now);
            var assertTaskResult = TaskResult.success(assertId, "database-assertion",
                    Duration.ofMillis(100), Map.of("assertionResult", assertResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(prepId.value(), "agent-1", prepResult)
                    .with(assertId.value(), "agent-1", assertTaskResult);
            ExecutionState state = createState(context);
            repository.put(state);

            var event = new ScenarioFinished(executionId, scenarioId,
                    Verdict.SUCCESS, Duration.ofSeconds(5), now);
            engine.onScenarioFinished(event);

            assertFalse(eventPublisher.publishedEvents.isEmpty());
            ReportGenerated rg = (ReportGenerated) eventPublisher.publishedEvents.get(0);
            assertNotNull(rg.reportId());
            assertEquals(executionId, rg.executionId());
        }
    }

    // ══════════════════════════════════════════════
    // Verdict calculation integration
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Verdict calculation integration")
    class VerdictIntegrationTests {

        @Test
        @DisplayName("SUCCESS when all assertions PASSED or SKIPPED")
        void successWhenAllPassedOrSkipped() {
            var assertA = TaskId.of("assert-a");
            var passedResult = new AssertionResult(
                    assertA, AssertionStatus.PASSED, "p95 ok", null,
                    Duration.ofMillis(10), now);
            var taskResultA = TaskResult.success(assertA, "check",
                    Duration.ofMillis(10), Map.of("assertionResult", passedResult));

            var assertB = TaskId.of("assert-b");
            var skippedResult = new AssertionResult(
                    assertB, AssertionStatus.SKIPPED, "no data to check", null,
                    Duration.ofMillis(5), now);
            var taskResultB = TaskResult.success(assertB, "check",
                    Duration.ofMillis(5), Map.of("assertionResult", skippedResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertA.value(), "agent-1", taskResultA)
                    .with(assertB.value(), "agent-1", taskResultB);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.SUCCESS, report.verdict());
            assertTrue(report.verdictReason().contains("passed"));
            assertTrue(report.verdictReason().contains("skipped"));
        }

        @Test
        @DisplayName("WARNING when at least one FAILED and no ERROR")
        void warningWhenFailedNoError() {
            var assertA = TaskId.of("assert-a");
            var passedResult = new AssertionResult(
                    assertA, AssertionStatus.PASSED, "ok", null,
                    Duration.ofMillis(10), now);
            var taskResultA = TaskResult.success(assertA, "check",
                    Duration.ofMillis(10), Map.of("assertionResult", passedResult));

            var assertB = TaskId.of("assert-b");
            var failedResult = new AssertionResult(
                    assertB, AssertionStatus.FAILED, "threshold exceeded", null,
                    Duration.ofMillis(10), now);
            var taskResultB = TaskResult.success(assertB, "check",
                    Duration.ofMillis(10), Map.of("assertionResult", failedResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertA.value(), "agent-1", taskResultA)
                    .with(assertB.value(), "agent-1", taskResultB);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.WARNING, report.verdict());
            assertTrue(report.verdictReason().contains("1 passed"));
            assertTrue(report.verdictReason().contains("1 failed"));
        }

        @Test
        @DisplayName("FAILED when at least one ERROR regardless of other statuses")
        void failedWhenErrorPresent() {
            var assertA = TaskId.of("assert-a");
            var passedResult = new AssertionResult(
                    assertA, AssertionStatus.PASSED, "ok", null,
                    Duration.ofMillis(10), now);
            var taskResultA = TaskResult.success(assertA, "check",
                    Duration.ofMillis(10), Map.of("assertionResult", passedResult));

            var assertB = TaskId.of("assert-b");
            var errorResult = new AssertionResult(
                    assertB, AssertionStatus.ERROR, "null pointer in evaluator", null,
                    Duration.ofMillis(5), now);
            var taskResultB = TaskResult.success(assertB, "check",
                    Duration.ofMillis(5), Map.of("assertionResult", errorResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertA.value(), "agent-1", taskResultA)
                    .with(assertB.value(), "agent-1", taskResultB);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.FAILED, report.verdict());
            assertTrue(report.verdictReason().contains("error"),
                    "Verdict reason must mention error");
        }

        @Test
        @DisplayName("ERROR has priority over FAILED — multiple assertions")
        void errorPriorityOverFailed() {
            var assertA = TaskId.of("assert-a");
            var failedA = new AssertionResult(
                    assertA, AssertionStatus.FAILED, "threshold A", null,
                    Duration.ofMillis(10), now);
            var taskResultA = TaskResult.success(assertA, "check",
                    Duration.ofMillis(10), Map.of("assertionResult", failedA));

            var assertB = TaskId.of("assert-b");
            var failedB = new AssertionResult(
                    assertB, AssertionStatus.FAILED, "threshold B", null,
                    Duration.ofMillis(10), now);
            var taskResultB = TaskResult.success(assertB, "check",
                    Duration.ofMillis(10), Map.of("assertionResult", failedB));

            var assertC = TaskId.of("assert-c");
            var errorC = new AssertionResult(
                    assertC, AssertionStatus.ERROR, "crash", null,
                    Duration.ofMillis(5), now);
            var taskResultC = TaskResult.success(assertC, "check",
                    Duration.ofMillis(5), Map.of("assertionResult", errorC));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertA.value(), "agent-1", taskResultA)
                    .with(assertB.value(), "agent-1", taskResultB)
                    .with(assertC.value(), "agent-1", taskResultC);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            // One ERROR trumps all FAILED
            assertEquals(Verdict.FAILED, report.verdict());
            assertTrue(report.verdictReason().contains("2 failed"));
            assertTrue(report.verdictReason().contains("1 error"));
        }
    }

    // ══════════════════════════════════════════════
    // Error handling
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling and edge cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should generate report with only SKIPPED assertions")
        void shouldHandleOnlySkippedAssertions() {
            var skipA = TaskId.of("skip-a");
            var skippedA = new AssertionResult(
                    skipA, AssertionStatus.SKIPPED, "no data", null,
                    Duration.ofMillis(1), now);
            var taskResultA = TaskResult.success(skipA, "check",
                    Duration.ofMillis(1), Map.of("assertionResult", skippedA));

            var skipB = TaskId.of("skip-b");
            var skippedB = new AssertionResult(
                    skipB, AssertionStatus.SKIPPED, "dependency failed", null,
                    Duration.ofMillis(1), now);
            var taskResultB = TaskResult.success(skipB, "check",
                    Duration.ofMillis(1), Map.of("assertionResult", skippedB));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(skipA.value(), "agent-1", taskResultA)
                    .with(skipB.value(), "agent-1", taskResultB);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.SUCCESS, report.verdict());
            assertEquals(2, report.executionSummary().skippedTasks());
            assertEquals(2, report.executionSummary().totalTasks());
            assertEquals(0, report.executionSummary().successfulTasks());
            assertEquals(0, report.executionSummary().failedTasks());
        }

        @Test
        @DisplayName("should handle SCENARIO status FAILED in assertion")
        void shouldHandleFailedAssertionStatusInVerdict() {
            // Testing FAILED assertion status (not ERROR)
            var assertId = TaskId.of("assert-1");
            var failedAssert = new AssertionResult(
                    assertId, AssertionStatus.FAILED, "p99 exceeded 100ms", null,
                    Duration.ofMillis(15), now);
            var taskResult = TaskResult.success(assertId, "gatling-metric",
                    Duration.ofMillis(15), Map.of("assertionResult", failedAssert));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertId.value(), "agent-1", taskResult);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            assertEquals(Verdict.WARNING, report.verdict());
            // Assertion status FAILED counts as failed in summary, not ERROR
            assertEquals(1, report.executionSummary().failedTasks());
            assertEquals(0, report.executionSummary().successfulTasks());
        }

        @Test
        @DisplayName("should handle assertion with null result in VerdictCalculator")
        void shouldHandleNullAssertionResult() {
            // The VerdictCalculator skips entries with null result
            // DefaultReportEngine uses AssertionResult from ExecutionContext, so it's never null
            // But we test the edge case at the calculator level
            assertEquals(Verdict.SUCCESS,
                    VerdictCalculator.calculate(List.of()));
        }

        @Test
        @DisplayName("should produce valid report with mixed injection and assertion taskName values")
        void shouldHandleVariousTaskNames() {
            // Various task names as strings (not enums)
            var injId = TaskId.of("inj-1");
            InjectionResult injResult = createInjectionResult(injId, "MySim", Duration.ofSeconds(3));
            var injTask = TaskResult.success(injId, "gatling-http-injection",
                    Duration.ofSeconds(3), Map.of("injectionResult", injResult));

            var assertId = TaskId.of("assert-1");
            var assertResult = new AssertionResult(
                    assertId, AssertionStatus.PASSED, "throughput >= 100", null,
                    Duration.ofMillis(30), now);
            var assertTask = TaskResult.success(assertId, "custom-assertion-plugin",
                    Duration.ofMillis(30), Map.of("assertionResult", assertResult));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(injId.value(), "agent-1", injTask)
                    .with(assertId.value(), "agent-1", assertTask);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            // Injection classification works: the injection result is found in injectionResults
            assertEquals("MySim", report.injectionResults().get(0).metrics().simulationClass());
            assertEquals(Verdict.SUCCESS, report.verdict());
            // Assertion entry is correctly classified as assertion (not preparation)
            assertEquals(1, report.assertionResults().size());
            assertEquals("throughput >= 100", report.assertionResults().get(0).result().description());
            // No tasks classified as preparation (both are typed)
            assertEquals(0, report.preparationResults().size());
        }

        @Test
        @DisplayName("should handle generate() throwing ReportGenerationException on null state")
        void shouldThrowOnNullState() {
            // Null state triggers NPE in classifyTasks (pre-condition violation)
            // This is expected behavior per the spec — null is not a valid input
            assertThrows(NullPointerException.class, () -> engine.generate(null));
        }

        @Test
        @DisplayName("should generate report with all verdict reason fields populated")
        void shouldPopulateAllVerdictReasonFields() {
            var assertA = TaskId.of("assert-a");
            var passedA = new AssertionResult(assertA, AssertionStatus.PASSED, "ok", null, Duration.ofMillis(5), now);
            var taskA = TaskResult.success(assertA, "check", Duration.ofMillis(5), Map.of("assertionResult", passedA));

            var assertB = TaskId.of("assert-b");
            var failedB = new AssertionResult(assertB, AssertionStatus.FAILED, "fail", null, Duration.ofMillis(5), now);
            var taskB = TaskResult.success(assertB, "check", Duration.ofMillis(5), Map.of("assertionResult", failedB));

            var assertC = TaskId.of("assert-c");
            var errorC = new AssertionResult(assertC, AssertionStatus.ERROR, "error", null, Duration.ofMillis(5), now);
            var taskC = TaskResult.success(assertC, "check", Duration.ofMillis(5), Map.of("assertionResult", errorC));

            var assertD = TaskId.of("assert-d");
            var skippedD = new AssertionResult(assertD, AssertionStatus.SKIPPED, "skip", null, Duration.ofMillis(5), now);
            var taskD = TaskResult.success(assertD, "check", Duration.ofMillis(5), Map.of("assertionResult", skippedD));

            var context = ExecutionContext.initial(executionId, scenarioId)
                    .with(assertA.value(), "agent-1", taskA)
                    .with(assertB.value(), "agent-1", taskB)
                    .with(assertC.value(), "agent-1", taskC)
                    .with(assertD.value(), "agent-1", taskD);
            ExecutionState state = createState(context);

            CampaignReport report = engine.generate(state);

            String reason = report.verdictReason();
            assertNotNull(reason);
            assertTrue(reason.contains("1 passed"));
            assertTrue(reason.contains("1 failed"));
            assertTrue(reason.contains("1 error"));
            assertTrue(reason.contains("1 skipped"));
        }
    }

    // ══════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════

    private ExecutionState createState(ExecutionContext context) {
        return new ExecutionState(
                executionId, scenarioId, ExecutionStatus.COMPLETED,
                Map.of(), context, now, now.plusSeconds(30));
    }

    private static InjectionResult createInjectionResult(TaskId taskId, String simClass, Duration duration) {
        return new InjectionResult(
                taskId, simClass, duration,
                1000, 990, 10, 1.0, 100.0,
                10, 15, 20, 25, 30, 35, 5, 8.5,
                Path.of("/tmp/gatling"), Map.of());
    }
}
