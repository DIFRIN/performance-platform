package com.performance.platform.application.ports.in;

import com.performance.platform.domain.id.ExecutionId;

/**
 * Use case : supprimer une execution terminee.
 * La suppression est interdite si l'execution est en cours (STARTED ou RUNNING).
 */
public interface DeleteExecutionUseCase {

    /**
     * Supprime l'execution identifiee par {@code id}.
     *
     * @param id l'identifiant de l'execution a supprimer
     * @throws com.performance.platform.application.usecase.ExecutionNotDeletableException
     *         si l'execution est dans un etat actif (STARTED ou RUNNING)
     * @throws com.performance.platform.application.exception.ExecutionException
     *         si l'execution n'existe pas
     */
    void delete(ExecutionId id);
}
