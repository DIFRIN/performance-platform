package com.performance.platform.reporting.render;

import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.RenderException;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.TaskReportEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renderer HTML pour {@link CampaignReport}.
 * <p>
 * Utilise un template HTML chargé depuis le classpath
 * ({@code templates/campaign-report.html}) avec substitution
 * de placeholders et génération dynamique des sections
 * (préparation, injection KPIs, assertions).
 * <p>
 * CC-02 : le pipeline de rendu (load template → build sections →
 * substitute placeholders) forme un ensemble cohésif insécable.
 */
@Component
public class HtmlReportRenderer implements ReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(HtmlReportRenderer.class);

    private static final String TEMPLATE_PATH = "templates/campaign-report.html";
    private static final int MAX_TEMPLATE_SIZE = 256 * 1024; // 256 KB

    private final String template;

    public HtmlReportRenderer() {
        this.template = loadTemplate();
    }

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.HTML;
    }

    @Override
    public byte[] render(CampaignReport report) throws RenderException {
        log.info("action=render_html reportId={}", report.id().value());
        try {
            String html = template
                    .replace("${scenarioName}", escapeHtml(report.scenarioName()))
                    .replace("${scenarioVersion}", escapeHtml(report.scenarioVersion()))
                    .replace("${reportId}", escapeHtml(report.id().value()))
                    .replace("${generatedAt}", report.generatedAt().toString())
                    .replace("${verdict}", report.verdict().name())
                    .replace("${verdictCssClass}", verdictCssClass(report.verdict()))
                    .replace("${verdictReason}", report.verdictReason() != null
                            ? " — " + escapeHtml(report.verdictReason()) : "")
                    .replace("${jvmVersion}", escapeHtml(report.environment().jvmVersion()))
                    .replace("${agentIds}", String.join(", ", report.environment().agentIds()))
                    .replace("${totalTasks}", String.valueOf(report.executionSummary().totalTasks()))
                    .replace("${successfulTasks}", String.valueOf(report.executionSummary().successfulTasks()))
                    .replace("${failedTasks}", String.valueOf(report.executionSummary().failedTasks()))
                    .replace("${skippedTasks}", String.valueOf(report.executionSummary().skippedTasks()))
                    .replace("${preparationDuration}", report.executionSummary().preparationDuration().toString())
                    .replace("${injectionDuration}", report.executionSummary().injectionDuration().toString())
                    .replace("${assertionDuration}", report.executionSummary().assertionDuration().toString())
                    .replace("${preparationTable}", buildPreparationTable(report.preparationResults()))
                    .replace("${injectionKPIs}", buildInjectionKPIs(report.injectionResults()))
                    .replace("${assertionTable}", buildAssertionTable(report.assertionResults()));

            return html.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RenderException("Failed to render CampaignReport as HTML for reportId=" +
                    report.id().value(), e);
        }
    }

    // ──────────────────────────────────────────────
    // Template loading
    // ──────────────────────────────────────────────

    private String loadTemplate() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                return "<html><body><h1>Template not found: " + TEMPLATE_PATH + "</h1></body></html>";
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length > MAX_TEMPLATE_SIZE) {
                log.warn("action=template_too_large size={}", bytes.length);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("action=template_load_failed path={}", TEMPLATE_PATH, e);
            return "<html><body><h1>Error loading template</h1></body></html>";
        }
    }

    // ──────────────────────────────────────────────
    // Section builders
    // ──────────────────────────────────────────────

    private String buildPreparationTable(List<TaskReportEntry> entries) {
        if (entries.isEmpty()) {
            return "<p><em>No preparation tasks</em></p>";
        }
        var sb = new StringBuilder();
        sb.append("<table><tr><th>Task</th><th>Name</th><th>Status</th><th>Duration</th></tr>\n");
        for (TaskReportEntry e : entries) {
            sb.append("<tr>")
                    .append("<td>").append(escapeHtml(e.taskId().value())).append("</td>")
                    .append("<td>").append(escapeHtml(e.taskName())).append("</td>")
                    .append("<td>").append(e.status().name()).append("</td>")
                    .append("<td>").append(e.duration().toString()).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String buildInjectionKPIs(List<InjectionReportEntry> entries) {
        if (entries.isEmpty()) {
            return "<p><em>No injection tasks</em></p>";
        }
        var sb = new StringBuilder();
        for (InjectionReportEntry e : entries) {
            var m = e.metrics();
            sb.append("<h3>").append(escapeHtml(e.metrics().simulationClass())).append("</h3>\n");
            sb.append("<div>\n");
            kpi(sb, "Requests", m.totalRequests());
            kpi(sb, "Throughput", String.format("%.1f/s", m.throughput()));
            kpi(sb, "Error Rate", String.format("%.2f%%", m.errorRate()));
            kpi(sb, "p50", m.p50Ms() + " ms");
            kpi(sb, "p90", m.p90Ms() + " ms");
            kpi(sb, "p99", m.p99Ms() + " ms");
            kpi(sb, "Max", m.maxMs() + " ms");
            kpi(sb, "Mean", String.format("%.1f ms", m.meanMs()));
            kpi(sb, "Duration", m.duration().toString());
            sb.append("</div>\n");
        }
        return sb.toString();
    }

    private void kpi(StringBuilder sb, String label, Object value) {
        sb.append("<div class=\"kpi\"><div class=\"kpi-value\">")
                .append(escapeHtml(String.valueOf(value)))
                .append("</div><div class=\"kpi-label\">")
                .append(escapeHtml(label))
                .append("</div></div>\n");
    }

    private String buildAssertionTable(List<AssertionReportEntry> entries) {
        if (entries.isEmpty()) {
            return "<p><em>No assertion tasks</em></p>";
        }
        var sb = new StringBuilder();
        sb.append("<table><tr><th>Task</th><th>Status</th><th>Description</th><th>Duration</th></tr>\n");
        for (AssertionReportEntry e : entries) {
            sb.append("<tr>")
                    .append("<td>").append(escapeHtml(e.assertionId().value())).append("</td>")
                    .append("<td>").append(e.result().status().name()).append("</td>")
                    .append("<td>").append(escapeHtml(e.result().description())).append("</td>")
                    .append("<td>").append(e.result().evaluationDuration().toString()).append("</td>")
                    .append("</tr>\n");
        }
        sb.append("</table>");
        return sb.toString();
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    static String verdictCssClass(Verdict verdict) {
        return switch (verdict) {
            case SUCCESS -> "success";
            case WARNING -> "warning";
            case FAILED -> "failed";
        };
    }

    static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
