package com.performance.platform.infrastructure.publisher.confluence;

import com.performance.platform.reporting.PublicationException;
import com.performance.platform.reporting.PublicationTarget;
import com.performance.platform.reporting.ReportPublisher;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.PublisherConfig;
import com.performance.platform.reporting.model.TaskReportEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Publishes the campaign report as a Confluence wiki page
 * via the Confluence REST API.
 *
 * <p>Configuration is read from {@link PublisherConfig#properties()}:
 * <ul>
 *   <li>{@code url} — base URL of the Confluence instance (required)</li>
 *   <li>{@code spaceKey} — Confluence space key (required)</li>
 *   <li>{@code parentPageId} — parent page ID for hierarchy (optional)</li>
 *   <li>{@code token} — Confluence API token (required, never logged)</li>
 * </ul>
 * The token should be sourced from an environment variable or secret
 * manager, never hardcoded in application configuration.</p>
 *
 * <p>All HTTP I/O executes under Virtual Threads
 * ({@link HttpClient} with the default Java HTTP client which uses
 * non-blocking I/O underneath).</p>
 *
 * <p><b>CC-02:</b> Pipeline cohesif de publication Confluence —
 * validation configuration → construction payload JSON
 * ({@link #buildConfluencePayload}) → construction body HTML
 * ({@link #buildStorageBody}) → appel HTTP POST → gestion
 * réponse/erreur. Les helpers d'échappement
 * ({@link #escapeJson}, {@link #escapeHtml}) et de formatage
 * ({@link #formatDuration}, {@link #verdictColor}) sont
 * indissociables du flux de publication. Extraire une portion
 * isolée nuirait à la lisibilité du pipeline séquentiel.</p>
 */
@Component
public class ConfluenceReportPublisher implements ReportPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceReportPublisher.class);

    private static final String API_PATH = "/wiki/rest/api/content";
    private static final int HTTP_TIMEOUT_SECONDS = 30;

    /** Configuration property keys (package-visible for test references). */
    static final String KEY_URL = "url";
    static final String KEY_SPACE_KEY = "spaceKey";
    static final String KEY_TOKEN = "token";
    static final String KEY_PARENT_PAGE_ID = "parentPageId";

    private final HttpClient httpClient;

    /** Public no-arg constructor for Spring + test visibility. */
    public ConfluenceReportPublisher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();
    }

    /** Package-visible constructor for testing with a custom HttpClient. */
    ConfluenceReportPublisher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public PublicationTarget getTarget() {
        return PublicationTarget.CONFLUENCE;
    }

    /**
     * Publishes the campaign report to Confluence.
     * Validates required configuration, builds the Confluence storage-format
     * page body, and POSTs to the Confluence REST API.
     *
     * <p><b>CC-02:</b> Pipeline cohesif — validation config
     * ({@link #requireProperty}) → construction payload JSON
     * ({@link #buildConfluencePayload}) → appel HTTP POST → gestion
     * reponse/erreur. Chaque etape est inseparable du flux de publication
     * Confluence ; extraire une portion isolee nuirait a la lisibilite du
     * pipeline sequentiel.</p>
     *
     * @param report the campaign report to publish
     * @param config publisher configuration (url, spaceKey, token, parentPageId)
     * @throws PublicationException if the API call fails or config is invalid
     */
    @Override
    public void publish(CampaignReport report, PublisherConfig config)
            throws PublicationException {
        Map<String, String> props = config.properties();

        String url = requireProperty(props, KEY_URL);
        String spaceKey = requireProperty(props, KEY_SPACE_KEY);
        String token = requireProperty(props, KEY_TOKEN);
        String parentPageId = props.getOrDefault(KEY_PARENT_PAGE_ID, "");

        String title = "Performance Report — " + report.scenarioName()
                + " (" + report.verdict().name() + ")";
        String body = buildStorageBody(report);

        String apiUrl = url.replaceAll("/$", "") + API_PATH;
        String payload = buildConfluencePayload(title, spaceKey, parentPageId, body);

        log.info("action=confluence_publish_start executionId={} url={} space={}",
                report.id().value(), url, spaceKey);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("action=confluence_publish_success executionId={} status={}",
                        report.id().value(), response.statusCode());
            } else {
                log.error("action=confluence_publish_error executionId={} status={} body={}",
                        report.id().value(), response.statusCode(),
                        truncate(response.body()));
                throw new PublicationException(
                        "Confluence API error: HTTP " + response.statusCode());
            }
        } catch (IOException e) {
            log.error("action=confluence_publish_io_error executionId={} error={}",
                    report.id().value(), e.getMessage());
            throw new PublicationException(
                    "Failed to connect to Confluence: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublicationException("Confluence publish interrupted", e);
        }
    }

    // ---- internal helpers ------------------------------------------------

    /**
     * Extracts a required property, throwing {@link PublicationException}
     * with a clear message if missing.
     */
    private static String requireProperty(Map<String, String> props, String key) {
        String value = props.get(key);
        if (value == null || value.isBlank()) {
            throw new PublicationException(
                    "Missing required Confluence config property: '" + key + "'");
        }
        return value;
    }

    /**
     * Builds the Confluence REST API JSON payload.
     */
    static String buildConfluencePayload(String title, String spaceKey,
                                          String parentPageId, String body) {
        var sb = new StringBuilder(512);
        sb.append("{\"type\":\"page\",\"title\":\"").append(escapeJson(title))
                .append("\",\"space\":{\"key\":\"").append(escapeJson(spaceKey))
                .append("\"}");
        if (!parentPageId.isBlank()) {
            sb.append(",\"ancestors\":[{\"id\":").append(escapeJson(parentPageId)).append("}]");
        }
        sb.append(",\"body\":{\"storage\":{\"value\":\"")
                .append(escapeJson(body))
                .append("\",\"representation\":\"storage\"}}");
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builds an XHTML storage-format body summarising the campaign report.
     * <p><b>CC-02:</b> Pipeline cohesif — title → execution summary →
     * task tables → verdict → environment info — chaque section est un
     * bloc HTML inséparable du flux de construction du rapport Confluence.</p>
     */
    static String buildStorageBody(CampaignReport report) {
        var sb = new StringBuilder(2048);
        sb.append("<h1>Performance Report: ").append(escapeHtml(report.scenarioName()))
                .append("</h1>");

        // Execution summary
        var summary = report.executionSummary();
        sb.append("<h2>Execution Summary</h2>");
        sb.append("<table><tr><th>Metric</th><th>Value</th></tr>");
        sb.append("<tr><td>Total tasks</td><td>").append(summary.totalTasks()).append("</td></tr>");
        sb.append("<tr><td>Successful</td><td>").append(summary.successfulTasks()).append("</td></tr>");
        sb.append("<tr><td>Failed</td><td>").append(summary.failedTasks()).append("</td></tr>");
        sb.append("<tr><td>Skipped</td><td>").append(summary.skippedTasks()).append("</td></tr>");
        sb.append("<tr><td>Preparation duration</td><td>")
                .append(formatDuration(summary.preparationDuration())).append("</td></tr>");
        sb.append("<tr><td>Injection duration</td><td>")
                .append(formatDuration(summary.injectionDuration())).append("</td></tr>");
        sb.append("<tr><td>Assertion duration</td><td>")
                .append(formatDuration(summary.assertionDuration())).append("</td></tr>");
        sb.append("</table>");

        // Verdict
        sb.append("<h2>Verdict</h2>");
        sb.append("<ac:structured-macro ac:name=\"status\">");
        sb.append("<ac:parameter ac:name=\"title\">").append(report.verdict().name())
                .append("</ac:parameter>");
        sb.append("<ac:parameter ac:name=\"colour\">")
                .append(verdictColor(report.verdict().name())).append("</ac:parameter>");
        sb.append("</ac:structured-macro>");
        if (report.verdictReason() != null && !report.verdictReason().isBlank()) {
            sb.append("<p><em>").append(escapeHtml(report.verdictReason())).append("</em></p>");
        }

        // Task results
        if (!report.preparationResults().isEmpty()) {
            sb.append("<h2>Preparation</h2>");
            appendTaskTable(sb, report.preparationResults());
        }
        if (!report.injectionResults().isEmpty()) {
            sb.append("<h2>Injection</h2>");
            appendInjectionTable(sb, report.injectionResults());
        }
        if (!report.assertionResults().isEmpty()) {
            sb.append("<h2>Assertions</h2>");
            appendAssertionTable(sb, report.assertionResults());
        }

        // Environment
        sb.append("<h2>Environment</h2>");
        sb.append("<p>JVM: ").append(escapeHtml(report.environment().jvmVersion())).append("</p>");
        sb.append("<p>Agents: ")
                .append(escapeHtml(String.join(", ", report.environment().agentIds())))
                .append("</p>");

        return sb.toString();
    }

    private static void appendTaskTable(StringBuilder sb,
                                         List<TaskReportEntry> entries) {
        sb.append("<table><tr><th>Task</th><th>Status</th><th>Duration</th></tr>");
        for (var entry : entries) {
            sb.append("<tr><td>").append(escapeHtml(entry.taskName())).append("</td>");
            sb.append("<td>").append(entry.status().name()).append("</td>");
            sb.append("<td>").append(formatDuration(entry.duration())).append("</td></tr>");
        }
        sb.append("</table>");
    }

    private static void appendInjectionTable(StringBuilder sb,
                                              List<InjectionReportEntry> entries) {
        sb.append("<table><tr><th>Simulation</th><th>Duration</th><th>Requests</th>")
                .append("<th>OK</th><th>KO</th><th>Error%</th><th>Throughput</th>")
                .append("<th>p50</th><th>p95</th><th>p99</th></tr>");
        for (var entry : entries) {
            var m = entry.metrics();
            sb.append("<tr><td>").append(escapeHtml(m.simulationClass())).append("</td>");
            sb.append("<td>").append(formatDuration(m.duration())).append("</td>");
            sb.append("<td>").append(m.totalRequests()).append("</td>");
            sb.append("<td>").append(m.successfulRequests()).append("</td>");
            sb.append("<td>").append(m.failedRequests()).append("</td>");
            sb.append("<td>").append(String.format("%.2f", m.errorRate())).append("%</td>");
            sb.append("<td>").append(String.format("%.1f", m.throughput())).append("/s</td>");
            sb.append("<td>").append(m.p50Ms()).append("ms</td>");
            sb.append("<td>").append(m.p95Ms()).append("ms</td>");
            sb.append("<td>").append(m.p99Ms()).append("ms</td></tr>");
        }
        sb.append("</table>");
    }

    private static void appendAssertionTable(StringBuilder sb,
                                              List<AssertionReportEntry> entries) {
        sb.append("<table><tr><th>Assertion</th><th>Verdict</th><th>Expected</th>")
                .append("<th>Actual</th><th>Operator</th></tr>");
        for (var entry : entries) {
            var r = entry.result();
            var e = r.evidence() != null ? r.evidence() : entry.evidence();
            sb.append("<tr><td>").append(escapeHtml(r.description())).append("</td>");
            sb.append("<td>").append(r.status().name()).append("</td>");
            sb.append("<td>").append(e != null ? String.valueOf(e.expectedValue()) : "-").append("</td>");
            sb.append("<td>").append(e != null ? String.valueOf(e.actualValue()) : "-").append("</td>");
            sb.append("<td>").append(e != null ? e.operator().name() : "-").append("</td></tr>");
        }
        sb.append("</table>");
    }

    private static String formatDuration(Duration d) {
        if (d == null) return "N/A";
        long s = d.toSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }

    private static String verdictColor(String verdict) {
        return switch (verdict) {
            case "SUCCESS" -> "Green";
            case "WARNING" -> "Yellow";
            case "FAILED" -> "Red";
            default -> "Grey";
        };
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        var sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        return s == null ? "null" :
                s.length() <= 500 ? s : s.substring(0, 500) + "...";
    }
}
