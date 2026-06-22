package com.performance.platform.reporting.render;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.reporting.ReportRenderer;
import com.performance.platform.reporting.RenderException;
import com.performance.platform.reporting.model.CampaignReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * Renderer PDF pour {@link CampaignReport}.
 * <p>
 * Délègue le rendu HTML à {@link HtmlReportRenderer}, puis convertit le HTML en PDF
 * via OpenHTMLToPDF (PDFBox backend). Le flux de conversion (HTML render →
 * OpenHTMLToPDF → PDF bytes) est un pipeline cohésif insécable.
 */
@Component
public class PdfReportRenderer implements ReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfReportRenderer.class);

    private final HtmlReportRenderer htmlRenderer;

    public PdfReportRenderer(HtmlReportRenderer htmlRenderer) {
        this.htmlRenderer = htmlRenderer;
    }

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] render(CampaignReport report) throws RenderException {
        log.info("action=render_pdf reportId={}", report.id().value());
        try {
            byte[] html = htmlRenderer.render(report);

            try (OutputStream os = new ByteArrayOutputStream()) {
                var builder = new PdfRendererBuilder();
                builder.withHtmlContent(new String(html, java.nio.charset.StandardCharsets.UTF_8),
                        null);
                builder.toStream(os);
                builder.run();

                return ((ByteArrayOutputStream) os).toByteArray();
            }
        } catch (RenderException e) {
            throw e;
        } catch (Exception e) {
            throw new RenderException("Failed to render CampaignReport as PDF for reportId=" +
                    report.id().value(), e);
        }
    }
}
