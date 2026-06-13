package com.performance.platform.domain.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant unique d'une exécution de scénario.
 * Value object immuable, 0-annotation framework.
 */
public record ExecutionId(String value) {

    public ExecutionId {
        Objects.requireNonNull(value, "value required");
    }

    public static ExecutionId generate() {
        return new ExecutionId(UUID.randomUUID().toString());
    }

    public static ExecutionId of(String value) {
        return new ExecutionId(value);
    }
}
