package com.performance.platform.reporting.model;

import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.report.Verdict;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rapport complet d'une campagne de performance.
 * Agrège toutes les informations : environnement, résumé d'exécution,
 * résultats de préparation/injection/assertion, verdict final et durée totale.
 * Record immuable — copies défensives sur toutes les collections.
 */
public record CampaignReport(
    ReportId id,
    ScenarioId scenarioId,
    String scenarioName,
    String scenarioVersion,
    List<String> tags,
    Map<String, String> metadata,
    EnvironmentInfo environment,
    ExecutionSummary executionSummary,
    List<TaskReportEntry> preparationResults,
    List<InjectionReportEntry> injectionResults,
    List<AssertionReportEntry> assertionResults,
    Verdict verdict,
    String verdictReason,
    Instant generatedAt,
    Duration totalDuration
) {
    public CampaignReport {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(scenarioName, "scenarioName required");
        Objects.requireNonNull(scenarioVersion, "scenarioVersion required");
        Objects.requireNonNull(environment, "environment required");
        Objects.requireNonNull(executionSummary, "executionSummary required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(generatedAt, "generatedAt required");
        Objects.requireNonNull(totalDuration, "totalDuration required");
        tags = tags == null ? List.of() : List.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        preparationResults = preparationResults == null ? List.of() : List.copyOf(preparationResults);
        injectionResults = injectionResults == null ? List.of() : List.copyOf(injectionResults);
        assertionResults = assertionResults == null ? List.of() : List.copyOf(assertionResults);
    }
}
