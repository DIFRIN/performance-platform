package com.performance.platform.domain.assertion;

/**
 * Tolerances des controles d'integrite d'experience (ADR-069).
 * Pourcentages dans ]0,100].
 */
public record IntegrityTolerances(double maxArrivalDeviationPercent, double maxGeneratorCpuPercent) {

    public IntegrityTolerances {
        requirePercent("maxArrivalDeviationPercent", maxArrivalDeviationPercent);
        requirePercent("maxGeneratorCpuPercent", maxGeneratorCpuPercent);
    }

    public static IntegrityTolerances defaults() {
        return new IntegrityTolerances(5.0, 90.0);
    }

    private static void requirePercent(String name, double value) {
        if (Double.isNaN(value) || value <= 0.0 || value > 100.0) {
            throw new IllegalArgumentException(name + " must be in ]0,100], got " + value);
        }
    }
}
