package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;

import java.util.List;
import java.util.Objects;

/**
 * Plan d'exécution complet d'un scénario, organisé en trois phases.
 * Contient toutes les étapes résolues (niveau DAG, dépendances) et le contexte initial.
 * Record immuable — copies défensives sur toutes les listes.
 * 0 annotation framework.
 */
public record ExecutionPlan(
    ExecutionId id,
    ScenarioId scenarioId,
    List<ExecutionStep> preparationSteps,
    List<ExecutionStep> injectionSteps,
    List<ExecutionStep> assertionSteps,
    ExecutionContext initialContext
) {
    public ExecutionPlan {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(initialContext, "initialContext required");
        preparationSteps = preparationSteps == null ? List.of() : List.copyOf(preparationSteps);
        injectionSteps = injectionSteps == null ? List.of() : List.copyOf(injectionSteps);
        assertionSteps = assertionSteps == null ? List.of() : List.copyOf(assertionSteps);
    }

    /**
     * Retourne le nombre total d'etapes dans le plan, toutes phases confondues.
     */
    public int totalSteps() {
        return preparationSteps.size() + injectionSteps.size() + assertionSteps.size();
    }
}
