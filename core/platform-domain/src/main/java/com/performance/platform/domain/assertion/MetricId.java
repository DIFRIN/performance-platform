package com.performance.platform.domain.assertion;

import java.util.Objects;

/**
 * Identite canonique d'une metrique utilisee par les assertions.
 * <p>
 * Le nom est unique et stable : {@code injection.latency.p95},
 * {@code database.updates}, {@code filesystem.exists}. Seule cette forme
 * canonique est acceptee par le catalogue.
 * <p>
 * 0 annotation framework.
 */
public record MetricId(String namespace, String name) {

    public MetricId {
        Objects.requireNonNull(namespace, "namespace required");
        Objects.requireNonNull(name, "name required");
        if (namespace.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("MetricId parts must not be blank");
        }
    }

    /** Analyse une forme {@code namespace.name}. */
    public static MetricId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MetricId value required");
        }
        int separator = value.indexOf('.');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "MetricId must use the 'namespace.name' form, got: " + value);
        }
        return new MetricId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public String toString() {
        return namespace + "." + name;
    }
}
