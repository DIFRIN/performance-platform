package com.performance.platform.application.exception;

/**
 * Exception levee lorsqu'une execution ne peut pas etre demarree ou echoue.
 */
public class ExecutionException extends RuntimeException {

    public ExecutionException(String message) {
        super(message);
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
