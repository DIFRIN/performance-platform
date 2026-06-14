package com.performance.platform.transport;

/**
 * Exception non verifiee levee par la couche de transport en cas d'erreur
 * de communication (connexion, envoi, reception).
 */
public class TransportException extends RuntimeException {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
