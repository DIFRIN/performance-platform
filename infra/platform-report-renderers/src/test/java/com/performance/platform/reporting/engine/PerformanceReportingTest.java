package com.performance.platform.reporting.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.AssertionSample;
import com.performance.platform.domain.execution.*;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.output.ReportFileWriter;
import com.performance.platform.reporting.output.ReportProperties;
import com.performance.platform.reporting.render.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceReportingTest {
    @TempDir Path directory;
    private static final Instant START = Instant.parse("2026-10-27T18:00:00Z");
    private static final ExecutionId ID = ExecutionId.of("report-check");
    private static final ScenarioId SCENARIO = ScenarioId.of("generic-campaign");
    private static final TaskId LOAD = TaskId.of("load");
    private static final TaskId ASSERT = TaskId.of("slo");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private DefaultReportEngine engine() {
        return new DefaultReportEngine(new DefaultReportEngineTest.StubExecutionRepository(), event -> {},
                new ReportFileWriter(List.of(new HtmlReportRenderer()),
                        new ReportProperties(directory.toString(), List.of(ReportFormat.HTML))));
    }

    private ReportDefinition definition(List<ReportDefinition.Criterion> extra) {
        var criteria = new ArrayList<>(List.of(
                new ReportDefinition.Criterion("Maximum duration", "durationSeconds", "load", "<=", 60.0, true),
                new ReportDefinition.Criterion("Target volume", "totalRequests", "load", ">=", 1000.0, true),
                new ReportDefinition.Criterion("Technical errors", "errorRate", "load", "<=", 1.0, true),
                new ReportDefinition.Criterion("No saturation", "saturation", null, "==", 0.0, true),
                new ReportDefinition.Criterion("p95 SLO", "p95Ms", "load", "<=", 500.0, true)));
        criteria.addAll(extra);
        return new ReportDefinition(Map.of("api", "2.4.1", "worker", "3.2"), "PERF", "Validate release", "LOAD",
                "Constant load, one request per user", List.of("Isolated test data"), criteria,
                List.of(new ReportDefinition.Observation("saturation", null, null, 0.0, "flag", "Monitoring export", true),
                        new ReportDefinition.Observation("CPU", "load", "cpu", null, "%", null, true)),
                List.of(new ReportDefinition.Graph("CPU over time", "https://monitor.example/d/test?from=1&to=2", "Grafana")),
                List.of(), List.of("Review release against baseline"));
    }

    private ExecutionState state(ReportDefinition spec, boolean persisted, AssertionStatus assertionStatus) throws Exception {
        var injection = new InjectionResult(LOAD, "GenericSimulation", Duration.ofSeconds(10),
                1000, 1000, 0, 0, 100, 25, 30, 40, 50, 90, 100, 10, 30,
                directory, Map.of());
        var outputs = Map.<String, Object>of("result", injection, "cpu", 35.0);
        if (persisted) outputs = MAPPER.readValue(MAPPER.writeValueAsBytes(outputs), new TypeReference<>() {});
        var result = new TaskResult(LOAD, "gatling", com.performance.platform.domain.task.TaskStatus.SUCCESS,
                Duration.ofSeconds(10), outputs, null, null, START.plusSeconds(12));
        var context = ExecutionContext.initial(ID, SCENARIO).with("load", "agent-1", result);
        var tasks = new ArrayList<>(List.of(new ExecutionTask(LOAD, "gatling", Phase.INJECTION, true,
                Map.of("parameters", Map.of("protocol", "http", "loadModel", "campaign"),
                        "resolvedLoadModel", Map.of("type", "CONSTANT", "parameters", Map.of("users", 100))))));
        if (assertionStatus != null) {
            var assertion = new AssertionResult(ASSERT, assertionStatus, "Latency objective", null,
                    Duration.ofMillis(1), START.plusSeconds(13));
            context = context.with("slo", "agent-1", TaskResult.success(ASSERT, "gatling-metric",
                    Duration.ofMillis(1), Map.of("assertionResult", assertion)));
            tasks.add(new ExecutionTask(ASSERT, "gatling-metric", Phase.ASSERTION, true));
        }
        return new ExecutionState(ID, SCENARIO, ExecutionStatus.COMPLETED, Map.of(), context,
                START, START.plusSeconds(15), tasks.size(), tasks, "# source",
                new ScenarioDetails("Release campaign", "scenario-v7", List.of("release"), Map.of("owner", "perf"), spec));
    }

    @Test
    void persistedAssertionsAcceptDerivedPassedButUseTheRecordedVerdict() throws Exception {
        for (boolean legacy : List.of(false, true)) {
            for (AssertionStatus status : List.of(AssertionStatus.PASSED, AssertionStatus.FAILED)) {
                var original = state(definition(List.of()), true, null);
                Object assertion = legacy
                        ? new AssertionResult(ASSERT, status, "Latency objective", null,
                                Duration.ofMillis(1), START.plusSeconds(13))
                        : new AssertionSummary(ASSERT, status, "Latency objective", Map.of("observed", 50),
                                List.of(new AssertionSample(START, 50, "ms", Map.of())),
                                Duration.ofMillis(1), START.plusSeconds(13));
                Map<String, Object> serialized = MAPPER.readValue(
                        MAPPER.writeValueAsBytes(assertion), new TypeReference<>() {});
                assertEquals(status == AssertionStatus.PASSED, serialized.get("passed"));
                // Derived data must never override the authoritative verdict/status.
                serialized.put("passed", status != AssertionStatus.PASSED);
                String key = legacy ? "assertionResult" : AssertionSummary.OUTPUT_KEY;
                var result = TaskResult.success(ASSERT, "gatling-metric", Duration.ofMillis(1),
                        Map.of(key, serialized));
                var context = original.context().with("slo", "agent-1", result);
                var persisted = new ExecutionState(original.id(), original.scenarioId(), original.status(),
                        original.phaseStatuses(), context, original.startedAt(), original.updatedAt(),
                        original.plannedTaskCount(), original.tasks(), original.scenarioYaml(), original.scenarioDetails());

                var report = engine().generate(persisted);

                assertEquals(status, report.assertionResults().getFirst().result().status());
                assertEquals(status == AssertionStatus.PASSED ? Verdict.SUCCESS : Verdict.FAILED, report.verdict());
                var renderer = new HtmlReportRenderer();
                assertTrue(new String(renderer.render(report), StandardCharsets.UTF_8).contains(status.name()));
                assertEquals(status.name(), MAPPER.readTree(new JsonReportRenderer().render(report))
                        .at("/assertionResults/0/result/status").asText());
                try (var pdf = PDDocument.load(new PdfReportRenderer(renderer).render(report))) {
                    assertTrue(new PDFTextStripper().getText(pdf).contains(status.name()));
                }

                serialized.put("unexpectedField", true);
                assertThrows(com.performance.platform.application.exception.ReportGenerationException.class,
                        () -> engine().generate(persisted), "Unknown fields must still fail explicitly");
            }
        }
    }

    @Test
    void persistedOutputsRemainTypedAndAllFormatsContainTheSixSections() throws Exception {
        CampaignReport report = engine().generate(state(definition(List.of()), true, AssertionStatus.PASSED));
        assertEquals(Verdict.SUCCESS, report.verdict());
        assertEquals("Release campaign", report.scenarioName());
        assertEquals("scenario-v7", report.scenarioVersion());
        assertEquals(1, report.injectionResults().size());
        assertEquals("agent-1", report.injectionResults().getFirst().agentId());
        assertEquals(50, report.injectionResults().getFirst().metrics().p95Ms());
        assertEquals(10.0, report.performance().context().durationSeconds());
        assertEquals(START.plusSeconds(2), report.performance().context().startedAt());
        var htmlRenderer = new HtmlReportRenderer();
        var html = new String(htmlRenderer.render(report), StandardCharsets.UTF_8);
        for (String section : List.of("1 - Context", "2 - Load model", "3 - SLI / SLO results",
                "4 - Technical indicators", "5 - Analysis and recommendations", "6 - Validation / Test status")) {
            assertTrue(html.contains(section), section);
        }
        assertTrue(html.contains("p95"));
        assertTrue(html.contains("Not collected"));
        assertTrue(html.contains("worker"));
        assertTrue(html.contains("2.4.1"));
        assertTrue(html.contains("Monitoring export"));
        assertTrue(html.contains("https://monitor.example/d/test?from=1&amp;to=2"));
        assertFalse(html.contains("${"));
        var json = MAPPER.readTree(new JsonReportRenderer().render(report));
        assertEquals(6, json.get("performance").size());
        assertEquals("SUCCESS", json.at("/performance/validation/status").asText());
        assertEquals("PERF", json.at("/performance/context/environment").asText());
        assertEquals(50, json.at("/injectionResults/0/metrics/p95Ms").asInt());
        try (var pdf = PDDocument.load(new PdfReportRenderer(htmlRenderer).render(report))) {
            var text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("6 - Validation / Test status"));
            assertTrue(text.contains("p95"));
            assertTrue(text.contains("PERF"));
            assertTrue(text.contains("2.4.1"));
        }
    }

    @Test
    void missingEvidenceIsWarningAndOptionalTelemetryDoesNotPreventSuccess() throws Exception {
        var report = engine().generate(state(null, false, AssertionStatus.PASSED));
        assertEquals(Verdict.WARNING, report.verdict());
        assertFalse(report.performance().validation().limitations().isEmpty());
        assertEquals(Verdict.SUCCESS, engine().generate(state(definition(List.of()), false, null)).verdict());
    }

    @Test
    void criticalFailureIsFailedMinorFailureIsWarningAndUnknownIsNotPassed() throws Exception {
        assertEquals(Verdict.FAILED,
                engine().generate(state(definition(List.of()), false, AssertionStatus.FAILED)).verdict());
        var minor = new ReportDefinition.Criterion("Minor assertion", "assertion", "slo", "==", 1.0, false);
        assertEquals(Verdict.WARNING,
                engine().generate(state(definition(List.of(minor)), false, AssertionStatus.FAILED)).verdict());
        var missing = new ReportDefinition.Criterion("No DB saturation", "missingDB", null, "<=", 2.0, true);
        var report = engine().generate(state(definition(List.of(missing)), false, null));
        assertEquals(Verdict.WARNING, report.verdict());
        assertTrue(report.performance().sliSlo().stream().anyMatch(c -> c.metric().equals("missingDB")
                && c.actual() == null && c.status().equals("UNKNOWN")));
    }

    @Test
    void thresholdBoundaryAndMajorSaturationAreEvaluatedFromActualValues() throws Exception {
        var boundary = new ReportDefinition.Criterion("Boundary", "p95Ms", "load", "<=", 50.0, true);
        assertEquals(Verdict.SUCCESS, engine().generate(state(definition(List.of(boundary)), false, null)).verdict());
        var exceeded = new ReportDefinition.Criterion("Critical p95", "p95Ms", "load", "<", 50.0, true);
        assertEquals(Verdict.FAILED, engine().generate(state(definition(List.of(exceeded)), false, null)).verdict());
        var shortDuration = new ReportDefinition.Criterion("Max duration", "durationSeconds", "load", "<=", 9.0, true);
        assertEquals(Verdict.FAILED, engine().generate(state(definition(List.of(shortDuration)), false, null)).verdict());
        var unmetVolume = new ReportDefinition.Criterion("Volume", "totalRequests", "load", ">=", 1001.0, true);
        assertEquals(Verdict.FAILED, engine().generate(state(definition(List.of(unmetVolume)), false, null)).verdict());
        var base = definition(List.of());
        var saturated = new ReportDefinition(base.components(), base.environment(), base.objective(), base.testType(),
                base.loadDescription(), base.assumptions(), base.criteria(),
                List.of(new ReportDefinition.Observation("saturation", null, null, 1.0, "flag", "Monitoring export", true)),
                base.graphs(), base.incidents(), base.recommendations());
        assertEquals(Verdict.FAILED, engine().generate(state(saturated, false, null)).verdict());
    }

    @Test
    void allAgentsAreKeptAndTheirPercentilesAreNeverAveraged() throws Exception {
        var first = state(definition(List.of()), false, null);
        var second = new InjectionResult(LOAD, "GenericSimulation", Duration.ofSeconds(10),
                1000, 900, 100, 10, 100, 50, 75, 100, 700, 900, 1000, 5, 300, directory, Map.of());
        var context = first.context().with("load", "agent-2", TaskResult.success(LOAD, "gatling",
                Duration.ofSeconds(10), Map.of("result", second, "cpu", 70)));
        var state = new ExecutionState(first.id(), first.scenarioId(), first.status(), first.phaseStatuses(),
                context, first.startedAt(), first.updatedAt(), first.plannedTaskCount(), first.tasks(),
                first.scenarioYaml(), first.scenarioDetails());
        var report = engine().generate(state);
        assertEquals(2, report.injectionResults().size());
        assertEquals(Verdict.FAILED, report.verdict());
        assertTrue(report.performance().sliSlo().stream().anyMatch(c -> c.metric().equals("p95Ms") && c.actual() == 700));
        assertTrue(report.performance().sliSlo().stream().anyMatch(c -> c.metric().equals("errorRate") && c.actual() == 5));
    }

    @Test
    void exportedGraphIsEmbeddedInHtmlAndPdfWithoutFetchingTheDashboard() throws Exception {
        var image = new java.awt.image.BufferedImage(100, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(image, "png", bytes));
        String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        var spec = definition(List.of());
        var imageSpec = new ReportDefinition(spec.components(), spec.environment(), spec.objective(), spec.testType(),
                spec.loadDescription(), spec.assumptions(), spec.criteria(), spec.observations(),
                List.of(new ReportDefinition.Graph("CPU export <script>", null, "Grafana PNG export", uri)),
                spec.incidents(), spec.recommendations());
        var report = engine().generate(state(imageSpec, true, null));
        var renderer = new HtmlReportRenderer();
        var html = new String(renderer.render(report), StandardCharsets.UTF_8);
        assertTrue(html.contains(uri));
        assertTrue(html.contains("CPU export &lt;script&gt;"));
        assertFalse(html.contains("<script>"));
        boolean foundImage = false;
        try (var pdf = PDDocument.load(new PdfReportRenderer(renderer).render(report))) {
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getXObjectNames()) {
                    if (page.getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        foundImage = true;
                    }
                }
            }
        }
        assertTrue(foundImage, "Supplied graph must be present in PDF, not merely named");
        assertThrows(IllegalArgumentException.class, () ->
                new ReportDefinition.Graph("bad", null, "export", "data:image/svg+xml;base64,PHN2Zz4="));
    }

    @Test
    void requiredMissingMeasurementAndSkippedCheckCannotProduceSuccess() throws Exception {
        var base = definition(List.of());
        var observations = new ArrayList<>(base.observations());
        observations.add(new ReportDefinition.Observation("RAM", "missing-monitor", "ram.max",
                null, "MB", null, true));
        var spec = new ReportDefinition(base.components(), base.environment(), base.objective(), base.testType(),
                base.loadDescription(), base.assumptions(), base.criteria(), observations,
                base.graphs(), base.incidents(), base.recommendations());
        var report = engine().generate(state(spec, true, null));
        assertEquals(Verdict.WARNING, report.verdict());
        assertTrue(report.performance().validation().limitations().contains("Required measurement not collected: RAM"));
        assertEquals(Verdict.WARNING, engine().generate(state(base, true, AssertionStatus.SKIPPED)).verdict());
        var minor = new ReportDefinition.Criterion("Minor", "assertion", "slo", "==", 1.0, false);
        assertEquals(Verdict.FAILED, engine().generate(state(definition(List.of(minor)), true, AssertionStatus.ERROR)).verdict());
    }
}
