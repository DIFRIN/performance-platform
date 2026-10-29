package com.performance.platform.application.ports.in;

import com.performance.platform.domain.id.ExecutionId;

/**
 * Use case : annuler une execution en cours.
 */
public interface CancelExecutionUseCase {

    void cancel(ExecutionId id);
}
