package com.performance.platform.engine;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.ScenarioDefinition;

/**
 * Port d'orchestration de l'execution d'un scenario.
 * Implementations : {@code LocalExecutionEngine} (in-process, Virtual Threads)
 * et {@code RemoteExecutionEngine} (distribue via transport).
 *
 * <p>0 annotation framework — interface Java pure.</p>
 */
public interface ExecutionEngine {

    /**
     * Lance l'execution d'un scenario complet.
     *
     * @param scenario la definition du scenario deja validee
     * @return l'identifiant de l'execution creee
     * @throws ExecutionException si l'execution ne peut pas demarrer
     */
    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;

    /**
     * Interroge le statut d'une execution en cours ou terminee.
     *
     * @param id l'identifiant de l'execution
     * @return le statut courant
     * @throws ExecutionException si l'execution est introuvable
     */
    ExecutionStatus getStatus(ExecutionId id);

    /**
     * Demande l'annulation d'une execution en cours.
     * L'annulation est cooperative : l'engine verifie le flag entre les niveaux DAG
     * et entre les phases.
     *
     * @param id l'identifiant de l'execution a annuler
     */
    void cancel(ExecutionId id);
}
