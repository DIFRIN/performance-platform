package com.performance.platform.application.exception;

/**
 * Exception levee lorsque la generation d'un rapport echoue.
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
