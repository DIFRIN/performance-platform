package com.performance.platform.application.ports.in;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.ScenarioDefinition;

/**
 * Use case : demarrer l'execution d'un scenario.
 * Retourne l'identifiant d'execution cree.
 */
public interface ExecuteScenarioUseCase {

    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;
}
