package com.performance.platform.reporting;

/**
 * Exception levée lorsqu'une opération de rendu échoue
 * (template manquant, erreur de sérialisation, etc.).
 */
public class RenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
