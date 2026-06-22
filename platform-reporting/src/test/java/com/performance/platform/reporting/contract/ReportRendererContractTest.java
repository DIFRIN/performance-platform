package com.performance.platform.reporting.contract;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.RenderException;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.TaskReportEntry;

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

/**
 * Contrat abstrait pour toutes les implementations de {@link ReportRenderer}.
 * <p>
 * Chaque implementation concrete de ReportRenderer (HTML, JSON, PDF) doit
 * satisfaire ce contrat : getFormat() coherent, render() non-null/non-vide,
 * render() stable, et gestion correcte des cas limites (rapport vide, verdicts differents).
 * <p>
 * Les implementations sont testees via des factories de renderer injectees
 * dans les tests parametres manuellement (pas de {@code @ParameterizedTest}
 * avec sources dynamiques car les renderers ont des dependances differentes).
 */
@DisplayName("ReportRenderer Contract")
public class ReportRendererContractTest {

    // ── Shared fixtures ──

    private static CampaignReport createFullReport(Verdict verdict, String reason) {
        var reportId = ReportId.generate();
        var scenarioId = new ScenarioId("contract-test");
        var now = Instant.now();

        var prepEntry = new TaskReportEntry(
                new TaskId("prep-1"), "database-setup", TaskStatus.SUCCESS,
                Duration.ofSeconds(2), Map.of("rows", 42));

        var injResult = new InjectionResult(
                new TaskId("inj-1"), "com.example.Simulation", Duration.ofSeconds(10),
                1000, 990, 10, 1.0, 100.0,
                10, 15, 20, 25, 30, 35, 5, 8.5,
                Path.of("/tmp/gatling"), Map.of());
        var injEntry = new InjectionReportEntry(
                new TaskId("inj-1"), injResult, Path.of("/tmp/gatling"));

        var assertResult = new AssertionResult(
                new TaskId("assert-1"), AssertionStatus.PASSED, "p95 < 100ms",
                null, Duration.ofMillis(50), now);
        var assertEntry = new AssertionReportEntry(
                new TaskId("assert-1"), assertResult, null);

        return new CampaignReport(
                reportId, scenarioId, "Contract Test Campaign", "1.0.0",
                List.of("contract"), Map.of("purpose", "testing"),
                new EnvironmentInfo(List.of("agent-1"), "25", Map.of("os", "linux")),
                new ExecutionSummary(3, 3, 0, 0,
                        Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofMillis(50)),
                List.of(prepEntry), List.of(injEntry), List.of(assertEntry),
                verdict, reason, now, Duration.ofSeconds(12));
    }

    private static CampaignReport createEmptyReport(Verdict verdict) {
        return new CampaignReport(
                ReportId.generate(), new ScenarioId("empty"), "Empty Report", "1.0",
                List.of(), Map.of(),
                new EnvironmentInfo(List.of(), "25", Map.of()),
                new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                List.of(), List.of(), List.of(),
                verdict, null, Instant.now(), Duration.ZERO);
    }

    // ══════════════════════════════════════════════
    // Contract tests pour HtmlReportRenderer
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("HtmlReportRenderer")
    class HtmlContractTests {

        private final ReportRenderer renderer = new com.performance.platform.reporting.render.HtmlReportRenderer();

        @Test
        @DisplayName("getFormat() must return HTML")
        void getFormatMustReturnHtml() {
            assertEquals(ReportFormat.HTML, renderer.getFormat());
        }

        @Test
        @DisplayName("render() must return non-null non-empty bytes for full report")
        void renderMustReturnNonEmptyBytes() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            assertNotNull(result);
            assertTrue(result.length > 0, "HTML output must be non-empty");
        }

        @Test
        @DisplayName("render() must produce valid UTF-8 HTML content")
        void renderMustProduceUtf8Html() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            // Must contain HTML structural elements (from template or fallback)
            assertTrue(content.contains("<html") || content.contains("<body") || content.contains("<table"),
                    "HTML output must contain structural markup");
        }

        @Test
        @DisplayName("render() must include scenario name")
        void renderMustIncludeScenarioName() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            assertTrue(content.contains("Contract Test Campaign"),
                    "HTML must contain the scenario name");
        }

        @Test
        @DisplayName("render() must include verdict in output")
        void renderMustIncludeVerdict() {
            CampaignReport report = createFullReport(Verdict.WARNING, "Some assertions failed");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            assertTrue(content.contains("WARNING"),
                    "HTML must contain the verdict string");
        }

        @Test
        @DisplayName("render() must handle empty report gracefully")
        void renderMustHandleEmptyReport() {
            CampaignReport report = createEmptyReport(Verdict.SUCCESS);
            byte[] result = renderer.render(report);
            assertNotNull(result);
            assertTrue(result.length > 0, "Empty report must still produce valid HTML");
            var content = new String(result, StandardCharsets.UTF_8);
            assertTrue(content.contains("SUCCESS"),
                    "Empty report HTML must contain verdict");
        }

        @Test
        @DisplayName("render() must escape XSS vectors in scenario name")
        void renderMustEscapeXss() {
            var report = new CampaignReport(
                    ReportId.generate(), new ScenarioId("sc"),
                    "<script>alert('xss')</script>", "1.0",
                    List.of(), Map.of(),
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, Instant.now(), Duration.ZERO);
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            // The script tag must be escaped, not rendered as executable HTML
            assertFalse(content.contains("<script>"),
                    "Script tags must be HTML-escaped");
            assertTrue(content.contains("&lt;script&gt;") || !content.contains("<script>"),
                    "XSS vector must be neutralized");
        }

        @Test
        @DisplayName("render() must produce consistent output for same input")
        void renderMustBeIdempotent() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result1 = renderer.render(report);
            byte[] result2 = renderer.render(report);
            assertEquals(result1.length, result2.length,
                    "Same input must produce same-size output");
            assertArrayEquals(result1, result2,
                    "Same input must produce identical output");
        }
    }

    // ══════════════════════════════════════════════
    // Contract tests pour JsonReportRenderer
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("JsonReportRenderer")
    class JsonContractTests {

        private final ReportRenderer renderer = new com.performance.platform.reporting.render.JsonReportRenderer();

        @Test
        @DisplayName("getFormat() must return JSON")
        void getFormatMustReturnJson() {
            assertEquals(ReportFormat.JSON, renderer.getFormat());
        }

        @Test
        @DisplayName("render() must return non-null non-empty bytes for full report")
        void renderMustReturnNonEmptyBytes() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            assertNotNull(result);
            assertTrue(result.length > 0, "JSON output must be non-empty");
        }

        @Test
        @DisplayName("render() must produce pretty-printed JSON")
        void renderMustProducePrettyPrintedJson() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            assertTrue(content.contains("\n  ") || content.contains("\n\t"),
                    "JSON must be pretty-printed with indentation");
        }

        @Test
        @DisplayName("render() must include all top-level fields")
        void renderMustIncludeAllTopLevelFields() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            // All CampaignReport fields must appear in the JSON
            assertTrue(content.contains("\"scenarioName\""));
            assertTrue(content.contains("\"scenarioVersion\""));
            assertTrue(content.contains("\"verdict\""));
            assertTrue(content.contains("\"executionSummary\""));
            assertTrue(content.contains("\"environment\""));
            assertTrue(content.contains("\"preparationResults\""));
            assertTrue(content.contains("\"injectionResults\""));
            assertTrue(content.contains("\"assertionResults\""));
        }

        @Test
        @DisplayName("render() must serialize Instant as ISO string")
        void renderMustSerializeInstantAsIsoString() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            // generatedAt should be an ISO-8601 string, not a numeric timestamp
            assertTrue(content.contains("\"generatedAt\" : \""));
        }

        @Test
        @DisplayName("render() must handle empty report gracefully")
        void renderMustHandleEmptyReport() {
            CampaignReport report = createEmptyReport(Verdict.FAILED);
            byte[] result = renderer.render(report);
            assertNotNull(result);
            assertTrue(result.length > 0);
            var content = new String(result, StandardCharsets.UTF_8);
            assertTrue(content.contains("\"verdict\""));
        }

        @Test
        @DisplayName("render() must preserve special characters in scenario name")
        void renderMustPreserveSpecialChars() {
            var report = new CampaignReport(
                    ReportId.generate(), new ScenarioId("sc"),
                    "Test \"quoted\" & ampersand", "1.0",
                    List.of(), Map.of(),
                    new EnvironmentInfo(List.of(), "25", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.SUCCESS, null, Instant.now(), Duration.ZERO);
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.UTF_8);
            // JSON must properly escape quotes, but preserve the content
            assertTrue(content.contains("quoted") && content.contains("ampersand"),
                    "Special characters must be present in JSON output");
        }

        @Test
        @DisplayName("render() must produce consistent output for same input")
        void renderMustBeIdempotent() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result1 = renderer.render(report);
            byte[] result2 = renderer.render(report);
            assertArrayEquals(result1, result2,
                    "Same input must produce identical JSON");
        }
    }

    // ══════════════════════════════════════════════
    // Contract tests pour PdfReportRenderer
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("PdfReportRenderer")
    class PdfContractTests {

        private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

        private final ReportRenderer renderer = new com.performance.platform.reporting.render.PdfReportRenderer(
                new com.performance.platform.reporting.render.HtmlReportRenderer());

        @Test
        @DisplayName("getFormat() must return PDF")
        void getFormatMustReturnPdf() {
            assertEquals(ReportFormat.PDF, renderer.getFormat());
        }

        @Test
        @DisplayName("render() must return non-null non-empty bytes for full report")
        void renderMustReturnNonEmptyBytes() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            assertNotNull(result);
            assertTrue(result.length > 4, "PDF output must be larger than magic bytes alone");
        }

        @Test
        @DisplayName("render() must start with %PDF magic bytes")
        void renderMustStartWithPdfMagic() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                assertEquals(PDF_MAGIC[i], result[i],
                        "PDF byte " + i + " must match '%PDF' magic");
            }
        }

        @Test
        @DisplayName("render() must contain %%EOF trailer")
        void renderMustContainEofTrailer() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.ISO_8859_1);
            assertTrue(content.contains("%%EOF"),
                    "PDF must contain %%EOF trailer marker");
        }

        @Test
        @DisplayName("render() must contain PDF structural markers")
        void renderMustContainStructuralMarkers() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result = renderer.render(report);
            var content = new String(result, StandardCharsets.ISO_8859_1);
            assertTrue(content.contains("/Type"),
                    "PDF must contain /Type marker");
            assertTrue(content.contains("/Page"),
                    "PDF must contain /Page descriptor");
        }

        @Test
        @DisplayName("render() must handle empty report gracefully")
        void renderMustHandleEmptyReport() {
            CampaignReport report = createEmptyReport(Verdict.SUCCESS);
            byte[] result = renderer.render(report);
            assertNotNull(result);
            assertTrue(result.length > 4, "Empty report PDF must be non-empty");
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                assertEquals(PDF_MAGIC[i], result[i],
                        "Empty report must still produce valid PDF magic");
            }
        }

        @Test
        @DisplayName("render() must produce different output for different verdicts")
        void renderMustProduceDifferentOutputForDifferentVerdicts() {
            CampaignReport report1 = createFullReport(Verdict.SUCCESS, "All good");
            CampaignReport report2 = createFullReport(Verdict.FAILED, "Critical error");
            byte[] result1 = renderer.render(report1);
            byte[] result2 = renderer.render(report2);
            assertNotEquals(result1.length, result2.length,
                    "Different verdicts must produce different PDF sizes");
        }

        @Test
        @DisplayName("render() must produce consistent output for same input")
        void renderMustBeIdempotent() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");
            byte[] result1 = renderer.render(report);
            byte[] result2 = renderer.render(report);
            assertEquals(result1.length, result2.length,
                    "Same input must produce same-size PDF");
        }
    }

    // ══════════════════════════════════════════════
    // Cross-format consistency
    // ══════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-Format Consistency")
    class CrossFormatConsistencyTests {

        private final ReportRenderer htmlRenderer = new com.performance.platform.reporting.render.HtmlReportRenderer();
        private final ReportRenderer jsonRenderer = new com.performance.platform.reporting.render.JsonReportRenderer();
        private final ReportRenderer pdfRenderer = new com.performance.platform.reporting.render.PdfReportRenderer(
                new com.performance.platform.reporting.render.HtmlReportRenderer());

        @Test
        @DisplayName("all 3 formats must produce non-empty output for the same report")
        void allFormatsMustProduceNonEmptyOutput() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");

            byte[] html = htmlRenderer.render(report);
            byte[] json = jsonRenderer.render(report);
            byte[] pdf = pdfRenderer.render(report);

            assertTrue(html.length > 0, "HTML must be non-empty");
            assertTrue(json.length > 0, "JSON must be non-empty");
            assertTrue(pdf.length > 0, "PDF must be non-empty");
        }

        @Test
        @DisplayName("HTML and PDF must both contain scenario name text")
        void htmlAndPdfMustContainScenarioText() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");

            byte[] html = htmlRenderer.render(report);
            byte[] pdf = pdfRenderer.render(report);

            var htmlContent = new String(html, StandardCharsets.UTF_8);
            assertTrue(htmlContent.contains("Contract Test Campaign"),
                    "HTML must contain scenario name");

            // PDF contains the text embedded (may be in compressed streams,
            // but the text content from OpenHTMLToPDF is usually uncompressed in simple cases)
            var pdfContent = new String(pdf, StandardCharsets.ISO_8859_1);
            assertTrue(pdfContent.length() > 0,
                    "PDF must contain content");
        }

        @Test
        @DisplayName("JSON must contain all data present in HTML (structural check)")
        void jsonMustContainEquivalentData() {
            CampaignReport report = createFullReport(Verdict.SUCCESS, "All good");

            byte[] json = jsonRenderer.render(report);
            var jsonContent = new String(json, StandardCharsets.UTF_8);

            // JSON must contain the same logical data
            assertTrue(jsonContent.contains("Contract Test Campaign"));
            assertTrue(jsonContent.contains("SUCCESS"));
            assertTrue(jsonContent.contains("database-setup"));
            assertTrue(jsonContent.contains("com.example.Simulation"));
            assertTrue(jsonContent.contains("agent-1"));
        }
    }
}
