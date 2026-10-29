package com.performance.platform.reporting.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Résumé quantitatif de l'exécution : compteurs de tâches et durées par phase.
 * Record immuable — 0 annotation framework.
 */
public record ExecutionSummary(
    int totalTasks,
    int successfulTasks,
    int failedTasks,
    int skippedTasks,
    Duration preparationDuration,
    Duration injectionDuration,
    Duration assertionDuration
) {
    public ExecutionSummary {
        Objects.requireNonNull(preparationDuration, "preparationDuration required");
        Objects.requireNonNull(injectionDuration, "injectionDuration required");
        Objects.requireNonNull(assertionDuration, "assertionDuration required");
        if (totalTasks < 0) throw new IllegalArgumentException("totalTasks must be non-negative, got " + totalTasks);
        if (successfulTasks < 0) throw new IllegalArgumentException("successfulTasks must be non-negative, got " + successfulTasks);
        if (failedTasks < 0) throw new IllegalArgumentException("failedTasks must be non-negative, got " + failedTasks);
        if (skippedTasks < 0) throw new IllegalArgumentException("skippedTasks must be non-negative, got " + skippedTasks);
    }
}
