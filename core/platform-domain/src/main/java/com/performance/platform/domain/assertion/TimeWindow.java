package com.performance.platform.domain.assertion;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Fenetre temporelle d'une observation, ancreé sur les evenements d'execution.
 *
 * @param from       debut (inclus)
 * @param to         fin (incluse)
 * @param resolution pas d'echantillonnage declare (peut etre null si inconnu)
 */
public record TimeWindow(Instant from, Instant to, Duration resolution) {

    public TimeWindow {
        Objects.requireNonNull(from, "from required");
        Objects.requireNonNull(to, "to required");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("window end must not precede start");
        }
        if (resolution != null && resolution.isNegative()) {
            throw new IllegalArgumentException("resolution must not be negative");
        }
    }

    public static TimeWindow of(Instant from, Instant to) {
        return new TimeWindow(from, to, null);
    }

    /** @return la duree couverte par la fenetre. */
    public Duration duration() {
        return Duration.between(from, to);
    }

    /** Elargit la fenetre des marges declarees. */
    public TimeWindow withMargins(Duration before, Duration after) {
        Duration b = before == null ? Duration.ZERO : before;
        Duration a = after == null ? Duration.ZERO : after;
        return new TimeWindow(from.minus(b), to.plus(a), resolution);
    }
}
