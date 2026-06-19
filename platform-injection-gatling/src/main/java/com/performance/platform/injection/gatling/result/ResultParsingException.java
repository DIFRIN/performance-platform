package com.performance.platform.injection.gatling.result;

/**
 * Exception levee en cas d'erreur lors du parsing des resultats Gatling.
 * <p>
 * Wrappe l'exception cause (fichier manquant, JSON malforme, champ absent)
 * et le message pour le diagnostic.
 * Unchecked : un parsing qui echoue est une erreur systeme, pas metier.
 */
public class ResultParsingException extends RuntimeException {

    public ResultParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResultParsingException(String message) {
        super(message);
    }
}
