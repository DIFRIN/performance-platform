package com.performance.platform.application.exception;

import com.performance.platform.domain.id.ExecutionId;

import java.util.Optional;

/**
 * Exception levee lorsqu'une execution ne peut pas etre demarree ou echoue.
 */
public class ExecutionException extends RuntimeException {

    private final ExecutionId executionId;

    public ExecutionException(String message) {
        super(message);
        this.executionId = null;
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.executionId = null;
    }

    public ExecutionException(String message, ExecutionId executionId) {
        super(message);
        this.executionId = executionId;
    }

    public ExecutionException(String message, Throwable cause, ExecutionId executionId) {
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
