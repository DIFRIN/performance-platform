package com.performance.platform.domain.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Politique de retry pour une étape en échec.
 * Backoff exponentiel : délai = min(initialDelay * multiplier^attempt, maxDelay).
 * Record immuable — copies défensives sur l'ensemble des exceptions.
 * 0 annotation framework.
 */
public record RetryPolicy(
    int maxAttempts,
    Duration initialDelay,
    double multiplier,
    Duration maxDelay,
    Set<Class<? extends Exception>> retryableExceptions
) {
    public RetryPolicy {
        Objects.requireNonNull(initialDelay, "initialDelay required");
        Objects.requireNonNull(maxDelay, "maxDelay required");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0, got: " + multiplier);
        }
        retryableExceptions = retryableExceptions == null ? Set.of() : Set.copyOf(retryableExceptions);
    }

    /**
     * Politique par défaut : 3 tentatives, backoff exponentiel de 1s à 30s, pas de filtre d'exception.
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30), Set.of());
    }
}
