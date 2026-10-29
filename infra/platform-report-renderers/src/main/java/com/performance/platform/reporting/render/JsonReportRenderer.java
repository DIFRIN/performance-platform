package com.performance.platform.reporting.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.RenderException;
import com.performance.platform.reporting.model.CampaignReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renderer JSON pour {@link CampaignReport}.
 * <p>
 * Utilise Jackson avec le module {@link JavaTimeModule} pour la sérialisation
 * d'{@code Instant} et {@code Duration}. La sortie est formatée (pretty-print)
 * pour faciliter la relecture.
 */
@Component
public class JsonReportRenderer implements ReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(JsonReportRenderer.class);

    private final ObjectMapper mapper;

    public JsonReportRenderer() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.JSON;
    }

    @Override
    public byte[] render(CampaignReport report) throws RenderException {
        log.info("action=render_json reportId={}", report.id().value());
        try {
            return mapper.writeValueAsBytes(report);
        } catch (Exception e) {
            throw new RenderException("Failed to render CampaignReport as JSON for reportId=" +
                    report.id().value(), e);
        }
    }
}
