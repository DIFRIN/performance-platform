package com.performance.platform.reporting.model;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Entrée de rapport pour une tâche d'injection.
 * Associe l'identifiant de tâche aux métriques d'injection, au répertoire Gatling,
 * et au plan de charge résolu (forme canonique) avec son hash SHA-256.
 * Record immuable.
 *
 * @param workloadPlan     forme canonique du plan ; vide = plan non enregistré
 * @param workloadPlanHash SHA-256 hex ; "" = non enregistré
 */
public record InjectionReportEntry(
    TaskId taskId,
    InjectionResult metrics,
    Path gatlingReportDirectory,
    String agentId,
    Map<String, Object> workloadPlan,
    String workloadPlanHash
) {
    public InjectionReportEntry(TaskId taskId, InjectionResult metrics, Path gatlingReportDirectory) {
        this(taskId, metrics, gatlingReportDirectory, null, Map.of(), "");
    }

    public InjectionReportEntry(TaskId taskId, InjectionResult metrics, Path gatlingReportDirectory, String agentId) {
        this(taskId, metrics, gatlingReportDirectory, agentId, Map.of(), "");
    }

    public InjectionReportEntry {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(metrics, "metrics required");
        Objects.requireNonNull(gatlingReportDirectory, "gatlingReportDirectory required");
        workloadPlan = workloadPlan == null ? Map.of() : Map.copyOf(workloadPlan);
        workloadPlanHash = workloadPlanHash == null ? "" : workloadPlanHash;
    }

    public boolean planRecorded() {
        return !workloadPlanHash.isBlank();
    }
}
