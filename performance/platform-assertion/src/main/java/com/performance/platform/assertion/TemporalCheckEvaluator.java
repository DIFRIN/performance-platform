package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.CriterionType;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.SeriesAnalysis;
import com.performance.platform.domain.assertion.SeriesObservation;
import com.performance.platform.domain.assertion.TemporalCriterion;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

/**
 * Evalue les criteres temporels (atteinte, persistance, etalement, allure,
 * reprise, couverture, monotonie) sur une serie.
 * <p>
 * La couverture est toujours verifiee en premier : une serie trop courte ou
 * trouee produit {@code INCONCLUSIVE}, jamais un faux verdict.
 */
public final class TemporalCheckEvaluator {

    /** Couverture minimale par defaut pour un quantile. */
    public static final int DEFAULT_MIN_SAMPLES = 3;

    /** Couverture minimale pour un quantile (un p99 sur 5 points n'a pas de sens). */
    public static final int MIN_SAMPLES_FOR_QUANTILE = 30;

    private TemporalCheckEvaluator() {
    }

    /**
     * @param series   serie mesuree (peut etre null)
     * @param criterion critere temporel
     * @param intent   libelle lisible (peut etre null)
     * @param injectionEnd fin d'injection, requise pour RECOVERY
     * @return le check correspondant
     */
    public static Check evaluate(SeriesObservation series, TemporalCriterion criterion,
                                 String intent, Instant injectionEnd) {
        String label = intent == null || intent.isBlank()
                ? defaultIntent(series, criterion)
                : intent;
        if (series == null) {
            throw new IllegalArgumentException("series required");
        }
        if (series.isEmpty()) {
            return Check.inconclusive(series.metric(), label, "No data in window");
        }
        var metric = series.metric();
        int minSamples = criterion.minSamples() == null
                ? DEFAULT_MIN_SAMPLES : criterion.minSamples();
        if (series.size() < minSamples) {
            return Check.inconclusive(metric, label,
                    "Insufficient coverage: " + series.size() + " points < " + minSamples);
        }
        if (criterion.limit() != null && criterion.type() == CriterionType.COVERAGE) {
            var gap = SeriesAnalysis.maxGap(series);
            if (gap.isPresent() && gap.get().toMillis() > criterion.limit()) {
                return Check.inconclusive(metric, label,
                        "Gap of " + gap.get() + " exceeds " + criterion.limit());
            }
        }

        return switch (criterion.type()) {
            case COVERAGE -> passed(series, criterion, label,
                    series.size() + " points, no excessive gap");
            case REACH -> reach(series, criterion, label);
            case SUSTAINED -> sustained(series, criterion, label);
            case SPREAD -> spread(series, criterion, label);
            case TREND -> trend(series, criterion, label);
            case RECOVERY -> recovery(series, criterion, label, injectionEnd);
            case MONOTONIC -> monotonic(series, criterion, label);
        };
    }

    private static Check reach(SeriesObservation series, TemporalCriterion criterion, String label) {
        if (criterion.threshold() == null || criterion.operator() == null) {
            return Check.inconclusive(series.metric(), label, "REACH requires operator and threshold");
        }
        boolean reached = series.points().stream()
                .anyMatch(point -> criterion.operator().evaluate(point.value(), criterion.threshold()));
        if (reached) {
            return passed(series, criterion, label, "threshold reached at least once");
        }
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MAX);
        return Check.failed(reduced,
                new Expectation(criterion.operator(), criterion.threshold(), 0, criterion.severity()),
                label, "Threshold never reached in window");
    }

    private static Check sustained(SeriesObservation series, TemporalCriterion criterion, String label) {
        if (criterion.threshold() == null || criterion.operator() == null) {
            return Check.inconclusive(series.metric(), label, "SUSTAINED requires operator and threshold");
        }
        double required = criterion.limit() == null ? 1.0 : criterion.limit();
        OptionalDouble duty = SeriesAnalysis.dutyCycle(series, criterion.operator(),
                criterion.threshold());
        if (duty.isEmpty()) {
            return Check.inconclusive(series.metric(), label, "No duty cycle computable");
        }
        String reason = String.format("Duty cycle %.2f of window, required %.2f", duty.getAsDouble(), required);
        if (duty.getAsDouble() + 1e-9 >= required) {
            return passed(series, criterion, label, reason);
        }
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MIN);
        return Check.failed(reduced,
                new Expectation(criterion.operator(), criterion.threshold(), 0, criterion.severity()),
                label, reason);
    }

    private static Check spread(SeriesObservation series, TemporalCriterion criterion, String label) {
        OptionalDouble cv = SeriesAnalysis.coefficientOfVariation(series);
        if (cv.isEmpty()) {
            return Check.inconclusive(series.metric(), label,
                    "Coefficient of variation not computable (empty series or zero mean)");
        }
        double maxCv = criterion.limit() == null ? 0.2 : criterion.limit();
        String reason = String.format("CV %.3f, max %.3f", cv.getAsDouble(), maxCv);
        if (cv.getAsDouble() <= maxCv) {
            return passed(series, criterion, label, reason);
        }
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MAX);
        return Check.failed(reduced, new Expectation(
                        com.performance.platform.domain.assertion.AssertionOperator.LTE,
                        maxCv, 0, criterion.severity()),
                label, reason);
    }

    private static Check trend(SeriesObservation series, TemporalCriterion criterion, String label) {
        OptionalDouble slope = SeriesAnalysis.slopePerSecond(series);
        if (slope.isEmpty()) {
            return Check.inconclusive(series.metric(), label, "Trend not computable (needs 2+ points)");
        }
        double min = criterion.threshold() == null ? Double.NEGATIVE_INFINITY : criterion.threshold();
        double max = criterion.limit() == null ? Double.POSITIVE_INFINITY : criterion.limit();
        String reason = String.format("Slope %.4f/s in [%.4f, %.4f]", slope.getAsDouble(), min, max);
        if (slope.getAsDouble() >= min && slope.getAsDouble() <= max) {
            return passed(series, criterion, label, reason);
        }
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.FIRST);
        return Check.failed(reduced, new Expectation(
                        com.performance.platform.domain.assertion.AssertionOperator.LTE,
                        max, 0, criterion.severity()),
                label, reason);
    }

    private static Check recovery(SeriesObservation series, TemporalCriterion criterion,
                                  String label, Instant injectionEnd) {
        if (injectionEnd == null) {
            return Check.inconclusive(series.metric(), label,
                    "RECOVERY requires the injection end instant");
        }
        double threshold = criterion.threshold() == null ? 0 : criterion.threshold();
        var recovered = SeriesAnalysis.recoveryDuration(series, threshold, injectionEnd);
        if (recovered.isEmpty()) {
            Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MAX);
            return Check.failed(reduced, new Expectation(
                            com.performance.platform.domain.assertion.AssertionOperator.LTE,
                            threshold, 0, criterion.severity()),
                    label, "Threshold not recovered after injection end");
        }
        Duration within = criterion.within() == null ? Duration.ZERO : criterion.within();
        String reason = "Recovered after " + recovered.get();
        if (recovered.get().compareTo(within) <= 0) {
            return passed(series, criterion, label, reason);
        }
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MAX);
        return Check.failed(reduced, new Expectation(
                        com.performance.platform.domain.assertion.AssertionOperator.LTE,
                        threshold, 0, criterion.severity()),
                label, reason + ", required within " + within);
    }

    private static Check monotonic(SeriesObservation series, TemporalCriterion criterion, String label) {
        boolean increasing = criterion.threshold() == null || criterion.threshold() >= 0;
        boolean ok = SeriesAnalysis.monotonic(series, increasing);
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MAX);
        return ok
                ? Check.passed(reduced, null, label)
                : Check.failed(reduced, null, label,
                        "Series is not " + (increasing ? "increasing" : "decreasing"));
    }

    private static Check passed(SeriesObservation series, TemporalCriterion criterion,
                                String label, String reason) {
        Observation reduced = series.reduce(com.performance.platform.domain.assertion.Reduction.MAX);
        return Check.passed(reduced,
                criterion.threshold() == null || criterion.operator() == null ? null
                        : new Expectation(criterion.operator(), criterion.threshold(), 0,
                        criterion.severity()),
                label + (reason.isBlank() ? "" : " (" + reason + ")"));
    }

    private static String defaultIntent(SeriesObservation series, TemporalCriterion criterion) {
        String base = series == null ? "" : series.metric().toString();
        return base + " " + criterion.type().name().toLowerCase();
    }
}
