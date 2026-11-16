package com.performance.platform.reporting.model;

import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.SloStatement;
import com.performance.platform.domain.id.TaskId;

import java.util.List;
import java.util.Objects;

/**
 * Entree de rapport pour une tache d'assertion : le resume complet, source
 * unique du verdict, des checks et du SLI/SLO textuel (ADR-049/ADR-051).
 */
public record AssertionReportEntry(TaskId assertionId, AssertionSummary summary) {

    public AssertionReportEntry {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(summary, "summary required");
    }

    public AssertionStatus verdict() {
        return summary.verdict();
    }

    public List<Check> checks() {
        return summary.checks();
    }

    public SloStatement slo() {
        return summary.slo();
    }
}
