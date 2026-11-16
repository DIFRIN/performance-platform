package com.performance.platform.domain.assertion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Analyse pure d'une serie temporelle.
 * <p>
 * Aucune decision ici : uniquement des mesures (couverture, duty cycle,
 * dispersion, pente, reprise). {@code TemporalCheckEvaluator} les compare
 * ensuite au {@link TemporalCriterion}.
 * <p>
 * 0 annotation framework.
 */
public final class SeriesAnalysis {

    private SeriesAnalysis() {
    }

    /** @return la valeur moyenne, ou vide si la serie est vide. */
    public static OptionalDouble mean(SeriesObservation series) {
        if (series == null || series.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(series.points().stream()
                .mapToDouble(SamplePoint::value).average().orElseThrow());
    }

    /** @return l'ecart-type populationnel, ou vide si moins de deux points. */
    public static OptionalDouble standardDeviation(SeriesObservation series) {
        if (series == null || series.size() < 2) {
            return OptionalDouble.empty();
        }
        double mean = mean(series).orElseThrow();
        double variance = series.points().stream()
                .mapToDouble(p -> Math.pow(p.value() - mean, 2))
                .sum() / series.size();
        return OptionalDouble.of(Math.sqrt(variance));
    }

    /** @return sigma/mu, vide si mu est nul ou la serie trop courte. */
    public static OptionalDouble coefficientOfVariation(SeriesObservation series) {
        var avg = mean(series);
        var sd = standardDeviation(series);
        if (avg.isEmpty() || sd.isEmpty() || avg.getAsDouble() == 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sd.getAsDouble() / Math.abs(avg.getAsDouble()));
    }

    /** @return amplitude max-min, vide si serie vide. */
    public static OptionalDouble range(SeriesObservation series) {
        if (series == null || series.isEmpty()) {
            return OptionalDouble.empty();
        }
        var stats = series.points().stream().mapToDouble(SamplePoint::value).summaryStatistics();
        return OptionalDouble.of(stats.getMax() - stats.getMin());
    }

    /**
     * Part du temps (proportion de points) qui satisfait l'attente.
     *
     * @param series    serie mesuree
     * @param operator  operateur
     * @param threshold seuil
     * @return duty cycle dans [0,1], vide si serie vide
     */
    public static OptionalDouble dutyCycle(SeriesObservation series, AssertionOperator operator,
                                           double threshold) {
        if (series == null || series.isEmpty()) {
            return OptionalDouble.empty();
        }
        long matching = series.points().stream()
                .filter(p -> operator.evaluate(p.value(), threshold))
                .count();
        return OptionalDouble.of((double) matching / series.size());
    }

    /**
     * Pente des moindres carres, en unites de valeur par seconde.
     *
     * @return pente, vide si moins de deux points ou duree nulle
     */
    public static OptionalDouble slopePerSecond(SeriesObservation series) {
        if (series == null || series.size() < 2) {
            return OptionalDouble.empty();
        }
        List<SamplePoint> points = series.points();
        Instant origin = points.get(0).at();
        double meanT = points.stream()
                .mapToDouble(p -> Duration.between(origin, p.at()).toMillis() / 1000.0)
                .average().orElseThrow();
        double meanV = mean(series).orElseThrow();
        double numerator = 0;
        double denominator = 0;
        for (SamplePoint point : points) {
            double t = Duration.between(origin, point.at()).toMillis() / 1000.0;
            numerator += (t - meanT) * (point.value() - meanV);
            denominator += Math.pow(t - meanT, 2);
        }
        if (denominator == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(numerator / denominator);
    }

    /**
     * Trou maximal entre deux points consecutifs.
     *
     * @return le trou, vide si moins de deux points
     */
    public static Optional<Duration> maxGap(SeriesObservation series) {
        if (series == null || series.size() < 2) {
            return Optional.empty();
        }
        Duration max = Duration.ZERO;
        for (int i = 1; i < series.size(); i++) {
            Duration gap = Duration.between(series.points().get(i - 1).at(),
                    series.points().get(i).at());
            if (gap.compareTo(max) > 0) {
                max = gap;
            }
        }
        return Optional.of(max);
    }

    /**
     * Cherche le premier point ou la serie repasse sous (ou egal a) un seuil
     * apres un instant donne.
     *
     * @param series    serie
     * @param threshold seuil
     * @param after     instant de depart (fin d'injection)
     * @return le delai de reprise, vide si le seuil n'est jamais retrouve
     */
    public static Optional<Duration> recoveryDuration(SeriesObservation series, double threshold,
                                                      Instant after) {
        if (series == null || series.isEmpty()) {
            return Optional.empty();
        }
        return series.points().stream()
                .filter(p -> !p.at().isBefore(after) && p.value() <= threshold)
                .findFirst()
                .map(p -> Duration.between(after, p.at()));
    }

    /** @return vrai si la serie est croissante (ou decroissante) au sens large. */
    public static boolean monotonic(SeriesObservation series, boolean increasing) {
        if (series == null || series.size() < 2) {
            return false;
        }
        for (int i = 1; i < series.size(); i++) {
            double previous = series.points().get(i - 1).value();
            double current = series.points().get(i).value();
            if (increasing ? current < previous : current > previous) {
                return false;
            }
        }
        return true;
    }
}
