package com.performance.platform.reporting.render;

import com.performance.platform.reporting.model.PerformanceAssessment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Collection;
import java.util.Map;

/** Shared XHTML sections, also consumed by the PDF renderer. */
final class PerformanceSections {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private PerformanceSections() {}

    static String context(PerformanceAssessment assessment) {
        if (assessment == null) return missing();
        var c = assessment.context();
        return "<table>" + row("Application component versions", c.components()) + row("Environment", c.environment())
                + row("Test date / start", c.startedAt()) + row("Test end", c.finishedAt())
                + row("Injection duration (seconds)", c.durationSeconds()) + row("Objective", c.objective())
                + row("Test type", c.testType()) + "</table>";
    }

    static String load(PerformanceAssessment assessment) {
        if (assessment == null || assessment.loadModel().isEmpty()) return missing();
        var html = new StringBuilder();
        for (var load : assessment.loadModel()) {
            html.append("<h3>").append(escape(load.taskId())).append("</h3><table>")
                    .append(row("Description", load.description())).append(row("Configured load / volume / duration", load.configuration()))
                    .append(row("Achieved requests (all agents)", load.achievedRequests()))
                    .append(row("Measured duration (seconds)", load.durationSeconds()))
                    .append(row("Assumptions", load.assumptions())).append("</table>");
        }
        return html.toString();
    }

    static String sli(PerformanceAssessment assessment) {
        if (assessment == null) return missing();
        return checks(assessment.sliSlo()) + "<p>Latency percentiles are reported per agent; "
                + "threshold checks use the worst agent, never an average of percentiles. "
                + "Throughput checks use the lowest agent rate; error rate is request-weighted.</p>";
    }

    static String technical(PerformanceAssessment assessment) {
        if (assessment == null) return missing();
        var html = new StringBuilder("<table><tr><th>Indicator</th><th>Value</th><th>Unit</th><th>Source</th></tr>");
        for (var m : assessment.technicalIndicators().measurements()) {
            html.append("<tr><td>").append(escape(m.name())).append("</td><td>")
                    .append(m.value() == null ? "Not collected" : escape(m.value())).append("</td><td>")
                    .append(escape(m.unit())).append("</td><td>").append(escape(m.source())).append("</td></tr>");
        }
        html.append("</table><h3>Graphs / external evidence</h3><ul>");
        for (var graph : assessment.technicalIndicators().graphs()) {
            html.append("<li><strong>").append(escape(graph.title())).append("</strong> - ").append(escape(graph.source()));
            if (graph.url() != null && !graph.url().isBlank()) {
                html.append("<br/><a target=\"_blank\" rel=\"noopener noreferrer\" href=\"")
                        .append(escape(graph.url())).append("\">")
                        .append(escape(graph.url())).append("</a>");
            }
            if (graph.imageDataUri() != null) {
                html.append("<br/><img style=\"max-width: 100%;\" alt=\"").append(escape(graph.title()))
                        .append("\" src=\"").append(escape(graph.imageDataUri())).append("\"/>");
            }
            html.append("</li>");
        }
        html.append("</ul><p>External graphs are links or supplied PNG exports, not fetched or measured by this report. "
                + "Use exports or dashboards bounded to the test interval.</p><h3>Observed incidents</h3>")
                .append(list(assessment.technicalIndicators().incidents()));
        return html.toString();
    }

    static String analysis(PerformanceAssessment assessment) {
        if (assessment == null) return missing();
        return "<h3>Findings / anomalies / contention</h3>" + list(assessment.analysis().findings())
                + "<h3>Recommendations</h3>" + list(assessment.analysis().recommendations());
    }

    static String validation(PerformanceAssessment assessment) {
        if (assessment == null) return "<p>Validation evidence unavailable; legacy report verdict is not a full performance approval.</p>";
        return "<p><strong>" + assessment.validation().status() + "</strong></p>"
                + "<p>SUCCESS: all required checks passed. PARTIAL / WARNING: minor threshold failure or incomplete evidence. "
                + "FAILED: critical threshold failure or technical / functional execution failure.</p>"
                + checks(assessment.validation().criteria()) + "<h3>Missing requirements / limitations</h3>"
                + list(assessment.validation().limitations());
    }

    private static String checks(Collection<PerformanceAssessment.Check> checks) {
        if (checks.isEmpty()) return "<p>No SLO comparison available: no thresholds or assertions configured.</p>";
        var html = new StringBuilder("<table><tr><th>Check / metric</th><th>Step</th><th>Observed</th>"
                + "<th>SLO / threshold</th><th>Critical</th><th>Status</th></tr>");
        for (var check : checks) {
            html.append("<tr><td>").append(escape(check.name())).append(" / ").append(escape(check.metric()))
                    .append("</td><td>").append(escape(check.taskId())).append("</td><td>")
                    .append(check.actual() == null ? "Not collected" : escape(check.actual()))
                    .append("</td><td>").append(escape(check.operator())).append(" ").append(escape(check.threshold()))
                    .append("</td><td>").append(check.critical()).append("</td><td>").append(escape(check.status()))
                    .append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    private static String row(String label, Object value) {
        return "<tr><th>" + escape(label) + "</th><td>" + value(value) + "</td></tr>";
    }

    private static String value(Object value) {
        if (value == null || value instanceof Map<?, ?> map && map.isEmpty()
                || value instanceof Collection<?> items && items.isEmpty()) return "Not provided";
        if (value instanceof Map<?, ?> map) {
            var html = new StringBuilder();
            map.forEach((key, item) -> html.append("<p><strong>").append(escape(key)).append("</strong>: ")
                    .append(value(item)).append("</p>"));
            return html.toString();
        }
        if (value instanceof Collection<?> items) return list(items);
        if (value.getClass().isRecord()) return value(MAPPER.convertValue(value, Map.class));
        return escape(value);
    }

    private static String list(Collection<?> items) {
        if (items.isEmpty()) return "<p>Not provided</p>";
        var html = new StringBuilder("<ul>");
        items.forEach(item -> html.append("<li>").append(value(item)).append("</li>"));
        return html.append("</ul>").toString();
    }

    private static String missing() { return "<p>Not collected / Not provided</p>"; }
    private static String escape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
