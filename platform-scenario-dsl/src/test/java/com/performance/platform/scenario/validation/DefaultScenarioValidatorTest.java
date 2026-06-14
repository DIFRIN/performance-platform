package com.performance.platform.scenario.validation;

import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultScenarioValidator")
class DefaultScenarioValidatorTest {

    private DefaultScenarioValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DefaultScenarioValidator();
    }

    // ==================== Helper factories ====================

    private ScenarioDefinition scenarioWithId(String idValue) {
        return new ScenarioDefinition(
            ScenarioId.of(idValue), "Test Scenario", "1.0.0",
            List.of(), Map.of(), ExecutionMode.LOCAL,
            List.of(), Map.of()
        );
    }

    private StepDefinition step(String id, String taskName, Phase phase) {
        return new StepDefinition(
            TaskId.of(id), taskName, phase,
            Map.of(), List.of(), List.of(), null, null
        );
    }

    private StepDefinition stepWithDependsOn(String id, String taskName, Phase phase, String... depIds) {
        List<TaskId> deps = java.util.Arrays.stream(depIds).map(TaskId::of).toList();
        return new StepDefinition(
            TaskId.of(id), taskName, phase,
            Map.of(), deps, List.of(), null, null
        );
    }

    private StepDefinition stepWithRequiredContexts(String id, String taskName, Phase phase, List<String> contexts) {
        return new StepDefinition(
            TaskId.of(id), taskName, phase,
            Map.of(), List.of(), contexts, null, null
        );
    }

    private StepDefinition stepWithParams(String id, String taskName, Phase phase, Map<String, Object> params) {
        return new StepDefinition(
            TaskId.of(id), taskName, phase,
            params, List.of(), List.of(), null, null
        );
    }

    // ==================== Reference YAML validation ====================

    @Nested
    @DisplayName("Reference YAML — valid scenario")
    class ReferenceYamlValid {

        @Test
        @DisplayName("reference YAML structure yields valid=true")
        void referenceYamlValid() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("customer-api-perf"),
                "Customer API Campaign",
                "1.0.0",
                List.of("performance", "regression"),
                Map.of("owner", "team-a", "jira", "PERF-123"),
                ExecutionMode.DISTRIBUTED,
                List.of(
                    new StepDefinition(
                        TaskId.of("purge-db"), "database", Phase.PREPARATION,
                        Map.of("operation", "PURGE", "datasource", "customer-db"),
                        List.of(), List.of(),
                        Duration.ofSeconds(30), null
                    ),
                    new StepDefinition(
                        TaskId.of("customer-api-load"), "gatling", Phase.INJECTION,
                        Map.of("simulation", "com.example.CustomerApiSimulation", "loadModel", "api-load"),
                        List.of(TaskId.of("purge-db")), List.of("purge-db"),
                        Duration.ofMinutes(20), null
                    )
                ),
                Map.of("api-load", new LoadModel(LoadModelType.RAMP, Map.of(
                    "stages", List.of(
                        Map.of("duration", "2m", "usersPerSecond", 10),
                        Map.of("duration", "5m", "usersPerSecond", 100)
                    )
                )))
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.valid(), "Reference YAML should be valid. Errors: " + result.errorMessages());
        }

        @Test
        @DisplayName("reference YAML has no errors")
        void referenceYamlNoErrors() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("customer-api-perf"),
                "Customer API Campaign",
                "1.0.0",
                List.of("performance", "regression"),
                Map.of("owner", "team-a"),
                ExecutionMode.DISTRIBUTED,
                List.of(
                    new StepDefinition(
                        TaskId.of("purge-db"), "database", Phase.PREPARATION,
                        Map.of("operation", "PURGE"), List.of(), List.of(),
                        Duration.ofSeconds(30), null
                    ),
                    new StepDefinition(
                        TaskId.of("customer-api-load"), "gatling", Phase.INJECTION,
                        Map.of("simulation", "com.example.CustomerApiSimulation", "loadModel", "api-load"),
                        List.of(TaskId.of("purge-db")), List.of("purge-db"),
                        Duration.ofMinutes(20), null
                    )
                ),
                Map.of("api-load", new LoadModel(LoadModelType.RAMP, Map.of()))
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errors().isEmpty(), "Expected no errors, got: " + result.errorMessages());
        }

        @Test
        @DisplayName("reference YAML has warnings (no ASSERTION)")
        void referenceYamlHasWarnings() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("customer-api-perf"),
                "Customer API Campaign",
                "1.0.0",
                List.of("performance"),
                Map.of("owner", "team-a"),
                ExecutionMode.DISTRIBUTED,
                List.of(
                    new StepDefinition(
                        TaskId.of("purge-db"), "database", Phase.PREPARATION,
                        Map.of(), List.of(), List.of(),
                        Duration.ofSeconds(30), null
                    ),
                    new StepDefinition(
                        TaskId.of("customer-api-load"), "gatling", Phase.INJECTION,
                        Map.of("loadModel", "api-load"),
                        List.of(TaskId.of("purge-db")), List.of("purge-db"),
                        Duration.ofMinutes(20), null
                    )
                ),
                Map.of("api-load", new LoadModel(LoadModelType.RAMP, Map.of()))
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.valid());
            // Should have a warning about no ASSERTION step
            assertTrue(result.warnings().stream()
                .anyMatch(w -> w.field().equals("steps") && w.message().contains("ASSERTION")));
        }
    }

    // ==================== Scenario ID validation ====================

    @Nested
    @DisplayName("Scenario ID validation")
    class ScenarioIdValidation {

        @Test
        @DisplayName("valid id passes")
        void validId() {
            ScenarioDefinition scenario = scenarioWithId("my-scenario-123");
            ValidationResult result = validator.validate(scenario);
            assertFalse(result.errorMessages().stream().anyMatch(e -> e.contains("scenario.id")));
        }

        @Test
        @DisplayName("id exceeds max length 100")
        void idTooLong() {
            String longId = "a".repeat(101);
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of(longId), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("scenario.id") && e.contains("100")));
        }

        @Test
        @DisplayName("id exactly 100 chars passes")
        void idExactly100() {
            String id100 = "a".repeat(100);
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of(id100), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("scenario.id")));
        }

        @Test
        @DisplayName("id with invalid characters fails")
        void idWithInvalidChars() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("bad@id!"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("alphanumeric")));
        }

        @Test
        @DisplayName("empty id fails")
        void emptyId() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of(""), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            // Empty string is blank, should error on "required"
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("scenario.id")));
        }
    }

    // ==================== Version validation ====================

    @Nested
    @DisplayName("Version validation")
    class VersionValidation {

        @Test
        @DisplayName("valid semver passes")
        void validSemver() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("version")));
        }

        @Test
        @DisplayName("null version fails")
        void nullVersion() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", null,
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("version") && e.contains("required")));
        }

        @Test
        @DisplayName("blank version fails")
        void blankVersion() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "   ",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("version") && e.contains("required")));
        }

        @Test
        @DisplayName("non-semver version fails")
        void nonSemver() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "v1.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("semver")));
        }

        @Test
        @DisplayName("prerelease semver like 1.0.0-alpha fails")
        void prereleaseSemverFails() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0-alpha",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("semver")));
        }

        @Test
        @DisplayName("leading zeros in version number fails")
        void leadingZeros() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "01.02.03",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("s1", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("semver")));
        }
    }

    // ==================== Step ID uniqueness ====================

    @Nested
    @DisplayName("Step ID uniqueness")
    class StepIdUniqueness {

        @Test
        @DisplayName("duplicate step id fails")
        void duplicateStepId() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("my-step", "db", Phase.PREPARATION),
                    step("my-step", "http", Phase.INJECTION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("step.id") && e.contains("Duplicate")));
        }

        @Test
        @DisplayName("unique step ids pass")
        void uniqueStepIds() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("step-1", "db", Phase.PREPARATION),
                    step("step-2", "http", Phase.INJECTION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("Duplicate")));
        }
    }

    // ==================== DAG cycle detection ====================

    @Nested
    @DisplayName("DAG cycle detection")
    class DagCycleDetection {

        @Test
        @DisplayName("scenario with cycle yields valid=false")
        void scenarioWithCycle() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithDependsOn("A", "taskA", Phase.PREPARATION, "B"),
                    stepWithDependsOn("B", "taskB", Phase.PREPARATION, "A")
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("Cycle") || e.contains("cycle") || e.contains("DAG")));
        }

        @Test
        @DisplayName("self-loop yields cycle error")
        void selfLoop() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithDependsOn("A", "taskA", Phase.PREPARATION, "A")
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
        }

        @Test
        @DisplayName("acyclic graph passes")
        void acyclicGraph() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithDependsOn("A", "taskA", Phase.PREPARATION),
                    stepWithDependsOn("B", "taskB", Phase.PREPARATION, "A"),
                    stepWithDependsOn("C", "taskC", Phase.INJECTION, "A", "B")
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e ->
                e.contains("Cycle") || e.contains("cycle") || e.contains("DAG")));
        }

        @Test
        @DisplayName("no dependsOn — acyclic")
        void noDependsOn() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "taskA", Phase.PREPARATION),
                    step("B", "taskB", Phase.PREPARATION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("Cycle")));
        }

        @Test
        @DisplayName("dependsOn references non-existent step")
        void dependsOnNonExistent() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithDependsOn("A", "taskA", Phase.PREPARATION, "GHOST")
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("GHOST") && e.contains("does not exist")));
        }
    }

    // ==================== Required contexts validation ====================

    @Nested
    @DisplayName("Required contexts validation")
    class RequiredContextsValidation {

        @Test
        @DisplayName("requiredContext referencing a prior (dependency) step passes")
        void requiredContextPriorDependency() {
            // A is a dependency of B, so B can require context from A
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithDependsOn("A", "taskA", Phase.PREPARATION),
                    new StepDefinition(
                        TaskId.of("B"), "taskB", Phase.INJECTION,
                        Map.of(), List.of(TaskId.of("A")), List.of("A"),
                        null, null
                    )
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.valid(), "Errors: " + result.errorMessages());
        }

        @Test
        @DisplayName("requiredContext referencing a non-existent step fails")
        void requiredContextNonExistent() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithRequiredContexts("A", "taskA", Phase.PREPARATION, List.of("GHOST"))
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("GHOST") && e.contains("does not exist")));
        }

        @Test
        @DisplayName("requiredContext referencing a step not prior in DAG fails")
        void requiredContextNotPrior() {
            // A and B are independent (no dependency), B requires context from A
            // A is not prior in DAG
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "taskA", Phase.PREPARATION),
                    new StepDefinition(
                        TaskId.of("B"), "taskB", Phase.INJECTION,
                        Map.of(), List.of(), List.of("A"),
                        null, null
                    )
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("not prior") || e.contains("prior in the execution DAG")));
        }

        @Test
        @DisplayName("requiredContext transitive dependency passes")
        void requiredContextTransitive() {
            // A -> B -> C, C requires context from A (transitive dependency)
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "taskA", Phase.PREPARATION),
                    stepWithDependsOn("B", "taskB", Phase.PREPARATION, "A"),
                    new StepDefinition(
                        TaskId.of("C"), "taskC", Phase.INJECTION,
                        Map.of(), List.of(TaskId.of("B")), List.of("A"),
                        null, null
                    )
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.valid(), "Transitive dependency should be valid. Errors: " + result.errorMessages());
        }

        @Test
        @DisplayName("blank requiredContext entry fails")
        void blankRequiredContext() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithRequiredContexts("A", "taskA", Phase.PREPARATION, List.of("  "))
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e -> e.contains("blank")));
        }
    }

    // ==================== Load model references ====================

    @Nested
    @DisplayName("Load model references")
    class LoadModelReferences {

        @Test
        @DisplayName("gatling step with existing loadModel passes")
        void gatlingWithExistingLoadModel() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithParams("A", "gatling", Phase.INJECTION, Map.of("loadModel", "my-model"))
                ),
                Map.of("my-model", new LoadModel(LoadModelType.CONSTANT, Map.of()))
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.valid(), "Errors: " + result.errorMessages());
        }

        @Test
        @DisplayName("gatling step with missing loadModel fails")
        void gatlingWithMissingLoadModel() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithParams("A", "gatling", Phase.INJECTION, Map.of("loadModel", "no-such-model"))
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("no-such-model") && e.contains("loadModel")));
        }

        @Test
        @DisplayName("non-gatling step with missing loadModel passes")
        void nonGatlingMissingLoadModel() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    stepWithParams("A", "database", Phase.PREPARATION, Map.of("loadModel", "no-such"))
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            // Should not fail on loadModel for non-gatling steps
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("loadModel")));
        }

        @Test
        @DisplayName("gatling step without loadModel parameter passes (no reference)")
        void gatlingWithoutLoadModelRef() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "gatling", Phase.INJECTION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("loadModel")));
        }
    }

    // ==================== Task name ====================

    @Nested
    @DisplayName("Task name validation")
    class TaskNameValidation {

        @Test
        @DisplayName("blank task name fails")
        void blankTaskName() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "", Phase.PREPARATION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("task") && e.contains("required")));
        }

        @Test
        @DisplayName("whitespace-only task name fails")
        void whitespaceTaskName() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "   ", Phase.PREPARATION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("task") && e.contains("required")));
        }
    }

    // ==================== Phase validation ====================

    @Nested
    @DisplayName("Phase validation")
    class PhaseValidation {

        @Test
        @DisplayName("valid phase passes (PREPARATION)")
        void validPhasePreparation() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("A", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("phase")));
        }

        @Test
        @DisplayName("valid phase passes (ASSERTION)")
        void validPhaseAssertion() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("A", "metric", Phase.ASSERTION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.errorMessages().stream().noneMatch(e -> e.contains("phase")));
        }
    }

    // ==================== Warnings ====================

    @Nested
    @DisplayName("Warnings")
    class WarningsTests {

        @Test
        @DisplayName("no metadata.owner produces warning")
        void noOwnerWarning() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("A", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().anyMatch(w ->
                w.field().equals("metadata") && w.message().contains("owner")));
        }

        @Test
        @DisplayName("metadata with owner produces no owner warning")
        void withOwner() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of("owner", "team-a"), ExecutionMode.LOCAL,
                List.of(step("A", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().noneMatch(w ->
                w.field().equals("metadata") && w.message().contains("owner")));
        }

        @Test
        @DisplayName("no ASSERTION step produces warning")
        void noAssertionWarning() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of("owner", "team-a"), ExecutionMode.LOCAL,
                List.of(step("A", "db", Phase.PREPARATION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().anyMatch(w ->
                w.field().equals("steps") && w.message().contains("ASSERTION")));
        }

        @Test
        @DisplayName("ASSERTION step present suppresses the warning")
        void assertionPresent() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of("owner", "team-a"), ExecutionMode.LOCAL,
                List.of(
                    step("A", "db", Phase.PREPARATION),
                    step("B", "metric", Phase.ASSERTION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().noneMatch(w ->
                w.field().equals("steps") && w.message().contains("ASSERTION step")));
        }

        @Test
        @DisplayName("INJECTION step without timeout produces warning")
        void injectionWithoutTimeoutWarning() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of("owner", "team-a"), ExecutionMode.LOCAL,
                List.of(step("A", "gatling", Phase.INJECTION)),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().anyMatch(w ->
                w.field().contains("timeout") && w.message().contains("INJECTION")));
        }

        @Test
        @DisplayName("INJECTION step with timeout produces no warning")
        void injectionWithTimeout() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of("owner", "team-a"), ExecutionMode.LOCAL,
                List.of(new StepDefinition(
                    TaskId.of("A"), "gatling", Phase.INJECTION,
                    Map.of(), List.of(), List.of(),
                    Duration.ofMinutes(10), null
                )),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().noneMatch(w -> w.field().contains("timeout")));
        }

        @Test
        @DisplayName("ASSERTION step without requiredContexts produces warning")
        void assertionWithoutContextsWarning() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of("owner", "team-a"), ExecutionMode.LOCAL,
                List.of(
                    step("A", "db", Phase.PREPARATION),
                    step("B", "metric", Phase.ASSERTION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.warnings().stream().anyMatch(w ->
                w.field().contains("requiredContexts") && w.message().contains("ASSERTION")));
        }
    }

    // ==================== Edge cases ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("null scenario yields error")
        void nullScenario() {
            ValidationResult result = validator.validate(null);
            assertFalse(result.valid());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).message().contains("null"));
        }

        @Test
        @DisplayName("empty steps list yields error")
        void emptySteps() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(), Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            assertTrue(result.errorMessages().stream().anyMatch(e ->
                e.contains("at least one step")));
        }

        @Test
        @DisplayName("multiple errors collected")
        void multipleErrorsCollected() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("bad@id"), "Test", "v1",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(
                    step("A", "   ", Phase.PREPARATION)
                ),
                Map.of()
            );

            ValidationResult result = validator.validate(scenario);
            assertFalse(result.valid());
            // Should have at least: id format, version semver, task name, phase
            assertTrue(result.errors().size() >= 3,
                "Expected at least 3 errors, got " + result.errors().size() + ": " + result.errorMessages());
        }

        @Test
        @DisplayName("valid simple scenario with all required fields")
        void validSimpleScenario() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("simple-test"), "Simple Test", "2.0.0",
                List.of("smoke"), Map.of("owner", "qa-team"), ExecutionMode.LOCAL,
                List.of(
                    step("setup", "database", Phase.PREPARATION),
                    new StepDefinition(
                        TaskId.of("load"), "gatling", Phase.INJECTION,
                        Map.of("loadModel", "constant-load"), List.of(TaskId.of("setup")), List.of("setup"),
                        Duration.ofMinutes(5), null
                    ),
                    new StepDefinition(
                        TaskId.of("verify"), "metric-assertion", Phase.ASSERTION,
                        Map.of("metric", "p99"), List.of(TaskId.of("load")), List.of("load"),
                        Duration.ofMinutes(1), null
                    )
                ),
                Map.of("constant-load", new LoadModel(LoadModelType.CONSTANT, Map.of("usersPerSecond", 50)))
            );

            ValidationResult result = validator.validate(scenario);
            assertTrue(result.valid(), "Expected valid, got errors: " + result.errorMessages());
            assertTrue(result.warnings().isEmpty(), "Expected no warnings, got: " + result.warnings());
        }

        @Test
        @DisplayName("ValidationResult immutability: errors list")
        void errorsListImmutability() {
            ScenarioDefinition scenario = scenarioWithId("test");
            ValidationResult result = validator.validate(scenario);
            assertThrows(UnsupportedOperationException.class, () ->
                result.errors().add(new ValidationError("x", "y", null)));
        }

        @Test
        @DisplayName("ValidationResult immutability: warnings list")
        void warningsListImmutability() {
            ScenarioDefinition scenario = new ScenarioDefinition(
                ScenarioId.of("test"), "Test", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL,
                List.of(step("A", "db", Phase.PREPARATION)),
                Map.of()
            );
            ValidationResult result = validator.validate(scenario);
            assertThrows(UnsupportedOperationException.class, () ->
                result.warnings().add(new ValidationWarning("x", "y")));
        }

        @Test
        @DisplayName("ValidationResult.valid() factory method")
        void validFactoryMethod() {
            List<ValidationWarning> warnings = List.of(new ValidationWarning("f", "msg"));
            ValidationResult result = ValidationResult.valid(warnings);
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
            assertEquals(1, result.warnings().size());
        }

        @Test
        @DisplayName("ValidationResult.invalid() factory method")
        void invalidFactoryMethod() {
            List<ValidationError> errors = List.of(new ValidationError("f", "msg", null));
            List<ValidationWarning> warnings = List.of(new ValidationWarning("f", "warn"));
            ValidationResult result = ValidationResult.invalid(errors, warnings);
            assertFalse(result.valid());
            assertEquals(1, result.errors().size());
            assertEquals(1, result.warnings().size());
        }
    }
}
