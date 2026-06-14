package com.performance.platform.scenario.usecase;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.scenario.validation.ValidationResult;

import java.util.Objects;

/**
 * Exception levee lorsque la validation d'un scenario echoue
 * (erreurs bloquantes, pas les warnings).
 * Etend ScenarioParsingException pour compatibilite avec le port ScenarioParsingUseCase.
 */
public class ScenarioValidationException extends ScenarioParsingException {

    private final ValidationResult result;

    public ScenarioValidationException(ValidationResult result) {
        super("Scenario validation failed: " + result.errors().size() + " error(s)", result.errorMessages());
        this.result = Objects.requireNonNull(result, "result required");
    }

    /**
     * Retourne le resultat complet de validation (erreurs et warnings).
     */
    public ValidationResult getResult() {
        return result;
    }
}
