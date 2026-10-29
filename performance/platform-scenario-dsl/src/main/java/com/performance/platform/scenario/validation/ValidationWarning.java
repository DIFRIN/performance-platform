package com.performance.platform.scenario.validation;

import java.util.Objects;

/**
 * Avertissement de validation. Non bloquant.
 * Record immuable.
 */
public record ValidationWarning(String field, String message) {

    public ValidationWarning {
        Objects.requireNonNull(field, "field required");
        Objects.requireNonNull(message, "message required");
    }
}
