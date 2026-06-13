package com.performance.platform.application.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception levee lorsque le parsing d'un scenario YAML echoue.
 * Porte la liste des erreurs de parsing detaillees.
 * Definie dans le module application pour decoupler le port ScenarioParsingUseCase
 * du module scenario-dsl qui l'implementera.
 */
public class ScenarioParsingException extends RuntimeException {

    private final List<String> errors;

    public ScenarioParsingException(String message, List<String> errors) {
        super(message);
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * Retourne la liste immuable des erreurs de parsing.
     */
    public List<String> getErrors() {
        return errors;
    }
}
