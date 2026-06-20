package com.performance.platform.reporting;

/**
 * Exception levée lorsqu'une publication de rapport échoue
 * (échec de connexion à la plateforme cible, timeout, etc.).
 */
public class PublicationException extends RuntimeException {

    public PublicationException(String message) {
        super(message);
    }

    public PublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
