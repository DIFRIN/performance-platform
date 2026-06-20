package com.performance.platform.scenario.e2e;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.parser.YamlScenarioParser;
import com.performance.platform.scenario.usecase.DefaultScenarioParsingService;
import com.performance.platform.scenario.usecase.ScenarioValidationException;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;
import com.performance.platform.scenario.validation.ScenarioValidator;
import com.performance.platform.scenario.validation.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests E2E de contrat pour le pipeline complet du Scenario DSL.
 * <p>
 * Flux teste : fichier YAML reel -> ScenarioParser -> ScenarioDefinition
 * -> ScenarioValidator -> ScenarioParsingUseCase.
 * <p>
 * Utilise de vrais fichiers YAML dans src/test/resources/scenarios/
 * pour valider le comportement en conditions realistes.
 */
@DisplayName("Scenario DSL Contract E2E")
class ScenarioDslContractE2ETest {

    private static final Path SCENARIOS_DIR = Path.of(
            "src/test/resources/scenarios");

    private YamlScenarioParser parser;
    private ScenarioValidator validator;
    private ScenarioParsingUseCase useCase;

    @BeforeEach
    void setUp() {
        parser = new YamlScenarioParser();
        validator = new DefaultScenarioValidator();
        useCase = new DefaultScenarioParsingService(parser, validator);
    }

    // ========================================================================
    // Scenarios valides
    // ========================================================================

    @Nested
    @DisplayName("Valid scenarios — full pipeline parseFile")
    class ValidScenarios {

        @Test
        @DisplayName("TC-01: valid-minimal.yaml — parser only")
        void parseMinimalScenario() {
            Path file = SCENARIOS_DIR.resolve("valid-minimal.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            assertNotNull(scenario);
            assertEquals("minimal-valid", scenario.id().value());
            assertEquals("Minimal Valid Scenario", scenario.name());
            assertEquals("1.0.0", scenario.version());
            assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
            assertEquals(1, scenario.steps().size());

            StepDefinition step = scenario.steps().get(0);
            assertEquals("single-step", step.id().value());
            assertEquals("database", step.taskName());
            assertEquals(Phase.PREPARATION, step.phase());
        }

        @Test
        @DisplayName("TC-02: valid-minimal.yaml — full use case (parser + validator)")
        void parseMinimalScenarioWithValidation() {
            Path file = SCENARIOS_DIR.resolve("valid-minimal.yaml");
            String yamlContent = readFileAsString(file);
            ScenarioDefinition scenario = useCase.parse(yamlContent);

            assertNotNull(scenario);
            assertEquals("minimal-valid", scenario.id().value());
        }

        @Test
        @DisplayName("TC-03: valid-full.yaml — all features present")
        void parseFullScenario() {
            Path file = SCENARIOS_DIR.resolve("valid-full.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            assertNotNull(scenario);
            assertEquals("full-scenario", scenario.id().value());
            assertEquals("Full Feature Scenario", scenario.name());
            assertEquals("2.3.1", scenario.version());
            assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
            assertEquals(List.of("performance", "regression", "nightly"), scenario.tags());
            assertEquals("team-a", scenario.metadata().get("owner"));
            assertEquals("PERF-456", scenario.metadata().get("jira"));
            assertEquals(7, scenario.steps().size());
            assertEquals(1, scenario.loadModels().size());

            // Verify phases are correctly parsed
            List<StepDefinition> prepSteps = scenario.steps().stream()
                    .filter(s -> s.phase() == Phase.PREPARATION).toList();
            List<StepDefinition> injSteps = scenario.steps().stream()
                    .filter(s -> s.phase() == Phase.INJECTION).toList();
            List<StepDefinition> assertSteps = scenario.steps().stream()
                    .filter(s -> s.phase() == Phase.ASSERTION).toList();

            assertEquals(3, prepSteps.size());
            assertEquals(1, injSteps.size());
            assertEquals(3, assertSteps.size());
        }

        @Test
        @DisplayName("TC-04: valid-full.yaml — full use case with validation passes")
        void parseFullScenarioWithValidation() {
            Path file = SCENARIOS_DIR.resolve("valid-full.yaml");
            String yamlContent = readFileAsString(file);
            ScenarioDefinition scenario = useCase.parse(yamlContent);

            assertEquals(7, scenario.steps().size());
            assertNotNull(scenario.loadModels().get("ramp-load"));
        }

        @Test
        @DisplayName("TC-05: valid-full.yaml — dependsOn ordering is preserved")
        void parseFullScenarioDependsOn() {
            Path file = SCENARIOS_DIR.resolve("valid-full.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            StepDefinition seedStep = scenario.steps().stream()
                    .filter(s -> s.id().value().equals("seed-test-data"))
                    .findFirst().orElseThrow();
            StepDefinition loadStep = scenario.steps().stream()
                    .filter(s -> s.id().value().equals("customer-api-load"))
                    .findFirst().orElseThrow();

            assertTrue(seedStep.dependsOn().stream()
                    .anyMatch(d -> d.value().equals("purge-customer-db")));
            assertTrue(loadStep.dependsOn().size() >= 2);
        }

        @Test
        @DisplayName("TC-06: valid-full.yaml — retry policy is parsed correctly")
        void parseFullScenarioRetryPolicy() {
            Path file = SCENARIOS_DIR.resolve("valid-full.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            StepDefinition loadStep = scenario.steps().stream()
                    .filter(s -> s.id().value().equals("customer-api-load"))
                    .findFirst().orElseThrow();

            assertNotNull(loadStep.retryPolicy());
            assertEquals(2, loadStep.retryPolicy().maxAttempts());
        }

        @Test
        @DisplayName("TC-07: valid-distributed.yaml — DISTRIBUTED mode")
        void parseDistributedScenario() {
            Path file = SCENARIOS_DIR.resolve("valid-distributed.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            assertNotNull(scenario);
            assertEquals("distributed-scenario", scenario.id().value());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(List.of("distributed", "multi-agent"), scenario.tags());
            assertEquals(4, scenario.steps().size());
        }

        @Test
        @DisplayName("TC-08: valid-distributed.yaml — load models are parsed")
        void parseDistributedLoadModels() {
            Path file = SCENARIOS_DIR.resolve("valid-distributed.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            assertNotNull(scenario.loadModels().get("constant-load"));
            assertEquals(1, scenario.loadModels().size());
        }
    }

    // ========================================================================
    // Scenarios invalides
    // ========================================================================

    @Nested
    @DisplayName("Invalid scenarios — validation errors")
    class InvalidScenarios {

        @Test
        @DisplayName("TC-10: invalid-missing-id.yaml — should reject")
        void rejectMissingId() {
            Path file = SCENARIOS_DIR.resolve("invalid-missing-id.yaml");

            assertThrows(ScenarioParsingException.class, () -> parser.parseFile(file));
        }

        @Test
        @DisplayName("TC-11: invalid-cycle.yaml — should detect DAG cycle via validator")
        void rejectCycleInDependencies() throws Exception {
            Path file = SCENARIOS_DIR.resolve("invalid-cycle.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);
            // Parser accepts it (structural validity), validator catches the cycle
            var result = validator.validate(scenario);

            assertFalse(result.valid());
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.field() != null && e.field().contains("dependsOn")),
                    "Expected a validation error about dependsOn, got: "
                            + result.errors().stream()
                            .map(ValidationError::field)
                            .collect(Collectors.joining(", ")));
        }

        @Test
        @DisplayName("TC-12: invalid-self-dependency.yaml — should reject self-dependency")
        void rejectSelfDependency() throws Exception {
            Path file = SCENARIOS_DIR.resolve("invalid-self-dependency.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);
            var result = validator.validate(scenario);

            assertFalse(result.valid());
        }

        @Test
        @DisplayName("TC-13: invalid-duplicate-ids.yaml — should reject duplicate IDs")
        void rejectDuplicateIds() throws Exception {
            Path file = SCENARIOS_DIR.resolve("invalid-duplicate-ids.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);
            var result = validator.validate(scenario);

            assertFalse(result.valid());
        }

        @Test
        @DisplayName("TC-14: invalid-bad-version.yaml — should reject bad version via validator")
        void rejectBadVersion() throws Exception {
            Path file = SCENARIOS_DIR.resolve("invalid-bad-version.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);
            var result = validator.validate(scenario);

            // Version "not-a-semver" should produce a validation error
            assertFalse(result.valid(),
                    "Expected validation issues for bad version");
        }

        @Test
        @DisplayName("TC-15: invalid-missing-phase.yaml — parser rejects missing phase")
        void handleMissingPhase() {
            Path file = SCENARIOS_DIR.resolve("invalid-missing-phase.yaml");
            assertThrows(ScenarioParsingException.class,
                    () -> parser.parseFile(file));
        }

        @Test
        @DisplayName("TC-16: invalid-unknown-task.yaml — parser accepts but validator may warn")
        void handleUnknownTaskName() throws Exception {
            // Unknown task names are accepted at parse time
            // (task resolution happens at execution time)
            Path file = SCENARIOS_DIR.resolve("invalid-unknown-task.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            assertNotNull(scenario);
            assertEquals("completely-unknown-task-xyz",
                    scenario.steps().get(0).taskName());
        }
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("TC-20: Empty steps list should parse")
        void parseScenarioWithNoSteps() {
            String yaml = """
                scenario:
                  id: empty-steps
                  name: Empty Steps
                  version: 1.0.0
                  execution:
                    mode: LOCAL
                  steps: []
                """;
            ScenarioDefinition scenario = parser.parse(yaml);

            assertNotNull(scenario);
            assertEquals("empty-steps", scenario.id().value());
            assertTrue(scenario.steps().isEmpty());
        }

        @Test
        @DisplayName("TC-21: Scenario with no load models should parse")
        void parseScenarioWithoutLoadModels() {
            Path file = SCENARIOS_DIR.resolve("valid-minimal.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            assertNotNull(scenario);
            assertTrue(scenario.loadModels().isEmpty());
        }

        @Test
        @DisplayName("TC-22: Null YAML content should throw")
        void rejectNullYamlInput() {
            assertThrows(ScenarioParsingException.class,
                    () -> parser.parse((String) null));
        }

        @Test
        @DisplayName("TC-23: Blank YAML content should throw")
        void rejectBlankYamlInput() {
            assertThrows(ScenarioParsingException.class,
                    () -> parser.parse("   "));
        }

        @Test
        @DisplayName("TC-24: Missing scenario section should throw")
        void rejectMissingScenarioSection() {
            String yaml = """
                loadModels:
                  my-load:
                    type: CONSTANT
                    stages:
                      - duration: 1m
                        usersPerSecond: 10
                """;
            assertThrows(ScenarioParsingException.class,
                    () -> parser.parse(yaml));
        }

        @Test
        @DisplayName("TC-25: Default execution mode when absent is LOCAL")
        void defaultExecutionModeIsLocal() {
            String yaml = """
                scenario:
                  id: no-mode
                  name: No Mode Specified
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: database
                      phase: PREPARATION
                      timeout: 30s
                """;
            ScenarioDefinition scenario = parser.parse(yaml);

            assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        }
    }

    // ========================================================================
    // Realistic integration scenarios
    // ========================================================================

    @Nested
    @DisplayName("Realistic integration scenarios")
    class RealisticIntegration {

        @Test
        @DisplayName("TC-30: Full round-trip — parse YAML, validate, inspect all phases")
        void fullRoundTrip() {
            // Parse the most complex scenario
            Path file = SCENARIOS_DIR.resolve("valid-full.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);
            var result = validator.validate(scenario);

            assertTrue(result.valid(),
                    "Validation errors: " + result.errorMessages());

            // Verify complete structure
            assertEquals("full-scenario", scenario.id().value());
            assertEquals("2.3.1", scenario.version());
            assertEquals(ExecutionMode.LOCAL, scenario.executionMode());

            // Every step has a unique ID
            List<String> ids = scenario.steps().stream()
                    .map(s -> s.id().value()).toList();
            assertEquals(ids.size(), ids.stream().distinct().count());

            // Phases are in expected order in the list
            assertEquals(Phase.PREPARATION, scenario.steps().get(0).phase());

            // Injection phase contains the Gatling step
            long injectionCount = scenario.steps().stream()
                    .filter(s -> s.phase() == Phase.INJECTION).count();
            assertTrue(injectionCount > 0);

            // Assertion phase is last
            long assertionCount = scenario.steps().stream()
                    .filter(s -> s.phase() == Phase.ASSERTION).count();
            assertTrue(assertionCount > 0);

            // Load model is accessible
            assertNotNull(scenario.loadModels().get("ramp-load"));
        }

        @Test
        @DisplayName("TC-31: Full use case with validation throws on validation failure")
        void useCaseRejectsInvalidScenario() {
            Path file = SCENARIOS_DIR.resolve("invalid-cycle.yaml");
            String yamlContent = readFileAsString(file);

            assertThrows(ScenarioValidationException.class,
                    () -> useCase.parse(yamlContent));
        }

        @Test
        @DisplayName("TC-32: Context propagation structure — requiredContexts parsed")
        void requiredContextsAreParsed() {
            Path file = SCENARIOS_DIR.resolve("valid-full.yaml");
            ScenarioDefinition scenario = parser.parseFile(file);

            // seed-test-data requires purge-customer-db context
            StepDefinition seedStep = scenario.steps().stream()
                    .filter(s -> s.id().value().equals("seed-test-data"))
                    .findFirst().orElseThrow();
            assertTrue(seedStep.requiredContexts().contains("purge-customer-db"));

            // customer-api-load requires contexts from both preparation steps
            StepDefinition loadStep = scenario.steps().stream()
                    .filter(s -> s.id().value().equals("customer-api-load"))
                    .findFirst().orElseThrow();
            assertTrue(loadStep.requiredContexts().size() >= 2);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static String readFileAsString(Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read file: " + file, e);
        }
    }
}
