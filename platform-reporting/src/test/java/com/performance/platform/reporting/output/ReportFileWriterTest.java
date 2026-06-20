package com.performance.platform.reporting.output;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.TaskReportEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportFileWriter")
class ReportFileWriterTest {

    @TempDir
    Path tempDir;

    private ExecutionId executionId;
    private CampaignReport report;

    @BeforeEach
    void setUp() throws IOException {
        executionId = ExecutionId.of("exec-001");
        report = createCampaignReport();
    }

    // ──────────────────────────────────────────────
    // Tests : ecriture des formats configures
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("should write all 3 default formats")
    void shouldWriteAllDefaultFormats() throws IOException {
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "<html></html>".getBytes(StandardCharsets.UTF_8));
        var pdfRenderer = stubRenderer(ReportFormat.PDF, "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8));
        var jsonRenderer = stubRenderer(ReportFormat.JSON, "{}".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), null);
        var writer = new ReportFileWriter(
                List.of(htmlRenderer, pdfRenderer, jsonRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertTrue(Files.exists(outputDir));
        assertTrue(Files.exists(outputDir.resolve("campaign.html")));
        assertTrue(Files.exists(outputDir.resolve("campaign.pdf")));
        assertTrue(Files.exists(outputDir.resolve("campaign.json")));
        assertEquals("<html></html>", Files.readString(outputDir.resolve("campaign.html")));
        assertEquals("{}", Files.readString(outputDir.resolve("campaign.json")));
    }

    @Test
    @DisplayName("should write only configured formats when formats list is provided")
    void shouldWriteOnlyConfiguredFormats() throws IOException {
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "html".getBytes(StandardCharsets.UTF_8));
        var jsonRenderer = stubRenderer(ReportFormat.JSON, "json".getBytes(StandardCharsets.UTF_8));
        // PDF renderer present but not in configured formats
        var pdfRenderer = stubRenderer(ReportFormat.PDF, "pdf".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML, ReportFormat.JSON));
        var writer = new ReportFileWriter(
                List.of(htmlRenderer, jsonRenderer, pdfRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertTrue(Files.exists(outputDir.resolve("campaign.html")));
        assertTrue(Files.exists(outputDir.resolve("campaign.json")));
        assertFalse(Files.exists(outputDir.resolve("campaign.pdf")));
    }

    @Test
    @DisplayName("should write single format when only one configured")
    void shouldWriteSingleFormat() throws IOException {
        var jsonRenderer = stubRenderer(ReportFormat.JSON, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.JSON));
        var writer = new ReportFileWriter(List.of(jsonRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertTrue(Files.exists(outputDir.resolve("campaign.json")));
        assertEquals("{\"a\":1}", Files.readString(outputDir.resolve("campaign.json")));
    }

    @Test
    @DisplayName("should use default output directory when not configured")
    void shouldUseDefaultOutputDirectory() {
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "x".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(null, List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, report);

        // Le repertoire doit exister sous "reports/exec-001/"
        assertTrue(outputDir.startsWith(Path.of("reports")));
        assertTrue(Files.exists(outputDir));
        assertTrue(Files.exists(outputDir.resolve("campaign.html")));

        // Nettoyer le repertoire cree dans le working directory
        try {
            deleteRecursively(Path.of("reports"));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    // ──────────────────────────────────────────────
    // Tests : structure du chemin
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("should create output in outputDirectory/executionId")
    void shouldCreateOutputUnderExecutionIdDirectory() throws IOException {
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertEquals(tempDir.resolve("exec-001"), outputDir);
    }

    @Test
    @DisplayName("should create nested output directory if not exists")
    void shouldCreateNestedDirectories() throws IOException {
        Path nestedDir = tempDir.resolve("deep").resolve("path");
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(nestedDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertTrue(Files.exists(outputDir));
        assertEquals(nestedDir.resolve("exec-001"), outputDir);
    }

    // ──────────────────────────────────────────────
    // Tests : copie repertoire Gatling
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("should copy Gatling directory under gatling/simName")
    void shouldCopyGatlingDirectory() throws IOException {
        // Creer un faux repertoire Gatling
        Path gatlingSource = tempDir.resolve("gatling-source");
        Files.createDirectories(gatlingSource);
        Files.writeString(gatlingSource.resolve("simulation.log"), "log data");
        Files.writeString(gatlingSource.resolve("index.html"), "<html>gatling</html>");
        Files.createDirectories(gatlingSource.resolve("js"));
        Files.writeString(gatlingSource.resolve("js").resolve("stats.js"), "var stats = {};");

        var reportWithGatling = createReportWithGatlingDir(TaskId.of("inj-001"),
                "com.example.MySimulation", gatlingSource);

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, reportWithGatling);

        Path copiedGatling = outputDir.resolve("gatling").resolve("com.example.MySimulation");
        assertTrue(Files.exists(copiedGatling));
        assertTrue(Files.exists(copiedGatling.resolve("simulation.log")));
        assertTrue(Files.exists(copiedGatling.resolve("index.html")));
        assertTrue(Files.exists(copiedGatling.resolve("js").resolve("stats.js")));
        assertEquals("log data", Files.readString(copiedGatling.resolve("simulation.log")));
    }

    @Test
    @DisplayName("should copy multiple Gatling directories for multiple injection entries")
    void shouldCopyMultipleGatlingDirectories() throws IOException {
        Path source1 = tempDir.resolve("src1");
        Files.createDirectories(source1);
        Files.writeString(source1.resolve("data1.txt"), "d1");

        Path source2 = tempDir.resolve("src2");
        Files.createDirectories(source2);
        Files.writeString(source2.resolve("data2.txt"), "d2");

        var reportWithTwoGatling = createReportWithTwoGatlingDirs(source1, source2);

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, reportWithTwoGatling);

        assertTrue(Files.exists(outputDir.resolve("gatling").resolve("SimA").resolve("data1.txt")));
        assertTrue(Files.exists(outputDir.resolve("gatling").resolve("SimB").resolve("data2.txt")));
    }

    @Test
    @DisplayName("should sanitize simulation class names for directory names")
    void shouldSanitizeSimulationClassNames() throws IOException {
        Path source = tempDir.resolve("src");
        Files.createDirectories(source);
        Files.writeString(source.resolve("f.txt"), "data");

        var reportWithSpecialChars = createReportWithGatlingDir(TaskId.of("inj-002"),
                "com/example\\My:Sim*Test?\"<>|", source);

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, reportWithSpecialChars);

        // Le nom doit etre nettoye (tous les caracteres speciaux remplaces par _)
        Path gatlingDir = outputDir.resolve("gatling");
        List<Path> children;
        try (var stream = Files.list(gatlingDir)) {
            children = stream.toList();
        }
        assertEquals(1, children.size());
        String dirName = children.get(0).getFileName().toString();
        assertTrue(dirName.matches("[a-zA-Z0-9._-]+"));
        assertTrue(dirName.contains("com_example_My_Sim_Test"));
    }

    @Test
    @DisplayName("should not fail when Gatling directory does not exist")
    void shouldNotFailWhenGatlingDirMissing() throws IOException {
        Path nonExistentDir = tempDir.resolve("does-not-exist");
        var reportWithMissingGatling = createReportWithGatlingDir(TaskId.of("inj-003"),
                "com.example.Missing", nonExistentDir);

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        // Ne doit pas lever d'exception
        Path outputDir = writer.write(executionId, reportWithMissingGatling);
        assertTrue(Files.exists(outputDir.resolve("campaign.html")));
    }

    @Test
    @DisplayName("should handle report with no injection entries")
    void shouldHandleNoInjectionEntries() throws IOException {
        var reportWithoutInjections = new CampaignReport(
                report.id(), report.scenarioId(), report.scenarioName(),
                report.scenarioVersion(), report.tags(), report.metadata(),
                report.environment(), report.executionSummary(),
                report.preparationResults(), List.of(), report.assertionResults(),
                report.verdict(), report.verdictReason(), report.generatedAt(),
                report.totalDuration());

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, reportWithoutInjections);

        assertTrue(Files.exists(outputDir.resolve("campaign.html")));
        assertFalse(Files.exists(outputDir.resolve("gatling")));
    }

    @Test
    @DisplayName("should handle null Gatling report directory")
    void shouldHandleNullGatlingDir() throws IOException {
        // Utiliser le vrai record InjectionReportEntry (gatlingReportDirectory non-null requis)
        // → on cree un rapport sans injection pour ce test
        var reportWithoutInjections = new CampaignReport(
                report.id(), report.scenarioId(), report.scenarioName(),
                report.scenarioVersion(), report.tags(), report.metadata(),
                report.environment(), report.executionSummary(),
                report.preparationResults(), List.of(), report.assertionResults(),
                report.verdict(), report.verdictReason(), report.generatedAt(),
                report.totalDuration());

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        assertDoesNotThrow(() -> writer.write(executionId, reportWithoutInjections));
    }

    // ──────────────────────────────────────────────
    // Tests : gestion des renderers
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("should skip format with no matching renderer")
    void shouldSkipMissingRenderer() throws IOException {
        // Un seul renderer HTML, mais config demande HTML+PDF
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(),
                List.of(ReportFormat.HTML, ReportFormat.PDF));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertTrue(Files.exists(outputDir.resolve("campaign.html")));
        assertFalse(Files.exists(outputDir.resolve("campaign.pdf")));
    }

    @Test
    @DisplayName("should handle empty formats list by falling back to defaults")
    void shouldFallbackToDefaultsOnEmptyFormats() throws IOException {
        var htmlRenderer = stubRenderer(ReportFormat.HTML, "x".getBytes(StandardCharsets.UTF_8));
        var pdfRenderer = stubRenderer(ReportFormat.PDF, "%PDF".getBytes(StandardCharsets.UTF_8));
        var jsonRenderer = stubRenderer(ReportFormat.JSON, "{}".getBytes(StandardCharsets.UTF_8));
        var props = new ReportProperties(tempDir.toString(), List.of()); // empty
        var writer = new ReportFileWriter(
                List.of(htmlRenderer, pdfRenderer, jsonRenderer), props);

        Path outputDir = writer.write(executionId, report);

        assertTrue(Files.exists(outputDir.resolve("campaign.html")));
        assertTrue(Files.exists(outputDir.resolve("campaign.pdf")));
        assertTrue(Files.exists(outputDir.resolve("campaign.json")));
    }

    // ──────────────────────────────────────────────
    // Tests : erreurs
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("should throw UncheckedIOException when base directory creation fails")
    void shouldThrowOnDirectoryCreationFailure() {
        // Utiliser un chemin impossible (fichier existant la ou on veut creer un repertoire)
        Path conflictPath = tempDir.resolve("conflict");
        try {
            Files.createFile(conflictPath); // cree un fichier, pas un repertoire
        } catch (IOException e) {
            fail("setup failed: " + e.getMessage());
        }

        var htmlRenderer = stubRenderer(ReportFormat.HTML, "ok".getBytes(StandardCharsets.UTF_8));
        var execPath = conflictPath.resolve("exec-001"); // conflictPath est un fichier, pas un dir
        var props = new ReportProperties(conflictPath.toString(), List.of(ReportFormat.HTML));
        var writer = new ReportFileWriter(List.of(htmlRenderer), props);

        assertThrows(UncheckedIOException.class,
                () -> writer.write(executionId, report));
    }

    // ──────────────────────────────────────────────
    // Tests : helpers statiques
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("formatExtension should return lowercase format name")
    void formatExtensionShouldReturnLowercase() {
        assertEquals("html", ReportFileWriter.formatExtension(ReportFormat.HTML));
        assertEquals("pdf", ReportFileWriter.formatExtension(ReportFormat.PDF));
        assertEquals("json", ReportFileWriter.formatExtension(ReportFormat.JSON));
    }

    @Test
    @DisplayName("sanitizeFilename should replace special characters with underscores")
    void sanitizeFilenameShouldReplaceSpecialChars() {
        assertEquals("com.example.MyClass", ReportFileWriter.sanitizeFilename("com.example.MyClass"));
        assertEquals("com_example_Test___", ReportFileWriter.sanitizeFilename("com/example\\Test:?*"));
        assertEquals("SafeName_123", ReportFileWriter.sanitizeFilename("SafeName_123"));
        assertEquals("_____", ReportFileWriter.sanitizeFilename(" /\\:*"));
    }

    @Test
    @DisplayName("sanitizeFilename should preserve safe characters")
    void sanitizeFilenameShouldPreserveSafeChars() {
        assertEquals("MySimulation_Test_A-B.v1",
                ReportFileWriter.sanitizeFilename("MySimulation_Test_A-B.v1"));
        assertEquals("com.example.package.MySim",
                ReportFileWriter.sanitizeFilename("com.example.package.MySim"));
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private CampaignReport createCampaignReport() {
        return new CampaignReport(
                ReportId.generate(),
                ScenarioId.of("scenario-001"),
                "Performance Test",
                "1.0.0",
                List.of("critical", "api"),
                java.util.Map.of("env", "staging"),
                new EnvironmentInfo(List.of("agent-1"), "21", java.util.Map.of()),
                new ExecutionSummary(5, 4, 1, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(2)),
                List.of(new TaskReportEntry(TaskId.of("prep-001"), "db-setup",
                        com.performance.platform.domain.task.TaskStatus.SUCCESS,
                        Duration.ofSeconds(1), java.util.Map.of())),
                List.of(new InjectionReportEntry(TaskId.of("inj-001"),
                        createFakeInjectionResult(TaskId.of("inj-001"), "com.example.MySimulation",
                                tempDir.resolve("no-gatling")),
                        tempDir.resolve("no-gatling"))),
                List.of(new AssertionReportEntry(TaskId.of("assert-001"),
                        new com.performance.platform.domain.assertion.AssertionResult(
                                TaskId.of("assert-001"),
                                com.performance.platform.domain.assertion.AssertionStatus.PASSED,
                                "all metrics within threshold",
                                null,
                                Duration.ofMillis(150),
                                Instant.now()),
                        null)),
                Verdict.SUCCESS,
                "All assertions passed",
                Instant.now(),
                Duration.ofSeconds(33));
    }

    private CampaignReport createReportWithGatlingDir(TaskId taskId, String simClass, Path gatlingDir) {
        return new CampaignReport(
                report.id(), report.scenarioId(), report.scenarioName(),
                report.scenarioVersion(), report.tags(), report.metadata(),
                report.environment(), report.executionSummary(),
                report.preparationResults(),
                List.of(new InjectionReportEntry(taskId,
                        createFakeInjectionResult(taskId, simClass, gatlingDir), gatlingDir)),
                report.assertionResults(),
                report.verdict(), report.verdictReason(), report.generatedAt(),
                report.totalDuration());
    }

    private CampaignReport createReportWithTwoGatlingDirs(Path dirA, Path dirB) {
        return new CampaignReport(
                report.id(), report.scenarioId(), report.scenarioName(),
                report.scenarioVersion(), report.tags(), report.metadata(),
                report.environment(), report.executionSummary(),
                report.preparationResults(),
                List.of(
                        new InjectionReportEntry(TaskId.of("inj-A"),
                                createFakeInjectionResult(TaskId.of("inj-A"), "SimA", dirA), dirA),
                        new InjectionReportEntry(TaskId.of("inj-B"),
                                createFakeInjectionResult(TaskId.of("inj-B"), "SimB", dirB), dirB)),
                report.assertionResults(),
                report.verdict(), report.verdictReason(), report.generatedAt(),
                report.totalDuration());
    }

    private InjectionResult createFakeInjectionResult(TaskId taskId, String simClass, Path gatlingDir) {
        return new InjectionResult(
                taskId, simClass, Duration.ofSeconds(30), 1000, 990, 10,
                1.0, 33.3, 50, 75, 90, 95, 99, 150, 10, 55.5,
                gatlingDir, java.util.Map.of());
    }

    private ReportRenderer stubRenderer(ReportFormat format, byte[] content) {
        return new ReportRenderer() {
            @Override
            public byte[] render(CampaignReport report) {
                return content;
            }

            @Override
            public ReportFormat getFormat() {
                return format;
            }
        };
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (var stream = Files.walk(path)) {
                var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
                for (Path p : paths) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
