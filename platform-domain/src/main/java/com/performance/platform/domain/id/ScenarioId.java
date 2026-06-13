package com.performance.platform.domain.id;

import java.util.Objects;

/**
 * Identifiant unique d'un scénario de test.
 * Value object immuable, 0-annotation framework.
 */
public record ScenarioId(String value) {

    public ScenarioId {
        Objects.requireNonNull(value, "value required");
    }

    public static ScenarioId of(String value) {
        return new ScenarioId(value);
    }
}
