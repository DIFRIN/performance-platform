package com.performance.platform.domain.assertion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serie temporelle complete d'une metrique, utilisee par les criteres
 * temporels (atteinte, persistance, etalement, allure, reprise, couverture).
 * <p>
 * Contrairement a {@link Observation} (valeur agregee), la serie conserve tous
 * les points : c'est elle qui rend les criteres temporels verifiables.
 */
public record SeriesObservation(MetricId metric, String unit, TimeWindow window,
                                List<SamplePoint> points, String source,
                                QueryFingerprint fingerprint,
                                Map<String, Object> attributes) {

    public SeriesObservation {
        Objects.requireNonNull(metric, "metric required");
        Objects.requireNonNull(unit, "unit required");
        Objects.requireNonNull(window, "window required");
        points = points == null ? List.of()
                : points.stream().sorted(Comparator.comparing(SamplePoint::at)).toList();
        source = source == null ? "" : source;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public int size() {
        return points.size();
    }

    /** @return la valeur agregee selon la reduction declaree (par defaut MAX). */
    public Observation reduce(Reduction reduction) {
        if (points.isEmpty()) {
            throw new IllegalStateException("cannot reduce an empty series: " + metric);
        }
        double value = switch (reduction) {
            case MIN -> points.stream().mapToDouble(SamplePoint::value).min().orElseThrow();
            case SUM -> points.stream().mapToDouble(SamplePoint::value).sum();
            case FIRST -> points.get(0).value();
            default -> points.stream().mapToDouble(SamplePoint::value).max().orElseThrow();
        };
        return new Observation(metric, value, unit, window, source, fingerprint, attributes);
    }
}
