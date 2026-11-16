package com.performance.platform.app.api.dto;

import com.performance.platform.domain.scenario.ScenarioDefinition;

import java.util.List;

/**
 * Resultat de la validation d'un YAML de scenario, sans execution.
 * <p>
 * Utilise par {@code POST /api/v1/scenarios/validate} pour separer
 * l'upload/validation du lancement (LOCAL et ORCHESTRATOR).
 *
 * @param scenarioId    identifiant du scenario
 * @param name          nom lisible
 * @param version       version semver
 * @param stepCount     nombre d'etapes
 * @param steps         resumes des etapes (id, task, phase ou during)
 */
public record ScenarioPreviewResponse(
        String scenarioId,
        String name,
        String version,
        int stepCount,
        List<Step> steps) {

    /** Resume d'une etape, sans ses parametres. */
    public record Step(String id, String taskName, String phase) {
    }

    /** Construit la reponse depuis une definition validee. */
    public static ScenarioPreviewResponse from(ScenarioDefinition scenario) {
        List<Step> steps = scenario.steps().stream()
                .map(step -> new Step(step.id().value(), step.taskName(),
                        step.phase() == null ? String.valueOf(step.during()) : step.phase().name()))
                .toList();
        return new ScenarioPreviewResponse(
                scenario.id().value(),
                scenario.name(),
                scenario.version(),
                steps.size(),
                steps);
    }
}
