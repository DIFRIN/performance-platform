package com.performance.platform.application.ports.in;

import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;

import java.util.Optional;

/**
 * Use case : consulter le statut et l'etat d'une execution.
 */
public interface GetExecutionStatusUseCase {

    ExecutionStatus getStatus(ExecutionId id);

    Optional<ExecutionState> getState(ExecutionId id);
}
