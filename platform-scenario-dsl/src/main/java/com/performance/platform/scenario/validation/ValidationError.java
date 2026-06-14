package com.performance.platform.scenario.validation;

import java.util.Objects;

/**
 * Erreur bloquante de validation. Rend le scenario invalide.
 * Record immuable.
 */
public record ValidationError(String field, String message, String path) {

    public ValidationError {
        Objects.requireNonNull(field, "field required");
        Objects.requireNonNull(message, "message required");
        // path peut etre null (pas de chemin precis)
    }
}
