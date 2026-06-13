package com.performance.platform.domain.assertion;

import java.util.Map;
import java.util.Objects;

/**
 * Preuve collectée lors de l'évaluation d'une assertion.
 * Contient la valeur observée, la valeur attendue, l'opérateur de comparaison,
 * l'unité, et des métadonnées supplémentaires.
 * Record immuable — copie défensive sur {@code details}.
 * 0 annotation framework.
 */
public record Evidence(
    Object actualValue,
    Object expectedValue,
    AssertionOperator operator,
    String unit,
    Map<String, Object> details
) {
    public Evidence {
        Objects.requireNonNull(operator, "operator required");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
