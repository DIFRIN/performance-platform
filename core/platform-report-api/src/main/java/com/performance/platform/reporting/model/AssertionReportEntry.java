package com.performance.platform.reporting.model;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.id.TaskId;

import java.util.Objects;

/**
 * Entrée de rapport pour une tâche d'assertion.
 * Associe l'identifiant d'assertion au résultat et à l'évidence collectée.
 * Record immuable.
 */
public record AssertionReportEntry(
    TaskId assertionId,
    AssertionResult result,
    Evidence evidence
) {
    public AssertionReportEntry {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(result, "result required");
    }
}
