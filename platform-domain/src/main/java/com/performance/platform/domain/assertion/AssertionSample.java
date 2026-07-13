package com.performance.platform.domain.assertion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Echantillon individuel collecte pendant une assertion basee sur des intervalles.
 * Reference par {@link AssertionSummary#history()}.
 * <p>
 * Record immuable — 0 annotation framework.
 */
public record AssertionSample(
    Instant sampledAt,
    double observedValue,
    String unit,
    Map<String, Object> metadata
) {
    public AssertionSample {
        Objects.requireNonNull(sampledAt, "sampledAt required");
        Objects.requireNonNull(unit, "unit required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
