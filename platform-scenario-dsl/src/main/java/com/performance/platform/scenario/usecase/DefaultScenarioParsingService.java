package com.performance.platform.scenario.usecase;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.scenario.parser.ScenarioParser;
import com.performance.platform.scenario.validation.ScenarioValidator;

import java.util.Objects;

/**
 * Implementation du use case ScenarioParsingUseCase.
 * Orchestre le parsing YAML (ScenarioParser) et la validation (ScenarioValidator).
 * Les warnings n'empechent pas le parsing — seuls les erreurs bloquent.
 * <p>
 * L'enregistrement comme bean Spring est fait par configuration explicite
 * dans le module d'assemblage (platform-app).
 */
public class DefaultScenarioParsingService implements ScenarioParsingUseCase {

    private final ScenarioParser parser;
    private final ScenarioValidator validator;

    public DefaultScenarioParsingService(ScenarioParser parser, ScenarioValidator validator) {
        this.parser = Objects.requireNonNull(parser, "parser required");
        this.validator = Objects.requireNonNull(validator, "validator required");
    }

    @Override
    public ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException {
        // Etape 1 : parser le YAML en ScenarioDefinition
        var scenario = parser.parse(yamlContent);

        // Etape 2 : valider la definition (erreurs bloquantes uniquement)
        var result = validator.validate(scenario);
        if (!result.valid()) {
            throw new ScenarioValidationException(result);
        }

        return scenario;
    }
}
