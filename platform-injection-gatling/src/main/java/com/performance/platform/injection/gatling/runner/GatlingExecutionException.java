package com.performance.platform.injection.gatling.runner;

/**
 * Exception levee en cas d'echec du lancement d'une simulation Gatling.
 * <p>
 * Wrappe l'exception cause et le message pour le diagnostic.
 * Unchecked : une simulation qui ne peut pas etre lancee est une
 * erreur systeme, pas metier.
 */
public class GatlingExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GatlingExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public GatlingExecutionException(String message) {
        super(message);
    }
}
