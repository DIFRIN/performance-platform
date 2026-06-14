package com.performance.platform.engine.plan;

import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.scenario.ScenarioDefinition;

/**
 * Construit un {@link ExecutionPlan} complet a partir d'un {@link ScenarioDefinition} valide.
 * Les etapes sont reparties par phase (PREPARATION / INJECTION / ASSERTION) et triees par
 * niveau DAG calcule via {@link DagLevelCalculator}.
 * Leve {@link com.performance.platform.application.exception.InvalidScenarioException}
 * si un cycle est detecte dans les dependances.
 */
@FunctionalInterface
public interface ExecutionPlanBuilder {

    /**
     * Transforme un scenario en plan d'execution pret a etre orchestre.
     *
     * @param scenario la definition du scenario, deja validee
     * @return le plan d'execution complet
     * @throws com.performance.platform.application.exception.InvalidScenarioException si cycle DAG
     */
    ExecutionPlan build(ScenarioDefinition scenario);
}
