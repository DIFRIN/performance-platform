package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Résultat d'une évaluation d'assertion.
 * Contient le verdict (PASSED/FAILED/SKIPPED/ERROR), la description,
 * l'evidence collectee et les metadonnees de timing.
 * Record immuable — 0 annotation framework.
 */
public record AssertionResult(
    TaskId assertionId,
    AssertionStatus status,
    String description,
    Evidence evidence,
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public AssertionResult {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(description, "description required");
        Objects.requireNonNull(evaluationDuration, "evaluationDuration required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
    }

    /**
     * Verdict : vrai si et seulement si le statut est {@link AssertionStatus#PASSED}.
     */
    public boolean isPassed() {
        return status == AssertionStatus.PASSED;
    }
}
