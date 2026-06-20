package com.performance.platform.reporting.output;

import com.performance.platform.domain.report.ReportFormat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Proprietes de configuration du reporting.
 * Prefixe : {@code reporting}
 *
 * <p>Chaque champ correspond a une propriete dans {@code application.yaml} :
 * <pre>
 * reporting:
 *   output-directory: reports
 *   formats:
 *     - HTML
 *     - PDF
 *     - JSON
 * </pre>
 *
 * <p>Si {@code outputDirectory} est absent, la valeur par defaut est {@code "reports"}.
 * Si {@code formats} est absent ou vide, les trois formats (HTML, PDF, JSON) sont actifs.
 */
@ConfigurationProperties(prefix = "reporting")
public record ReportProperties(
        String outputDirectory,
        List<ReportFormat> formats
) {}
