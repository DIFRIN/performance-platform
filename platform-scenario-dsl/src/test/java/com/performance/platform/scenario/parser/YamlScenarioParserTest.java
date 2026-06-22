package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YamlScenarioParser")
class YamlScenarioParserTest {

    private YamlScenarioParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlScenarioParser();
    }

    @Nested
    @DisplayName("Reference YAML — happy path")
    class ReferenceYaml {

        private static final String REFERENCE_YAML = """
            scenario:
              id: customer-api-perf
              name: Customer API Campaign
              version: 1.0.0
              tags: [performance, regression]
              metadata: { owner: team-a, jira: PERF-123 }
              execution: { mode: DISTRIBUTED }
              taskAvailabilityTimeoutSeconds: 120
              steps:
                - id: purge-db
                  task: database
                  phase: PREPARATION
                  requiredContexts: []
                  dependsOn: []
                  parameters: { operation: PURGE, datasource: customer-db }
                  timeout: 30s
                - id: customer-api-load
                  task: gatling
                  phase: INJECTION
                  requiredContexts: [purge-db]
                  dependsOn: [purge-db]
                  parameters: { simulation: com.example.CustomerApiSimulation, loadModel: api-load }
                  timeout: 20m
            loadModels:
              api-load:
                type: RAMP
                stages:
                  - { duration: 2m, usersPerSecond: 10 }
                  - { duration: 5m, usersPerSecond: 100 }
            """;

        @Test
        @DisplayName("parse reference YAML from String successfully")
        void parseReferenceYamlFromString() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);

            assertNotNull(scenario);
            assertEquals("customer-api-perf", scenario.id().value());
            assertEquals("Customer API Campaign", scenario.name());
            assertEquals("1.0.0", scenario.version());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
        }

        @Test
        @DisplayName("parse reference YAML from InputStream successfully")
        void parseReferenceYamlFromInputStream() {
            var is = new ByteArrayInputStream(REFERENCE_YAML.getBytes(StandardCharsets.UTF_8));
            ScenarioDefinition scenario = parser.parse(is);

            assertNotNull(scenario);
            assertEquals("customer-api-perf", scenario.id().value());
        }

        @Test
        @DisplayName("scenario tags are correct")
        void scenarioTags() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            assertEquals(List.of("performance", "regression"), scenario.tags());
        }

        @Test
        @DisplayName("scenario metadata is correct")
        void scenarioMetadata() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            assertEquals("team-a", scenario.metadata().get("owner"));
            assertEquals("PERF-123", scenario.metadata().get("jira"));
        }

        @Test
        @DisplayName("scenario execution mode is DISTRIBUTED")
        void executionModeDistributed() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
        }

        @Test
        @DisplayName("scenario has 2 steps")
        void scenarioStepsCount() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            assertEquals(2, scenario.steps().size());
        }

        @Test
        @DisplayName("first step: purge-db — all fields verified")
        void firstStepPurgeDb() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            StepDefinition step = scenario.steps().get(0);

            assertEquals("purge-db", step.id().value());
            assertEquals("database", step.taskName());
            assertEquals(Phase.PREPARATION, step.phase());
            assertEquals(List.of(), step.requiredContexts());
            assertEquals(List.of(), step.dependsOn());
            assertEquals("PURGE", step.parameters().get("operation"));
            assertEquals("customer-db", step.parameters().get("datasource"));
            assertEquals(Duration.ofSeconds(30), step.timeout());
            assertNull(step.retryPolicy());
        }

        @Test
        @DisplayName("second step: customer-api-load — all fields verified")
        void secondStepCustomerApiLoad() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            StepDefinition step = scenario.steps().get(1);

            assertEquals("customer-api-load", step.id().value());
            assertEquals("gatling", step.taskName());
            assertEquals(Phase.INJECTION, step.phase());
            assertEquals(List.of("purge-db"), step.requiredContexts());
            assertEquals(1, step.dependsOn().size());
            assertEquals("purge-db", step.dependsOn().get(0).value());
            assertEquals("com.example.CustomerApiSimulation", step.parameters().get("simulation"));
            assertEquals("api-load", step.parameters().get("loadModel"));
            assertEquals(Duration.ofMinutes(20), step.timeout());
            assertNull(step.retryPolicy());
        }

        @Test
        @DisplayName("load model: api-load (RAMP type)")
        void loadModelApiLoad() {
            ScenarioDefinition scenario = parser.parse(REFERENCE_YAML);
            Map<String, LoadModel> loadModels = scenario.loadModels();

            assertEquals(1, loadModels.size());
            assertTrue(loadModels.containsKey("api-load"));

            LoadModel lm = loadModels.get("api-load");
            assertEquals(LoadModelType.RAMP, lm.type());

            // stages is a List of Maps
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stages = (List<Map<String, Object>>) lm.parameters().get("stages");
            assertNotNull(stages);
            assertEquals(2, stages.size());
            assertEquals("2m", stages.get(0).get("duration"));
            assertEquals(10, stages.get(0).get("usersPerSecond"));
            assertEquals("5m", stages.get(1).get("duration"));
            assertEquals(100, stages.get(1).get("usersPerSecond"));
        }
    }

    @Nested
    @DisplayName("Steps — timeout and retry")
    class StepsTimeoutRetry {

        @Test
        @DisplayName("step without timeout gets default 5 minutes")
        void stepWithoutTimeoutGetsDefault() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: test-task
                      phase: INJECTION
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            StepDefinition step = scenario.steps().get(0);
            assertEquals(Duration.ofMinutes(5), step.timeout());
        }

        @Test
        @DisplayName("step with custom timeout parses correctly")
        void stepWithCustomTimeout() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: test-task
                      phase: INJECTION
                      timeout: 2h
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(Duration.ofHours(2), scenario.steps().get(0).timeout());
        }

        @Test
        @DisplayName("step with retry policy")
        void stepWithRetryPolicy() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: test-task
                      phase: INJECTION
                      retryPolicy:
                        maxAttempts: 5
                        initialDelay: 10s
                        multiplier: 3.0
                        maxDelay: 2m
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            StepDefinition step = scenario.steps().get(0);
            assertNotNull(step.retryPolicy());
            assertEquals(5, step.retryPolicy().maxAttempts());
            assertEquals(Duration.ofSeconds(10), step.retryPolicy().initialDelay());
            assertEquals(3.0, step.retryPolicy().multiplier());
            assertEquals(Duration.ofMinutes(2), step.retryPolicy().maxDelay());
        }

        @Test
        @DisplayName("step with partial retry policy uses defaults for missing fields")
        void stepWithPartialRetryPolicy() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: test-task
                      phase: INJECTION
                      retryPolicy:
                        maxAttempts: 2
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertNotNull(scenario.steps().get(0).retryPolicy());
            assertEquals(2, scenario.steps().get(0).retryPolicy().maxAttempts());
            assertEquals(Duration.ofSeconds(1), scenario.steps().get(0).retryPolicy().initialDelay());
            assertEquals(2.0, scenario.steps().get(0).retryPolicy().multiplier());
            assertEquals(Duration.ofSeconds(30), scenario.steps().get(0).retryPolicy().maxDelay());
        }
    }

    @Nested
    @DisplayName("Execution modes")
    class ExecutionModes {

        @Test
        @DisplayName("execution mode LOCAL")
        void executionModeLocal() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  execution:
                    mode: LOCAL
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        }

        @Test
        @DisplayName("execution mode DISTRIBUTED")
        void executionModeDistributed() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  execution:
                    mode: DISTRIBUTED
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
        }

        @Test
        @DisplayName("missing execution section defaults to LOCAL")
        void missingExecutionDefaults() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        }
    }

    @Nested
    @DisplayName("Phases")
    class Phases {

        @Test
        @DisplayName("PREPARATION phase")
        void preparationPhase() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: db
                      phase: PREPARATION
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(Phase.PREPARATION, scenario.steps().get(0).phase());
        }

        @Test
        @DisplayName("INJECTION phase")
        void injectionPhase() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: gatling
                      phase: INJECTION
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(Phase.INJECTION, scenario.steps().get(0).phase());
        }

        @Test
        @DisplayName("ASSERTION phase")
        void assertionPhase() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: metric
                      phase: ASSERTION
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(Phase.ASSERTION, scenario.steps().get(0).phase());
        }

        @Test
        @DisplayName("lowercase phase is accepted")
        void lowercasePhase() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: db
                      phase: preparation
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertEquals(Phase.PREPARATION, scenario.steps().get(0).phase());
        }
    }

    @Nested
    @DisplayName("Load models — all 8 types")
    class LoadModels {

        @Test
        @DisplayName("CONSTANT load model")
        void constantLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  my-model:
                    type: CONSTANT
                    usersPerSecond: 100
                    duration: 10m
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("my-model");
            assertEquals(LoadModelType.CONSTANT, lm.type());
            assertEquals(100, lm.parameters().get("usersPerSecond"));
            assertEquals("10m", lm.parameters().get("duration"));
        }

        @Test
        @DisplayName("SPIKE load model")
        void spikeLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  spike-model:
                    type: SPIKE
                    baseUsersPerSecond: 50
                    spikeUsersPerSecond: 500
                    spikeDuration: 30s
                    baseDuration: 5m
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("spike-model");
            assertEquals(LoadModelType.SPIKE, lm.type());
            assertEquals(50, lm.parameters().get("baseUsersPerSecond"));
            assertEquals(500, lm.parameters().get("spikeUsersPerSecond"));
        }

        @Test
        @DisplayName("STAIR load model")
        void stairLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  stair-model:
                    type: STAIR
                    initialUsersPerSecond: 10
                    incrementPerStep: 20
                    stepDuration: 2m
                    steps: 5
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("stair-model");
            assertEquals(LoadModelType.STAIR, lm.type());
            assertEquals(10, lm.parameters().get("initialUsersPerSecond"));
            assertEquals(5, lm.parameters().get("steps"));
        }

        @Test
        @DisplayName("SOAK load model")
        void soakLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  soak-model:
                    type: SOAK
                    usersPerSecond: 50
                    duration: 2h
                    rampUpDuration: 5m
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("soak-model");
            assertEquals(LoadModelType.SOAK, lm.type());
            assertEquals("2h", lm.parameters().get("duration"));
        }

        @Test
        @DisplayName("BURST load model")
        void burstLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  burst-model:
                    type: BURST
                    burstUsersPerSecond: 1000
                    burstDuration: 10s
                    burstCount: 3
                    intervalBetweenBursts: 2m
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("burst-model");
            assertEquals(LoadModelType.BURST, lm.type());
            assertEquals(1000, lm.parameters().get("burstUsersPerSecond"));
            assertEquals(3, lm.parameters().get("burstCount"));
        }

        @Test
        @DisplayName("RAMP_UP_DOWN load model")
        void rampUpDownLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  rud-model:
                    type: RAMP_UP_DOWN
                    usersPerSecond: 200
                    rampUpDuration: 5m
                    holdDuration: 10m
                    rampDownDuration: 5m
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("rud-model");
            assertEquals(LoadModelType.RAMP_UP_DOWN, lm.type());
            assertEquals(200, lm.parameters().get("usersPerSecond"));
        }

        @Test
        @DisplayName("CUSTOM load model")
        void customLoadModel() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  custom-model:
                    type: CUSTOM
                    points:
                      - { time: 0s, usersPerSecond: 0 }
                      - { time: 60s, usersPerSecond: 100 }
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            LoadModel lm = scenario.loadModels().get("custom-model");
            assertEquals(LoadModelType.CUSTOM, lm.type());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = (List<Map<String, Object>>) lm.parameters().get("points");
            assertNotNull(points);
            assertEquals(2, points.size());
        }

        @Test
        @DisplayName("no load models produces empty map")
        void noLoadModels() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertTrue(scenario.loadModels().isEmpty());
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("empty YAML throws ScenarioParsingException")
        void emptyYamlThrows() {
            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse("")
            );
            assertFalse(ex.getErrors().isEmpty());
        }

        @Test
        @DisplayName("null YAML throws ScenarioParsingException")
        void nullYamlThrows() {
            assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse((String) null)
            );
        }

        @Test
        @DisplayName("missing scenario section throws")
        void missingScenarioSection() {
            String yaml = """
                loadModels:
                  test:
                    type: CONSTANT
                    usersPerSecond: 100
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("scenario")));
        }

        @Test
        @DisplayName("missing step id throws")
        void missingStepId() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - task: database
                      phase: PREPARATION
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("id")));
        }

        @Test
        @DisplayName("missing task name throws")
        void missingTaskName() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      phase: PREPARATION
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("task")));
        }

        @Test
        @DisplayName("missing phase throws")
        void missingPhase() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: database
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("phase")));
        }

        @Test
        @DisplayName("invalid phase value throws")
        void invalidPhase() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: database
                      phase: INVALID
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("phase") && e.contains("INVALID")));
        }

        @Test
        @DisplayName("invalid execution mode throws")
        void invalidExecutionMode() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  execution:
                    mode: CLOUD
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("mode") && e.contains("CLOUD")));
        }

        @Test
        @DisplayName("invalid duration in timeout throws")
        void invalidDuration() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: database
                      phase: PREPARATION
                      timeout: thirty-seconds
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("timeout")));
        }

        @Test
        @DisplayName("invalid load model type throws")
        void invalidLoadModelType() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  my-model:
                    type: UNKNOWN_TYPE
                    usersPerSecond: 100
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("UNKNOWN_TYPE")));
        }

        @Test
        @DisplayName("load model without type throws")
        void loadModelWithoutType() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  my-model:
                    usersPerSecond: 100
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("type")));
        }

        @Test
        @DisplayName("multiple errors are collected")
        void multipleErrorsCollected() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - task: database
                      phase: INVALID
                    - id: step2
                      phase: PREPARATION
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            List<String> errors = ex.getErrors();
            assertTrue(errors.size() >= 2, "Expected at least 2 errors, got " + errors.size() + ": " + errors);
        }

        @Test
        @DisplayName("malformed YAML throws ScenarioParsingException")
        void malformedYaml() {
            String yaml = """
                scenario:
                  id: test
                  steps:
                    - id: step1
                    - task: db  !!!bad syntax@@@
                """;

            assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
        }

        @Test
        @DisplayName("getErrors returns immutable list")
        void errorsListIsImmutable() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - task: db
                      phase: INVALID
                """;

            ScenarioParsingException ex = assertThrows(
                ScenarioParsingException.class,
                () -> parser.parse(yaml)
            );
            assertThrows(UnsupportedOperationException.class, () -> ex.getErrors().add("new error"));
        }
    }

    @Nested
    @DisplayName("Scenario immutability")
    class ScenarioImmutability {

        @Test
        @DisplayName("tags list is immutable")
        void tagsImmutable() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  tags: [a, b]
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertThrows(UnsupportedOperationException.class, () -> scenario.tags().add("c"));
        }

        @Test
        @DisplayName("steps list is immutable")
        void stepsImmutable() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: db
                      phase: PREPARATION
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertThrows(UnsupportedOperationException.class, () -> scenario.steps().add(null));
        }

        @Test
        @DisplayName("loadModels map is immutable")
        void loadModelsImmutable() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                loadModels:
                  test:
                    type: CONSTANT
                    usersPerSecond: 100
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertThrows(UnsupportedOperationException.class, () -> scenario.loadModels().put("x", null));
        }

        @Test
        @DisplayName("step parameters map is immutable")
        void stepParamsImmutable() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: db
                      phase: PREPARATION
                      parameters: { key: value }
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertThrows(UnsupportedOperationException.class, () -> scenario.steps().get(0).parameters().put("x", "y"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("scenario without steps")
        void scenarioWithoutSteps() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertTrue(scenario.steps().isEmpty());
        }

        @Test
        @DisplayName("scenario with empty steps list")
        void scenarioWithEmptyStepsList() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps: []
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertTrue(scenario.steps().isEmpty());
        }

        @Test
        @DisplayName("scenario without version parses with null version")
        void scenarioWithoutVersion() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertNull(scenario.version());
        }

        @Test
        @DisplayName("scenario without name parses with null name")
        void scenarioWithoutName() {
            String yaml = """
                scenario:
                  id: test
                  version: 1.0.0
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            assertNull(scenario.name());
        }

        @Test
        @DisplayName("step dependsOn with multiple entries")
        void multipleDependsOn() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  steps:
                    - id: step1
                      task: db
                      phase: PREPARATION
                      dependsOn: []
                    - id: step2
                      task: mock
                      phase: PREPARATION
                      dependsOn: []
                    - id: step3
                      task: gatling
                      phase: INJECTION
                      dependsOn: [step1, step2]
                """;

            ScenarioDefinition scenario = parser.parse(yaml);
            StepDefinition step = scenario.steps().get(2);
            assertEquals(2, step.dependsOn().size());
            assertEquals("step1", step.dependsOn().get(0).value());
            assertEquals("step2", step.dependsOn().get(1).value());
        }

        @Test
        @DisplayName("unknown YAML fields are silently ignored")
        void unknownFieldsIgnored() {
            String yaml = """
                scenario:
                  id: test
                  name: Test
                  version: 1.0.0
                  taskAvailabilityTimeoutSeconds: 120
                  customField: should-be-ignored
                """;

            // Should not throw
            ScenarioDefinition scenario = parser.parse(yaml);
            assertNotNull(scenario);
            assertEquals("test", scenario.id().value());
        }
    }
}
