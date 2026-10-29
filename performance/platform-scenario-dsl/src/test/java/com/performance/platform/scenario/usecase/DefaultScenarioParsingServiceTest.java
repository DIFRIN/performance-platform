package com.performance.platform.scenario.usecase;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.scenario.parser.ScenarioParser;
import com.performance.platform.scenario.validation.ScenarioValidator;
import com.performance.platform.scenario.validation.ValidationError;
import com.performance.platform.scenario.validation.ValidationResult;
import com.performance.platform.scenario.validation.ValidationWarning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultScenarioParsingService")
class DefaultScenarioParsingServiceTest {

    private DefaultScenarioParsingService service;
    private ScenarioDefinition validScenario;

    @BeforeEach
    void setUp() {
        validScenario = new ScenarioDefinition(
            ScenarioId.of("test-scenario"),
            "Test Scenario",
            "1.0.0",
            List.of(),
            java.util.Map.of(),
            ExecutionMode.LOCAL,
            List.of(),
            java.util.Map.of()
        );
    }

    // ==================== Stub helpers ====================

    private void initService(ScenarioParser parser, ScenarioValidator validator) {
        service = new DefaultScenarioParsingService(parser, validator);
    }

    private ScenarioParser stubParserReturning(ScenarioDefinition scenario) {
        return new ScenarioParser() {
            @Override
            public ScenarioDefinition parse(String yamlContent) {
                return scenario;
            }

            @Override
            public ScenarioDefinition parse(InputStream yamlContent) {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public ScenarioDefinition parseFile(Path scenarioFile) {
                throw new UnsupportedOperationException("not used in this test");
            }
        };
    }

    private ScenarioParser stubParserThrowing(ScenarioParsingException ex) {
        return new ScenarioParser() {
            @Override
            public ScenarioDefinition parse(String yamlContent) {
                throw ex;
            }

            @Override
            public ScenarioDefinition parse(InputStream yamlContent) {
                throw ex;
            }

            @Override
            public ScenarioDefinition parseFile(Path scenarioFile) {
                throw ex;
            }
        };
    }

    private ScenarioValidator stubValidator(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings) {
        return scenario -> new ValidationResult(valid, errors, warnings);
    }

    // ==================== Tests ====================

    @Nested
    @DisplayName("Parsing reussi + validation OK")
    class SuccessfulParsing {

        @Test
        @DisplayName("retourne le scenario quand parsing et validation reussissent")
        void shouldReturnScenarioWhenValid() {
            initService(
                stubParserReturning(validScenario),
                stubValidator(true, List.of(), List.of())
            );

            var result = service.parse("name: Test");

            assertSame(validScenario, result);
        }

        @Test
        @DisplayName("retourne le scenario avec des warnings (non bloquants)")
        void shouldReturnScenarioWithWarnings() {
            initService(
                stubParserReturning(validScenario),
                stubValidator(true, List.of(),
                    List.of(new ValidationWarning("version", "version should follow semver"))
                )
            );

            var result = service.parse("name: Test");

            assertSame(validScenario, result);
        }
    }

    @Nested
    @DisplayName("Echec de validation")
    class ValidationFailure {

        @Test
        @DisplayName("leve ScenarioValidationException en cas d'erreur de validation")
        void shouldThrowOnValidationError() {
            initService(
                stubParserReturning(validScenario),
                stubValidator(false,
                    List.of(new ValidationError("steps", "at least one step required", "steps")),
                    List.of()
                )
            );

            var ex = assertThrows(ScenarioValidationException.class,
                () -> service.parse("name: Test"));

            assertNotNull(ex.getResult());
            assertFalse(ex.getResult().valid());
            assertEquals(1, ex.getResult().errors().size());
            assertEquals("steps: at least one step required", ex.getResult().errorMessages().get(0));
        }

        @Test
        @DisplayName("ScenarioValidationException est une sous-classe de ScenarioParsingException")
        void shouldBeSubclassOfScenarioParsingException() {
            initService(
                stubParserReturning(validScenario),
                stubValidator(false,
                    List.of(new ValidationError("steps", "at least one step required", "steps")),
                    List.of()
                )
            );

            var ex = assertThrows(ScenarioParsingException.class,
                () -> service.parse("name: Test"));

            assertInstanceOf(ScenarioValidationException.class, ex);
        }

        @Test
        @DisplayName("porte le message des erreurs dans getErrors() herite")
        void shouldCarryErrorMessages() {
            initService(
                stubParserReturning(validScenario),
                stubValidator(false,
                    List.of(
                        new ValidationError("steps", "at least one step required", "steps"),
                        new ValidationError("version", "version is required", "version")
                    ),
                    List.of()
                )
            );

            var ex = assertThrows(ScenarioValidationException.class,
                () -> service.parse("name: Test"));

            assertEquals(2, ex.getErrors().size());
            assertTrue(ex.getErrors().contains("steps: at least one step required"));
            assertTrue(ex.getErrors().contains("version: version is required"));
        }

        @Test
        @DisplayName("getResult() contient aussi les warnings")
        void shouldContainWarningsInResult() {
            initService(
                stubParserReturning(validScenario),
                stubValidator(false,
                    List.of(new ValidationError("id", "invalid id format", "id")),
                    List.of(new ValidationWarning("name", "name is too long"))
                )
            );

            var ex = assertThrows(ScenarioValidationException.class,
                () -> service.parse("name: Test"));

            assertEquals(1, ex.getResult().warnings().size());
            assertEquals("name is too long", ex.getResult().warnings().get(0).message());
        }
    }

    @Nested
    @DisplayName("Echec de parsing")
    class ParsingFailure {

        @Test
        @DisplayName("propage ScenarioParsingException du parser")
        void shouldPropagateParsingException() {
            var parseException = new ScenarioParsingException("YAML syntax error",
                List.of("line 3: unexpected token"));

            initService(stubParserThrowing(parseException), stubValidator(true, List.of(), List.of()));

            var ex = assertThrows(ScenarioParsingException.class,
                () -> service.parse("invalid: [malformed"));

            assertSame(parseException, ex);
            assertEquals("line 3: unexpected token", ex.getErrors().get(0));
        }

        @Test
        @DisplayName("ne tente pas la validation si le parsing echoue")
        void shouldNotValidateOnParsingFailure() {
            var parseException = new ScenarioParsingException("YAML syntax error",
                List.of("line 3: unexpected token"));

            var validatorCalled = new boolean[]{false};
            var parser = stubParserThrowing(parseException);
            var validator = new ScenarioValidator() {
                @Override
                public ValidationResult validate(ScenarioDefinition scenario) {
                    validatorCalled[0] = true;
                    return ValidationResult.valid(List.of());
                }
            };

            service = new DefaultScenarioParsingService(parser, validator);

            assertThrows(ScenarioParsingException.class,
                () -> service.parse("invalid: [malformed"));
            assertFalse(validatorCalled[0], "validator should not be called when parsing fails");
        }
    }

    @Nested
    @DisplayName("Constructeur")
    class Constructor {

        @Test
        @DisplayName("refuse un parser null")
        void shouldRejectNullParser() {
            var ex = assertThrows(NullPointerException.class,
                () -> new DefaultScenarioParsingService(null, stubValidator(true, List.of(), List.of())));
            assertTrue(ex.getMessage().contains("parser"));
        }

        @Test
        @DisplayName("refuse un validator null")
        void shouldRejectNullValidator() {
            var ex = assertThrows(NullPointerException.class,
                () -> new DefaultScenarioParsingService(stubParserReturning(validScenario), null));
            assertTrue(ex.getMessage().contains("validator"));
        }
    }
}
