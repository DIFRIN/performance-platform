package com.performance.platform.agent.registration;

/**
 * Exception non vérifiée levée en cas d'erreur lors de l'enregistrement
 * ou de la communication de l'agent avec l'orchestrateur.
 */
public class RegistrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegistrationException(String message) {
        super(message);
    }

    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
