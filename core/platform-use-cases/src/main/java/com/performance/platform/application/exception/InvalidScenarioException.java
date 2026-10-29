package com.performance.platform.application.exception;

/**
 * Exception levee lorsqu'un scenario est invalide.
 */
public class InvalidScenarioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidScenarioException(String message) {
        super(message);
    }
}
