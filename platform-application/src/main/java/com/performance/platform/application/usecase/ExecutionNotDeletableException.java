package com.performance.platform.application.usecase;

import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;

/**
 * Levee lorsqu'une tentative de suppression vise une execution active.
 * La suppression est autorisee uniquement pour les statuts COMPLETED, FAILED ou CANCELLED.
 * Protege le checkpointing (CNF-02) contre les suppressions concurrentes (ADR-020).
 */
public class ExecutionNotDeletableException extends RuntimeException {

    private final ExecutionId executionId;
    private final ExecutionStatus currentStatus;

    public ExecutionNotDeletableException(ExecutionId executionId, ExecutionStatus currentStatus) {
        super("Execution " + executionId + " cannot be deleted: status=" + currentStatus
                + " (only COMPLETED, FAILED, CANCELLED are deletable)");
        this.executionId = executionId;
        this.currentStatus = currentStatus;
    }

    public ExecutionId getExecutionId() {
        return executionId;
    }

    public ExecutionStatus getCurrentStatus() {
        return currentStatus;
    }
}
