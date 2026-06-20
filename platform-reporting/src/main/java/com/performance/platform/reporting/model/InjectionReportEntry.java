package com.performance.platform.reporting.model;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Entrée de rapport pour une tâche d'injection.
 * Associe l'identifiant de tâche aux métriques d'injection et au répertoire Gatling.
 * Record immuable.
 */
public record InjectionReportEntry(
    TaskId taskId,
    InjectionResult metrics,
    Path gatlingReportDirectory
) {
    public InjectionReportEntry {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(metrics, "metrics required");
        Objects.requireNonNull(gatlingReportDirectory, "gatlingReportDirectory required");
    }
}
