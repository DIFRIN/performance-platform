package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.scenario.usecase.DefaultScenarioParsingService;
import com.performance.platform.scenario.usecase.ScenarioValidationException;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7 — le DSL refuse les scenarios mal formes au lieu de les executer avec
 * des valeurs par defaut silencieuses.
 */
@DisplayName("Scenario strictness (T7)")
class ScenarioStrictnessTest {

    private final YamlScenarioParser parser = new YamlScenarioParser();
    private final DefaultScenarioParsingService parsingService =
            new DefaultScenarioParsingService(parser, new DefaultScenarioValidator());

    @Test
    @DisplayName("unknown root key is rejected with its line number")
    void unknownRootKey() {
        String yaml = """
                scenario:
                  id: strict
                  name: Strict
                  version: 1.0.0
                  steps: []
                unknownRoot: true
                """;

        var exception = assertThrows(ScenarioParsingException.class, () -> parser.parse(yaml));
        assertTrue(exception.getErrors().stream().anyMatch(e -> e.contains("unknownRoot")),
                exception.getErrors().toString());
        assertTrue(exception.getErrors().stream().anyMatch(e -> e.startsWith("line 6:")),
                exception.getErrors().toString());
    }

    @Test
    @DisplayName("typo in a step key is rejected with its line number")
    void unknownStepKey() {
        String yaml = """
                scenario:
                  id: strict
                  name: Strict
                  version: 1.0.0
                  steps:
                    - id: s1
                      task: shell
                      phase: PREPARATION
                      duraton: 10s
                """;

        var exception = assertThrows(ScenarioParsingException.class, () -> parser.parse(yaml));
        assertTrue(exception.getErrors().stream().anyMatch(e -> e.contains("duraton")),
                exception.getErrors().toString());
        assertTrue(exception.getErrors().stream().anyMatch(e -> e.startsWith("line 9:")),
                exception.getErrors().toString());
    }

    @Test
    @DisplayName("wrong type for steps is rejected")
    void wrongType() {
        String yaml = """
                scenario:
                  id: strict
                  name: Strict
                  version: 1.0.0
                  steps: not-a-list
                """;

        var exception = assertThrows(ScenarioParsingException.class, () -> parser.parse(yaml));
        assertTrue(exception.getErrors().stream()
                        .anyMatch(e -> e.contains("'steps' must be a sequence")),
                exception.getErrors().toString());
    }

    @Test
    @DisplayName("missing required step field is rejected")
    void missingRequiredField() {
        String yaml = """
                scenario:
                  id: strict
                  name: Strict
                  version: 1.0.0
                  steps:
                    - id: s1
                      task: shell
                """;

        var exception = assertThrows(ScenarioParsingException.class, () -> parser.parse(yaml));
        assertTrue(exception.getErrors().stream()
                        .anyMatch(e -> e.contains("phase is required")),
                exception.getErrors().toString());
    }

    @Test
    @DisplayName("missing version is rejected by the parsing service")
    void missingVersion() {
        String yaml = """
                scenario:
                  id: strict
                  name: Strict
                  steps: []
                """;

        var exception = assertThrows(ScenarioValidationException.class,
                () -> parsingService.parse(yaml));
        assertTrue(exception.getErrors().stream().anyMatch(e -> e.contains("version")),
                exception.getErrors().toString());
    }
}
