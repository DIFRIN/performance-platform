package com.performance.platform.application.exception;

/**
 * Exception levee lorsqu'un scenario est invalide.
 */
public class InvalidScenarioException extends RuntimeException {

    public InvalidScenarioException(String message) {
        super(message);
    }
}
