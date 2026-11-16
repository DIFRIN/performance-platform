package com.performance.platform.domain.assertion;

import java.time.Duration;
import java.util.Objects;

/**
 * Critere temporel applique a une {@link SeriesObservation}.
 * <p>
 * {@code limit} porte la borne secondaire du critere, selon son type :
 * duty cycle ({@code SUSTAINED}), CV ({@code SPREAD}), pente maximale
 * ({@code TREND}) ou trou maximal tolere ({@code COVERAGE}).
 *
 * @param type       type de critere
 * @param operator   operateur applique a la valeur de reference
 * @param threshold  seuil principal (peut etre null pour COVERAGE/SPREAD)
 * @param limit      borne secondaire (voir ci-dessus)
 * @param within     delai de reprise (RECOVERY)
 * @param minSamples nombre minimal de points (COVERAGE, toutes series)
 * @param severity   severite en cas d'echec
 */
public record TemporalCriterion(CriterionType type, AssertionOperator operator,
                                Double threshold, Double limit, Duration within,
                                Integer minSamples, Severity severity) {

    public TemporalCriterion {
        Objects.requireNonNull(type, "type required");
        severity = severity == null ? Severity.WARNING : severity;
        if (threshold != null && !Double.isFinite(threshold)) {
            throw new IllegalArgumentException("threshold must be finite");
        }
        if (limit != null && !Double.isFinite(limit)) {
            throw new IllegalArgumentException("limit must be finite");
        }
        if (minSamples != null && minSamples < 0) {
            throw new IllegalArgumentException("minSamples must not be negative");
        }
    }

    public static TemporalCriterion coverage(int minSamples) {
        return new TemporalCriterion(CriterionType.COVERAGE, null, null, null, null,
                minSamples, Severity.WARNING);
    }

    public static TemporalCriterion sustained(AssertionOperator operator, double threshold,
                                              double minDutyCycle, int minSamples,
                                              Severity severity) {
        return new TemporalCriterion(CriterionType.SUSTAINED, operator, threshold,
                minDutyCycle, null, minSamples, severity);
    }

    public static TemporalCriterion spread(double maxCoefficientOfVariation, Severity severity) {
        return new TemporalCriterion(CriterionType.SPREAD, null, null,
                maxCoefficientOfVariation, null, null, severity);
    }

    public static TemporalCriterion trend(double minSlope, double maxSlope, Severity severity) {
        return new TemporalCriterion(CriterionType.TREND, null, minSlope, maxSlope,
                null, null, severity);
    }

    public static TemporalCriterion recovery(double threshold, Duration within, Severity severity) {
        return new TemporalCriterion(CriterionType.RECOVERY, AssertionOperator.LTE, threshold,
                null, within, null, severity);
    }
}
