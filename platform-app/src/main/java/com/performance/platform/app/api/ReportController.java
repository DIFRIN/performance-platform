package com.performance.platform.app.api;

import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.reporting.output.ReportProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REST controller exposing already-generated execution reports.
 * <p>
 * This controller NEVER triggers report generation — reports are generated
 * automatically at the end of an execution lifecycle. It merely streams
 * pre-existing files from the output directory.
 * <p>
 * Endpoints :
 * <ul>
 *   <li>{@code GET /api/v1/executions/{id}/report?format=html|pdf|json} — stream report file</li>
 * </ul>
 *
 * <p>File layout (matching {@link com.performance.platform.reporting.output.ReportFileWriter}) :
 * <pre>
 * {@code <outputDirectory>/<executionId>/campaign.<html|pdf|json>}
 * </pre>
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private static final String REPORT_FILE_PREFIX = "campaign.";

    private static final MediaType CONTENT_TYPE_HTML = MediaType.TEXT_HTML;
    private static final MediaType CONTENT_TYPE_PDF = MediaType.APPLICATION_PDF;
    private static final MediaType CONTENT_TYPE_JSON = MediaType.APPLICATION_JSON;

    private final Path outputDirectory;

    public ReportController(ReportProperties props) {
        this.outputDirectory = Path.of(
                props.outputDirectory() != null ? props.outputDirectory() : "reports")
                .toAbsolutePath().normalize();
    }

    /**
     * Streams a pre-generated report file for the given execution.
     *
     * @param id     the execution identifier
     * @param format the report format (html, pdf, json), defaults to html
     * @return 200 OK with the report bytes and correct Content-Type,
     *         400 if format is invalid,
     *         404 if the report file does not exist yet
     */
    @GetMapping("/executions/{id}/report")
    public ResponseEntity<byte[]> getReport(
            @PathVariable("id") String id,
            @RequestParam(name = "format", defaultValue = "html") String format) {
        log.info("action=get_report executionId={} format={}", id, format);

        // Validate format
        ReportFormat reportFormat;
        try {
            reportFormat = ReportFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("action=invalid_format executionId={} format={}", id, format);
            return ResponseEntity.badRequest().build();
        }

        // Validate executionId: must not be empty and must not contain path separators
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains("..")) {
            log.warn("action=invalid_execution_id executionId={}", id);
            return ResponseEntity.badRequest().build();
        }

        // Resolve file path
        Path filePath = outputDirectory.resolve(id)
                .resolve(REPORT_FILE_PREFIX + reportFormat.name().toLowerCase())
                .normalize();

        // Path traversal protection: ensure resolved path is under outputDirectory
        if (!filePath.startsWith(outputDirectory)) {
            log.warn("action=path_traversal_blocked executionId={} path={}", id, filePath);
            return ResponseEntity.badRequest().build();
        }

        // Check file existence
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.info("action=report_not_found executionId={} format={} path={}",
                    id, format, filePath);
            return ResponseEntity.notFound().build();
        }

        // Read file bytes (I/O on Virtual Thread thanks to Spring Boot default executor)
        byte[] content;
        try {
            content = Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("action=report_read_error executionId={} format={} path={}",
                    id, format, filePath, e);
            throw new UncheckedIOException("Failed to read report file: " + filePath, e);
        }

        MediaType contentType = switch (reportFormat) {
            case HTML -> CONTENT_TYPE_HTML;
            case PDF -> CONTENT_TYPE_PDF;
            case JSON -> CONTENT_TYPE_JSON;
        };

        log.info("action=get_report_done executionId={} format={} size={}",
                id, format, content.length);

        return ResponseEntity.ok()
                .contentType(contentType)
                .body(content);
    }
}
