package com.performance.platform.domain.assertion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Fait mesure, jamais un jugement.
 *
 * @param metric     metrique canonique
 * @param value      valeur agregee sur la fenetre (reduction deja appliquee)
 * @param unit       unite canonique
 * @param window     fenetre de mesure
 * @param source     taskId, agent, datasource ou source logique
 * @param fingerprint empreinte de la requete (rejouable)
 * @param attributes metadonnees non decisives (dashboard, query, ...)
 */
public record Observation(MetricId metric, double value, String unit, TimeWindow window,
                          String source, QueryFingerprint fingerprint,
                          Map<String, Object> attributes) {

    public Observation {
        Objects.requireNonNull(metric, "metric required");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("observation value must be finite");
        }
        Objects.requireNonNull(unit, "unit required");
        Objects.requireNonNull(window, "window required");
        source = source == null ? "" : source;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static Observation of(MetricId metric, double value, String unit,
                                 TimeWindow window, String source) {
        return new Observation(metric, value, unit, window, source,
                QueryFingerprint.internal(source, window), Map.of());
    }

    /** @return vrai si la valeur est comparable a un seuil (evite NaN). */
    public boolean measurable() {
        return !Double.isNaN(value);
    }

    /**
     * Retourne une observation avec une valeur differente mais la meme empreinte.
     * Utilise par l'agregation multi-sources (reduction deja calculee ailleurs).
     */
    public Observation withValue(double newValue) {
        return new Observation(metric, newValue, unit, window, source, fingerprint, attributes);
    }

    /** @return l'instant de fin de fenetre, utilitaire de tri. */
    public Instant measuredAt() {
        return window.to();
    }
}
