package com.performance.platform.domain.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant unique d'un run planifie.
 * Value object immuable, 0-annotation framework.
 */
public record ScheduledRunId(String value) {

    public ScheduledRunId {
        Objects.requireNonNull(value, "value required");
    }

    public static ScheduledRunId generate() {
        return new ScheduledRunId(UUID.randomUUID().toString());
    }

    public static ScheduledRunId of(String value) {
        return new ScheduledRunId(value);
    }
}
