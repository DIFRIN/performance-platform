package com.performance.platform.domain.assertion;

import java.time.Instant;
import java.util.Objects;

/** Un point d'une serie temporelle. */
public record SamplePoint(Instant at, double value) {

    public SamplePoint {
        Objects.requireNonNull(at, "at required");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("sample value must be finite");
        }
    }
}
