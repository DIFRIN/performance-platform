package com.performance.platform.domain.injection;

import java.time.Duration;

/** Étape d'un plan de charge. */
public sealed interface Stage permits Ramp, Hold, Burst, Pause {
    /** false = warm-up, exclu des métriques. */
    boolean measured();

    /** Durée de l'étape ; Burst → Duration.ZERO. */
    Duration duration();

    /** Validation partagée par les records. */
    static Duration requirePositive(Duration d, String name) {
        if (d == null || d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return d;
    }

    static double requireLevel(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return v;
    }
}
