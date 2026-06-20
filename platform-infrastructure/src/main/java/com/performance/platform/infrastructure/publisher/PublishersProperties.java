package com.performance.platform.infrastructure.publisher;

import com.performance.platform.reporting.PublicationTarget;
import com.performance.platform.reporting.model.PublisherConfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;

/**
 * Configuration properties for report publishers.
 * Bound from {@code platform.publishers.*} in application configuration.
 * Record immuable — defensive copy on {@code publishers} list.
 *
 * <h3>Configuration prefix convention</h3>
 * <p>This class uses {@code platform.publishers} whereas the reporting
 * engine output settings use {@code reporting.*} (via
 * {@code com.performance.platform.reporting.output.ReportProperties}).
 * This is intentional: {@code reporting.*} covers the rendering/output
 * pipeline (formats, output directory, templates), while
 * {@code platform.publishers.*} covers the infrastructure-side publication
 * dispatch to external systems (Confluence, S3, Git, etc.). The convention
 * follows the same pattern as {@code platform.datasources.*}
 * ({@code PlatformDatasourcesProperties}) — infrastructure adapters use
 * {@code platform.*} prefixes, domain-adjacent modules use their module
 * name as prefix.</p>
 */
@ConfigurationProperties(prefix = "platform.publishers")
public record PublishersProperties(List<PublisherConfig> publishers) {

    public PublishersProperties {
        publishers = publishers == null ? List.of() : List.copyOf(publishers);
    }

    /**
     * Looks up the {@link PublisherConfig} matching the given target.
     *
     * @param target the publication target to find configuration for
     * @return the matching config, or empty if not configured
     */
    public Optional<PublisherConfig> forTarget(PublicationTarget target) {
        return publishers.stream()
                .filter(c -> c.target() == target)
                .findFirst();
    }
}
