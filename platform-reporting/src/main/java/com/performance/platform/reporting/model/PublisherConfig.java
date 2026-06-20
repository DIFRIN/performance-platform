package com.performance.platform.reporting.model;

import java.util.Map;
import java.util.Objects;

/**
 * Configuration d'une publication de rapport vers une destination externe.
 * Associe une cible de publication à ses propriétés (URL, credentials, etc.).
 * Record immuable — copie défensive sur {@code properties}.
 */
public record PublisherConfig(
    com.performance.platform.reporting.PublicationTarget target,
    Map<String, String> properties
) {
    public PublisherConfig {
        Objects.requireNonNull(target, "target required");
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
