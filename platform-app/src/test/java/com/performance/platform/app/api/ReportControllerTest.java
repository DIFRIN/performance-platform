package com.performance.platform.app.api;

import com.performance.platform.reporting.output.ReportProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ReportController")
class ReportControllerTest {

    private static final String EXEC_ID = "exec-001";
    private static final String EXEC_ID_NO_REPORT = "exec-999";
    private static final byte[] HTML_CONTENT = "<html><body>Report</body></html>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF_CONTENT = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] JSON_CONTENT = "{\"report\":true}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        // Create report files for exec-001
        Path execDir = tempDir.resolve(EXEC_ID);
        Files.createDirectories(execDir);
        Files.write(execDir.resolve("campaign.html"), HTML_CONTENT);
        Files.write(execDir.resolve("campaign.pdf"), PDF_CONTENT);
        Files.write(execDir.resolve("campaign.json"), JSON_CONTENT);

        var props = new ReportProperties(tempDir.toString(), null);
        var controller = new ReportController(props);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /executions/{id}/report")
    class GetReport {

        @Nested
        @DisplayName("format=html (default)")
        class HtmlFormat {

            @Test
            @DisplayName("should return 200 with text/html content type")
            void shouldReturn200WithHtmlContentType() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.TEXT_HTML))
                        .andExpect(content().bytes(HTML_CONTENT));
            }

            @Test
            @DisplayName("should return 200 when format=html is explicit")
            void shouldReturn200WithExplicitHtmlFormat() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "html"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.TEXT_HTML))
                        .andExpect(content().bytes(HTML_CONTENT));
            }
        }

        @Nested
        @DisplayName("format=pdf")
        class PdfFormat {

            @Test
            @DisplayName("should return 200 with application/pdf content type")
            void shouldReturn200WithPdfContentType() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "pdf"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                        .andExpect(content().bytes(PDF_CONTENT));
            }

            @Test
            @DisplayName("should accept uppercase PDF")
            void shouldAcceptUppercasePdf() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "PDF"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_PDF));
            }
        }

        @Nested
        @DisplayName("format=json")
        class JsonFormat {

            @Test
            @DisplayName("should return 200 with application/json content type")
            void shouldReturn200WithJsonContentType() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "json"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(content().bytes(JSON_CONTENT));
            }

            @Test
            @DisplayName("should accept uppercase JSON")
            void shouldAcceptUppercaseJson() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "JSON"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            }
        }

        @Nested
        @DisplayName("404 — file not found")
        class NotFound {

            @Test
            @DisplayName("should return 404 when execution directory does not exist")
            void shouldReturn404WhenExecutionDirMissing() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", "exec-nonexistent"))
                        .andExpect(status().isNotFound());
            }

            @Test
            @DisplayName("should return 404 when report file does not exist for valid execution")
            void shouldReturn404WhenReportFileMissing() throws Exception {
                // EXEC_ID_NO_REPORT has no directory at all
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID_NO_REPORT)
                                .param("format", "html"))
                        .andExpect(status().isNotFound());
            }

            @Test
            @DisplayName("should return 404 when requested format file does not exist but others do")
            void shouldReturn404WhenSpecificFormatMissing() throws Exception {
                // Create a directory for a partial-report exec
                Path partialDir = tempDir.resolve("exec-partial");
                Files.createDirectories(partialDir);
                Files.write(partialDir.resolve("campaign.html"), HTML_CONTENT);
                // No campaign.pdf file

                mockMvc.perform(get("/api/v1/executions/{id}/report", "exec-partial")
                                .param("format", "pdf"))
                        .andExpect(status().isNotFound());
            }
        }

        @Nested
        @DisplayName("400 — invalid format")
        class InvalidFormat {

            @Test
            @DisplayName("should return 400 for unrecognized format")
            void shouldReturn400ForUnrecognizedFormat() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "xml"))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("should return 400 for numeric format")
            void shouldReturn400ForNumericFormat() throws Exception {
                mockMvc.perform(get("/api/v1/executions/{id}/report", EXEC_ID)
                                .param("format", "123"))
                        .andExpect(status().isBadRequest());
            }
        }

        @Nested
        @DisplayName("400 — path traversal protection")
        class PathTraversal {

            @Test
            @DisplayName("should return 400 when executionId is just '..'")
            void shouldReturn400ForDoubleDots() throws Exception {
                // ".." is a single path segment — reaches the controller,
                // then rejected by contains("..") check
                mockMvc.perform(get("/api/v1/executions/{id}/report", "..")
                                .param("format", "html"))
                        .andExpect(status().isBadRequest());
            }
        }
    }
}
