package com.performance.platform.reporting.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

@DisplayName("JsonReportRenderer")
class JsonReportRendererTest {

    private JsonReportRenderer renderer;
    private CampaignReport report;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        renderer = new JsonReportRenderer();

        var reportId = ReportId.generate();
        var scenarioId = new ScenarioId("test-scenario");
        var now = Instant.now();

        report = new CampaignReport(
                reportId, scenarioId, "API Test", "1.0",
                List.of("smoke"), Map.of("env", "staging"),
                new EnvironmentInfo(List.of("agent-1", "agent-2"), "25", Map.of("os", "linux")),
                new ExecutionSummary(3, 3, 0, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMillis(100)),
                List.of(new TaskReportEntry(new TaskId("t1"), "db-setup", TaskStatus.SUCCESS,
                        Duration.ofSeconds(1), Map.of())),
                List.of(new InjectionReportEntry(new TaskId("t2"),
                        new InjectionResult(new TaskId("t2"), "Sim", Duration.ofSeconds(5),
                                100, 100, 0, 0.0, 20.0,
                                5, 10, 15, 20, 25, 30, 3, 7.0,
                                Path.of("/tmp/gatling"), Map.of()),
                        Path.of("/tmp/gatling"))),
                List.of(new AssertionReportEntry(new TaskId("t3"),
                        new AssertionResult(new TaskId("t3"), AssertionStatus.PASSED,
                                "p99 < 50ms", null, Duration.ofMillis(100), now),
                        null)),
                Verdict.SUCCESS, "All good",
                now, Duration.ofSeconds(6)
        );
    }

    @Nested
    @DisplayName("getFormat()")
    class GetFormatTests {

        @Test
        @DisplayName("should return JSON")
        void shouldReturnJson() {
            assertEquals(ReportFormat.JSON, renderer.getFormat());
        }
    }

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("should produce valid JSON bytes")
        void shouldProduceJsonBytes() {
            byte[] json = renderer.render(report);
            assertNotNull(json);
            assertTrue(json.length > 0);

            var content = new String(json, StandardCharsets.UTF_8);
            assertTrue(content.contains("API Test"));
            assertTrue(content.contains("SUCCESS"));
            assertTrue(content.contains("agent-1"));
        }

        @Test
        @DisplayName("should produce pretty-printed JSON")
        void shouldBePrettyPrinted() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            // Pretty print means the JSON has newlines and indentation
            assertTrue(content.contains("\n  "));
        }

        @Test
        @DisplayName("should serialize Instant as ISO string, not timestamp")
        void shouldSerializeInstantAsIso() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            // Instant should be serialized as string like "2026-..." not as number
            assertTrue(content.contains("\"generatedAt\" : \""));
            assertFalse(content.contains("\"generatedAt\" : 1"));
        }

        @Test
        @DisplayName("should serialize Duration in output")
        void shouldSerializeDuration() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            // JavaTimeModule serializes Duration as numeric seconds + nanos
            assertTrue(content.contains("\"seconds\"") || content.contains("\"totalDuration\""));
        }

        @Test
        @DisplayName("should contain injection metrics")
        void shouldContainInjectionMetrics() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            assertTrue(content.contains("p90Ms"));
            assertTrue(content.contains("throughput"));
            assertTrue(content.contains("Sim"));
        }

        @Test
        @DisplayName("should contain environment info")
        void shouldContainEnvironmentInfo() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            assertTrue(content.contains("agent-2"));
            assertTrue(content.contains("linux"));
        }

        @Test
        @DisplayName("should contain assertion results")
        void shouldContainAssertionResults() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            assertTrue(content.contains("p99 < 50ms"));
            assertTrue(content.contains("PASSED"));
        }

        @Test
        @DisplayName("should contain tags and metadata")
        void shouldContainTagsAndMetadata() {
            byte[] json = renderer.render(report);
            var content = new String(json, StandardCharsets.UTF_8);
            assertTrue(content.contains("smoke"));
            assertTrue(content.contains("staging"));
        }

        @Test
        @DisplayName("should render empty report without error")
        void shouldRenderEmptyReport() {
            var empty = new CampaignReport(
                    ReportId.generate(), new ScenarioId("empty"), "Empty", "1.0",
                    List.of(), Map.of(),
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, Instant.now(), Duration.ZERO
            );
            byte[] json = renderer.render(empty);
            assertNotNull(json);
            assertTrue(json.length > 0);
        }
    }
}
