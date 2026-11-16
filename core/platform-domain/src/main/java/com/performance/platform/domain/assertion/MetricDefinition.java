package com.performance.platform.domain.assertion;

import java.util.Objects;

/**
 * Definition d'une metrique du catalogue canonique.
 *
 * @param id          identite canonique
 * @param kind        nature (compteur, jauge, quantile...)
 * @param unit        unite affichee et verifiee
 * @param reduction   regle d'agregation multi-sources
 * @param description libelle lisible
 * 0 annotation framework.
 */
public record MetricDefinition(MetricId id, MetricKind kind, String unit,
                               Reduction reduction, String description) {

    public MetricDefinition {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(kind, "kind required");
        Objects.requireNonNull(unit, "unit required");
        Objects.requireNonNull(reduction, "reduction required");
        description = description == null ? "" : description;
    }

    /** Vrai si la metrique est numerique continue (donc soumise a tolerance sur EQ). */
    public boolean requiresToleranceForEquality() {
        return kind == MetricKind.GAUGE || kind == MetricKind.RATIO
                || kind == MetricKind.QUANTILE || kind == MetricKind.DURATION;
    }
}
