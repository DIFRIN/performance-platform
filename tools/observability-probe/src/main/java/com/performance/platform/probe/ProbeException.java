package com.performance.platform.probe;

/** Erreur fonctionnelle de l'outil, avec code de sortie associe. */
public class ProbeException extends RuntimeException {

    private final int exitCode;

    public ProbeException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public ProbeException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    public static ProbeException config(String message) {
        return new ProbeException(2, message);
    }

    public static ProbeException insufficient(String message) {
        return new ProbeException(1, message);
    }

    public static ProbeException remote(String message, Throwable cause) {
        return new ProbeException(3, message, cause);
    }
}
