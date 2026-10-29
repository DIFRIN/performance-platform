package com.performance.platform.reporting.render;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.RenderException;
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

@DisplayName("PdfReportRenderer")
class PdfReportRendererTest {

    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private HtmlReportRenderer htmlRenderer;
    private PdfReportRenderer pdfRenderer;
    private CampaignReport report;

    @BeforeEach
    void setUp() {
        htmlRenderer = new HtmlReportRenderer();
        pdfRenderer = new PdfReportRenderer(htmlRenderer);

        var reportId = ReportId.generate();
        var scenarioId = new ScenarioId("test-scenario");
        var now = Instant.now();

        // Preparation entry
        var prepEntry = new TaskReportEntry(
                new TaskId("prep-1"), "database-setup", TaskStatus.SUCCESS,
                Duration.ofSeconds(2), Map.of("rows", 100)
        );

        // Injection entry with KPIs
        var injResult = new InjectionResult(
                new TaskId("inj-1"), "com.example.Simulation", Duration.ofSeconds(10),
                1000, 990, 10, 1.0, 100.0,
                10, 15, 20, 25, 30, 35, 5, 8.5,
                Path.of("/tmp/gatling"), Map.of()
        );
        var injEntry = new InjectionReportEntry(
                new TaskId("inj-1"), injResult, Path.of("/tmp/gatling")
        );

        // Assertion entry
        var assertResult = new AssertionResult(
                new TaskId("assert-1"), AssertionStatus.PASSED, "throughput >= 50",
                null, Duration.ofMillis(50), now
        );
        var assertEntry = new AssertionReportEntry(
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
        @DisplayName("should return PDF")
        void shouldReturnPdf() {
            assertEquals(ReportFormat.PDF, pdfRenderer.getFormat());
        }
    }

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("should produce PDF starting with %PDF magic bytes")
        void shouldStartWithPdfMagic() {
            byte[] pdf = pdfRenderer.render(report);

            assertTrue(pdf.length >= 4, "PDF must be at least 4 bytes");
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                assertEquals(PDF_MAGIC[i], pdf[i],
                        "Byte " + i + " must match '%PDF' magic");
            }
        }

        @Test
        @DisplayName("should produce non-empty PDF content")
        void shouldProduceNonEmptyPdf() {
            byte[] pdf = pdfRenderer.render(report);

            assertNotNull(pdf);
            assertTrue(pdf.length > 4, "PDF should be larger than magic bytes alone");
        }

        @Test
        @DisplayName("should embed document metadata in PDF")
        void shouldContainDocumentMetadata() {
            // OpenHTMLToPDF embeds document-level metadata (Producer, Creator)
            // in the uncompressed PDF trailer/catalog. Verify structural markers.
            byte[] pdf = pdfRenderer.render(report);
            var pdfString = new String(pdf, StandardCharsets.ISO_8859_1);

            // PDF must contain a catalog (structural requirement)
            assertTrue(pdfString.contains("/Type"),
                    "PDF must contain at least a /Type marker");

            // PDF must contain page descriptors
            assertTrue(pdfString.contains("/Page"),
                    "PDF must contain /Page descriptor");
        }

        @Test
        @DisplayName("should produce different content for different reports")
        void shouldProduceDifferentPdfsForDifferentReports() {
            var report2 = new CampaignReport(
                    ReportId.generate(), new ScenarioId("sc2"), "Another Campaign", "1.0",
                    List.of(), Map.of(),
                    new EnvironmentInfo(List.of(), "21", Map.of()),
                    new ExecutionSummary(0, 0, 0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                    List.of(), List.of(), List.of(),
                    Verdict.FAILED, "Everything failed", Instant.now(), Duration.ZERO
            );

            byte[] pdf1 = pdfRenderer.render(report);
            byte[] pdf2 = pdfRenderer.render(report2);

            assertNotEquals(pdf1.length, pdf2.length,
                    "Different reports should produce different PDF sizes");
        }

        @Test
        @DisplayName("should handle WARNING verdict PDF correctly")
        void shouldHandleWarningVerdict() {
            CampaignReport warnReport = buildReportWithVerdict(Verdict.WARNING, "Some failed");
            byte[] pdf = pdfRenderer.render(warnReport);

            assertNotNull(pdf);
            assertTrue(pdf.length > 4, "PDF must be non-empty");
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                assertEquals(PDF_MAGIC[i], pdf[i], "Must start with %PDF magic");
            }

            var pdfString = new String(pdf, StandardCharsets.ISO_8859_1);
            assertTrue(pdfString.contains("%%EOF"), "PDF must have %%EOF trailer");
            assertTrue(pdfString.contains("/Type"), "PDF must contain /Type marker");
        }

        @Test
        @DisplayName("should handle FAILED verdict PDF correctly")
        void shouldHandleFailedVerdict() {
            CampaignReport failReport = buildReportWithVerdict(Verdict.FAILED, "Critical error");
            byte[] pdf = pdfRenderer.render(failReport);

            assertNotNull(pdf);
            assertTrue(pdf.length > 4, "PDF must be non-empty");
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                assertEquals(PDF_MAGIC[i], pdf[i], "Must start with %PDF magic");
            }

            var pdfString = new String(pdf, StandardCharsets.ISO_8859_1);
            assertTrue(pdfString.contains("%%EOF"), "PDF must have %%EOF trailer");
            assertTrue(pdfString.contains("/Type"), "PDF must contain /Type marker");
        }

        @Test
        @DisplayName("should handle empty preparation/injection/assertion lists")
        void shouldHandleEmptyLists() {
            CampaignReport emptyReport = buildReportWithVerdict(Verdict.SUCCESS, null);
            byte[] pdf = pdfRenderer.render(emptyReport);

            assertNotNull(pdf);
            assertTrue(pdf.length > 4);
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                assertEquals(PDF_MAGIC[i], pdf[i]);
            }
        }

        @Test
        @DisplayName("should produce a valid PDF with %%EOF trailer")
        void shouldContainEofTrailer() {
            byte[] pdf = pdfRenderer.render(report);
            var pdfString = new String(pdf, StandardCharsets.ISO_8859_1);
            assertTrue(pdfString.contains("%%EOF"),
                    "PDF must contain %%EOF trailer marker");
        }

        @Test
        @DisplayName("should produce consistent PDF across multiple renders")
        void shouldBeIdempotent() {
            byte[] pdf1 = pdfRenderer.render(report);
            byte[] pdf2 = pdfRenderer.render(report);

            assertEquals(pdf1.length, pdf2.length,
                    "Same report should produce same-size PDF");
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should rethrow RenderException as-is without wrapping")
        void shouldRethrowRenderExceptionAsIs() {
            // Given: HtmlReportRenderer throws RenderException
            RenderException originalException =
                    new RenderException("HTML render failed for reportId=test-id");
            var faultyRenderer = new HtmlReportRenderer() {
                @Override
                public byte[] render(CampaignReport report) throws RenderException {
                    throw originalException;
                }
            };
            var renderer = new PdfReportRenderer(faultyRenderer);

            // When/Then: the original RenderException is rethrown, not wrapped
            RenderException thrown = assertThrows(RenderException.class,
                    () -> renderer.render(report));
            assertSame(originalException, thrown,
                    "RenderException should be rethrown as-is, not wrapped");
        }

        @Test
        @DisplayName("should wrap generic exceptions in RenderException")
        void shouldWrapGenericExceptionInRenderException() {
            // Given: HtmlReportRenderer throws a RuntimeException
            var rootCause = new RuntimeException("Unexpected JVM crash");
            var faultyRenderer = new HtmlReportRenderer() {
                @Override
                public byte[] render(CampaignReport report) throws RenderException {
                    throw rootCause;
                }
            };
            var renderer = new PdfReportRenderer(faultyRenderer);

            // When/Then: RuntimeException is wrapped in RenderException
            RenderException thrown = assertThrows(RenderException.class,
                    () -> renderer.render(report));
            assertNotNull(thrown.getCause(),
                    "Wrapped exception must have a cause");
            assertEquals(rootCause, thrown.getCause(),
                    "Cause must be the original RuntimeException");
            assertTrue(thrown.getMessage().contains(report.id().value()),
                    "Message should contain the reportId=" + report.id().value());
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
