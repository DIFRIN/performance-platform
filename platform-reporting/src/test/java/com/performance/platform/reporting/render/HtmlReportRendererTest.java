package com.performance.platform.reporting.render;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.TaskReportEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HtmlReportRenderer")
class HtmlReportRendererTest {

    private HtmlReportRenderer renderer;
    private CampaignReport report;

    @BeforeEach
    void setUp() {
        renderer = new HtmlReportRenderer();

        ReportId reportId = ReportId.generate();
        ScenarioId scenarioId = new ScenarioId("test-scenario");
        Instant now = Instant.now();

        // Preparation entry
        TaskReportEntry prepEntry = new TaskReportEntry(
                new TaskId("prep-1"), "database-setup", TaskStatus.SUCCESS,
                Duration.ofSeconds(2), Map.of("rows", 100)
        );

        // Injection entry with KPIs
        InjectionResult injResult = new InjectionResult(
                new TaskId("inj-1"), "com.example.Simulation", Duration.ofSeconds(10),
                1000, 990, 10, 1.0, 100.0,
                10, 15, 20, 25, 30, 35, 5, 8.5,
                Path.of("/tmp/gatling"), Map.of()
        );
        InjectionReportEntry injEntry = new InjectionReportEntry(
                new TaskId("inj-1"), injResult, Path.of("/tmp/gatling")
        );

        // Assertion entry
        AssertionResult assertResult = new AssertionResult(
                new TaskId("assert-1"), AssertionStatus.PASSED, "throughput >= 50",
                null, Duration.ofMillis(50), now
        );
        AssertionReportEntry assertEntry = new AssertionReportEntry(
                new TaskId("assert-1"), assertResult, null
        );

        report = new CampaignReport(
                reportId, scenarioId, "Test Campaign", "2.0",
                List.of("api"), Map.of("team", "perf"),
                new EnvironmentInfo(List.of("agent-1"), "25", Map.of()),
                new ExecutionSummary(3, 2, 0, 1,
                        Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofMillis(50)),
                List.of(prepEntry),
                List.of(injEntry),
                List.of(assertEntry),
                Verdict.SUCCESS, "All assertions passed",
                now, Duration.ofSeconds(12)
        );
    }

    @Nested
    @DisplayName("getFormat()")
    class GetFormatTests {

        @Test
        @DisplayName("should return HTML")
        void shouldReturnHtml() {
            assertEquals(ReportFormat.HTML, renderer.getFormat());
        }
    }

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("should produce HTML containing scenario name and verdict")
        void shouldContainScenarioNameAndVerdict() {
            byte[] html = renderer.render(report);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("Test Campaign"));
            assertTrue(content.contains("SUCCESS"));
            assertTrue(content.contains("badge-success"));
        }

        @Test
        @DisplayName("should contain preparation table with task info")
        void shouldContainPreparationTable() {
            byte[] html = renderer.render(report);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("database-setup"));
            assertTrue(content.contains("SUCCESS"));
        }

        @Test
        @DisplayName("should contain injection KPIs")
        void shouldContainInjectionKPIs() {
            byte[] html = renderer.render(report);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("com.example.Simulation"));
            assertTrue(content.contains("1000")); // totalRequests
            assertTrue(content.contains("Throughput")); // KPI label
            assertTrue(content.contains("Error Rate"));
        }

        @Test
        @DisplayName("should contain assertion results")
        void shouldContainAssertionResults() {
            byte[] html = renderer.render(report);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("throughput &gt;= 50")); // HTML-escaped
            assertTrue(content.contains("PASSED"));
        }

        @Test
        @DisplayName("should contain execution summary")
        void shouldContainExecutionSummary() {
            byte[] html = renderer.render(report);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("3")); // totalTasks (check in table context)
        }

        @Test
        @DisplayName("should contain environment info")
        void shouldContainEnvironmentInfo() {
            byte[] html = renderer.render(report);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("agent-1"));
            assertTrue(content.contains("25")); // JVM version
        }

        @Test
        @DisplayName("should handle WARNING verdict with correct CSS")
        void shouldHandleWarningVerdict() {
            CampaignReport warnReport = buildReportWithVerdict(Verdict.WARNING, "Some failed");
            byte[] html = renderer.render(warnReport);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("badge-warning"));
            assertTrue(content.contains("WARNING"));
            assertTrue(content.contains("Some failed"));
        }

        @Test
        @DisplayName("should handle FAILED verdict with correct CSS")
        void shouldHandleFailedVerdict() {
            CampaignReport failReport = buildReportWithVerdict(Verdict.FAILED, "Critical error");
            byte[] html = renderer.render(failReport);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("badge-failed"));
            assertTrue(content.contains("FAILED"));
        }

        @Test
        @DisplayName("should handle empty preparation/injection/assertion lists")
        void shouldHandleEmptyLists() {
            CampaignReport emptyReport = buildReportWithVerdict(Verdict.SUCCESS, null);
            byte[] html = renderer.render(emptyReport);
            String content = new String(html, StandardCharsets.UTF_8);
            assertTrue(content.contains("No preparation tasks"));
            assertTrue(content.contains("No injection tasks"));
            assertTrue(content.contains("No assertion tasks"));
        }

        @Test
        @DisplayName("should HTML-escape special characters")
        void shouldEscapeSpecialChars() {
            CampaignReport escapeReport = new CampaignReport(
                    ReportId.generate(), new ScenarioId("sc"), "<script>alert('xss')</script>", "1.0",
                    List.of(), Map.of(),
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, Instant.now(), Duration.ZERO
            );
            byte[] html = renderer.render(escapeReport);
            String content = new String(html, StandardCharsets.UTF_8);
            assertFalse(content.contains("<script>"));
            assertTrue(content.contains("&lt;script&gt;"));
        }
    }

    // ── helper ──

    private CampaignReport buildReportWithVerdict(Verdict verdict, String reason) {
        return new CampaignReport(
                ReportId.generate(), new ScenarioId("sc"), "Campaign", "1.0",
                List.of(), Map.of(),
                new EnvironmentInfo(List.of(), "25", Map.of()),
                new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                List.of(), List.of(), List.of(),
                verdict, reason, Instant.now(), Duration.ZERO
        );
    }
}
