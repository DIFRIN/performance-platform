package com.performance.platform.application.exception;

import com.performance.platform.domain.id.ExecutionId;

import java.util.Optional;

/**
 * Exception levee lorsque la generation d'un rapport echoue.
 */
public class ReportGenerationException extends RuntimeException {

    private final ExecutionId executionId;

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.executionId = null;
    }

    public ReportGenerationException(String message, Throwable cause, ExecutionId executionId) {
        super(message, cause);
        this.executionId = executionId;
    }

    /**
     * Returns the execution identifier if available.
     */
    public Optional<ExecutionId> getExecutionId() {
        return Optional.ofNullable(executionId);
    }
}
