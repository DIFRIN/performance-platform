package com.performance.platform.application.ports.in;

import com.performance.platform.domain.execution.ExecutionState;

import java.util.List;

/**
 * Use case : lister les executions recentes.
 * Si {@code limit <= 0}, un defaut est applique.
 */
public interface ListExecutionsUseCase {

    /**
     * Retourne les executions les plus recentes, triees par startedAt DESC.
     *
     * @param limit nombre maximum d'executions a retourner ; si {@code <= 0}, un defaut est applique
     * @return liste d'etats d'execution, jamais null
     */
    List<ExecutionState> list(int limit);
}
