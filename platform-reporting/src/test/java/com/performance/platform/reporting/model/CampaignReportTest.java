package com.performance.platform.reporting.model;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.PublicationTarget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CampaignReport and model records")
class CampaignReportTest {

    // --- Shared test fixtures ---
    private static final ReportId reportId = ReportId.generate();
    private static final ScenarioId scenarioId = new ScenarioId("scenario-1");
    private static final TaskId taskId = new TaskId("task-1");
    private static final Instant now = Instant.now();
    private static final Duration duration = Duration.ofSeconds(5);

    @Nested
    @DisplayName("CampaignReport")
    class CampaignReportTests {

        @Test
        @DisplayName("should construct with all fields populated")
        void shouldConstructWithAllFields() {
            var env = new EnvironmentInfo(List.of("agent-1"), "25", Map.of("os", "linux"));
            var summary = new ExecutionSummary(3, 2, 0, 1,
                    Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1));

            var prepEntry = new TaskReportEntry(taskId, "database-setup", TaskStatus.SUCCESS, duration, Map.of());
            var injEntry = new InjectionReportEntry(taskId,
                    new InjectionResult(taskId, "Sim", duration, 100, 99, 1,
                            1.0, 20.0, 10, 15, 20, 25, 30, 35, 5, 8.5,
                            Path.of("/tmp/gatling"), Map.of()),
                    Path.of("/tmp/gatling"));
            var evidence = new Evidence(99L, 100L, AssertionOperator.GTE, "requests", Map.of());
            var assertResult = new AssertionResult(taskId, AssertionStatus.PASSED, "enough requests",
                    evidence, duration, now);
            var assertEntry = new AssertionReportEntry(taskId, assertResult, evidence);

            var report = new CampaignReport(
                    reportId, scenarioId, "Test Scenario", "1.0",
                    List.of("smoke", "api"), Map.of("team", "perf"),
                    env, summary,
                    List.of(prepEntry),
                    List.of(injEntry),
                    List.of(assertEntry),
                    Verdict.SUCCESS, "All good",
                    now, Duration.ofSeconds(10)
            );

            assertEquals(reportId, report.id());
            assertEquals(scenarioId, report.scenarioId());
            assertEquals("Test Scenario", report.scenarioName());
            assertEquals("1.0", report.scenarioVersion());
            assertEquals(List.of("smoke", "api"), report.tags());
            assertEquals(Map.of("team", "perf"), report.metadata());
            assertEquals(env, report.environment());
            assertEquals(summary, report.executionSummary());
            assertEquals(1, report.preparationResults().size());
            assertEquals(1, report.injectionResults().size());
            assertEquals(1, report.assertionResults().size());
            assertEquals(Verdict.SUCCESS, report.verdict());
            assertEquals("All good", report.verdictReason());
            assertEquals(now, report.generatedAt());
            assertEquals(Duration.ofSeconds(10), report.totalDuration());
        }

        @Test
        @DisplayName("should use defensive copies for tags list")
        void shouldUseDefensiveCopyForTags() {
            var mutList = new java.util.ArrayList<>(List.of("a", "b"));
            var report = new CampaignReport(
                    reportId, scenarioId, "S", "1", mutList, Map.of(),
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, now, Duration.ZERO
            );

            mutList.add("c");
            assertEquals(2, report.tags().size());
            assertThrows(UnsupportedOperationException.class, () -> report.tags().add("d"));
        }

        @Test
        @DisplayName("should use defensive copies for metadata map")
        void shouldUseDefensiveCopyForMetadata() {
            var mutMap = new java.util.HashMap<>(Map.of("k", "v"));
            var report = new CampaignReport(
                    reportId, scenarioId, "S", "1", List.of(), mutMap,
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, now, Duration.ZERO
            );

            mutMap.put("k2", "v2");
            assertEquals(1, report.metadata().size());
            assertThrows(UnsupportedOperationException.class, () -> report.metadata().put("k3", "v3"));
        }

        @Test
        @DisplayName("should accept null tags -> empty list")
        void shouldAcceptNullTags() {
            var report = new CampaignReport(
                    reportId, scenarioId, "S", "1", null, Map.of(),
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, now, Duration.ZERO
            );
            assertTrue(report.tags().isEmpty());
        }

        @Test
        @DisplayName("should accept null metadata -> empty map")
        void shouldAcceptNullMetadata() {
            var report = new CampaignReport(
                    reportId, scenarioId, "S", "1", List.of(), null,
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, now, Duration.ZERO
            );
            assertTrue(report.metadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("EnvironmentInfo")
    class EnvironmentInfoTests {

        @Test
        @DisplayName("should construct with valid fields")
        void shouldConstruct() {
            var info = new EnvironmentInfo(List.of("a1", "a2"), "25", Map.of("os", "linux"));
            assertEquals(2, info.agentIds().size());
            assertEquals("25", info.jvmVersion());
            assertEquals("linux", info.systemProperties().get("os"));
        }

        @Test
        @DisplayName("should use defensive copy for agentIds")
        void shouldUseDefensiveCopyForAgentIds() {
            var mutList = new java.util.ArrayList<>(List.of("a1"));
            var info = new EnvironmentInfo(mutList, "25", Map.of());
            mutList.add("a2");
            assertEquals(1, info.agentIds().size());
        }
    }

    @Nested
    @DisplayName("ExecutionSummary")
    class ExecutionSummaryTests {

        @Test
        @DisplayName("should construct with valid fields")
        void shouldConstruct() {
            var s = new ExecutionSummary(5, 3, 1, 1,
                    Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
            assertEquals(5, s.totalTasks());
            assertEquals(3, s.successfulTasks());
            assertEquals(1, s.failedTasks());
            assertEquals(1, s.skippedTasks());
        }

        @Test
        @DisplayName("should reject negative totalTasks")
        void shouldRejectNegativeTotalTasks() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ExecutionSummary(-1, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO));
        }

        @Test
        @DisplayName("should reject negative failedTasks")
        void shouldRejectNegativeFailedTasks() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ExecutionSummary(0, 0, -1, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("TaskReportEntry")
    class TaskReportEntryTests {

        @Test
        @DisplayName("should construct with String taskName (not TaskType)")
        void shouldConstructWithStringTaskName() {
            var entry = new TaskReportEntry(taskId, "gatling-injection", TaskStatus.SUCCESS, duration, Map.of("rps", 100));
            assertEquals("gatling-injection", entry.taskName());
            assertEquals(TaskStatus.SUCCESS, entry.status());
            assertEquals(100, entry.outputs().get("rps"));
        }

        @Test
        @DisplayName("should use defensive copy for outputs")
        void shouldUseDefensiveCopyForOutputs() {
            java.util.HashMap<String, Object> mutMap = new java.util.HashMap<>();
            mutMap.put("k", "v");
            var entry = new TaskReportEntry(taskId, "t", TaskStatus.SUCCESS, duration, mutMap);
            mutMap.put("k2", "v2");
            assertEquals(1, entry.outputs().size());
        }
    }

    @Nested
    @DisplayName("InjectionReportEntry")
    class InjectionReportEntryTests {

        @Test
        @DisplayName("should construct with valid fields")
        void shouldConstruct() {
            var injResult = new InjectionResult(taskId, "Sim", duration, 100, 99, 1,
                    1.0, 20.0, 10, 15, 20, 25, 30, 35, 5, 8.5,
                    Path.of("/tmp/gatling"), Map.of());
            var entry = new InjectionReportEntry(taskId, injResult, Path.of("/tmp/gatling"));
            assertEquals(taskId, entry.taskId());
            assertEquals(injResult, entry.metrics());
            assertEquals(Path.of("/tmp/gatling"), entry.gatlingReportDirectory());
        }
    }

    @Nested
    @DisplayName("AssertionReportEntry")
    class AssertionReportEntryTests {

        @Test
        @DisplayName("should construct without evidence")
        void shouldConstructWithoutEvidence() {
            var result = new AssertionResult(taskId, AssertionStatus.PASSED, "ok", null, duration, now);
            var entry = new AssertionReportEntry(taskId, result, null);
            assertEquals(taskId, entry.assertionId());
            assertEquals(result, entry.result());
            assertNull(entry.evidence());
        }
    }

    @Nested
    @DisplayName("PublisherConfig")
    class PublisherConfigTests {

        @Test
        @DisplayName("should construct with target and properties")
        void shouldConstruct() {
            var config = new PublisherConfig(PublicationTarget.S3, Map.of("bucket", "reports"));
            assertEquals(PublicationTarget.S3, config.target());
            assertEquals("reports", config.properties().get("bucket"));
        }

        @Test
        @DisplayName("should use defensive copy for properties")
        void shouldUseDefensiveCopy() {
            var mutMap = new java.util.HashMap<String, String>();
            mutMap.put("k", "v");
            var config = new PublisherConfig(PublicationTarget.CONFLUENCE, mutMap);
            mutMap.put("k2", "v2");
            assertEquals(1, config.properties().size());
        }
    }

    @Nested
    @DisplayName("PublicationTarget")
    class PublicationTargetTests {

        @Test
        @DisplayName("should contain all expected values")
        void shouldContainAllValues() {
            assertEquals(6, PublicationTarget.values().length);
            assertNotNull(PublicationTarget.valueOf("CONFLUENCE"));
            assertNotNull(PublicationTarget.valueOf("S3"));
            assertNotNull(PublicationTarget.valueOf("SHAREPOINT"));
            assertNotNull(PublicationTarget.valueOf("GIT"));
            assertNotNull(PublicationTarget.valueOf("NEXUS"));
            assertNotNull(PublicationTarget.valueOf("CUSTOM"));
        }
    }
}
