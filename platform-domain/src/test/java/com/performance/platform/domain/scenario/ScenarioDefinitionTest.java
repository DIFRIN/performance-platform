package com.performance.platform.domain.scenario;

import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour ScenarioDefinition — immuabilité, copies défensives, validation.
 */
@DisplayName("ScenarioDefinition")
class ScenarioDefinitionTest {

    private static ScenarioId validId() {
        return ScenarioId.of("scenario-1");
    }

    private static StepDefinition validStep() {
        return new StepDefinition(
            TaskId.of("step-1"), "http-get", Phase.INJECTION,
            Map.of(), List.of(), List.of(), null, null
        );
    }

    private static LoadModel validLoadModel() {
        return new LoadModel(LoadModelType.CONSTANT, Map.of("users", 10));
    }

    // ─── Construction nominale ────────────────────────────────────────

    @Nested
    @DisplayName("Construction nominale")
    class NominalConstruction {

        @Test
        @DisplayName("construction avec tous les champs")
        void fullConstruction() {
            var steps = List.of(validStep());
            var loadModels = Map.of("main", validLoadModel());
            var scenario = new ScenarioDefinition(
                validId(), "test-scenario", "1.0",
                List.of("smoke", "critical"),
                Map.of("owner", "team-a"),
                ExecutionMode.DISTRIBUTED,
                steps, loadModels
            );

            assertEquals(validId(), scenario.id());
            assertEquals("test-scenario", scenario.name());
            assertEquals("1.0", scenario.version());
            assertEquals(List.of("smoke", "critical"), scenario.tags());
            assertEquals(Map.of("owner", "team-a"), scenario.metadata());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(steps, scenario.steps());
            assertEquals(loadModels, scenario.loadModels());
        }

        @Test
        @DisplayName("construction avec champs optionnels null")
        void nullableFieldsDefaultToEmpty() {
            var scenario = new ScenarioDefinition(
                validId(), null, null, null, null, null, null, null
            );

            assertEquals(validId(), scenario.id());
            assertNull(scenario.name());
            assertNull(scenario.version());
            assertEquals(List.of(), scenario.tags());
            assertEquals(Map.of(), scenario.metadata());
            assertNull(scenario.executionMode());
            assertEquals(List.of(), scenario.steps());
            assertEquals(Map.of(), scenario.loadModels());
        }
    }

    // ─── Validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("id null lève NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new ScenarioDefinition(null, "name", null, null, null, null, null, null)
            );
        }
    }

    // ─── Immuabilité / copies défensives ───────────────────────────────

    @Nested
    @DisplayName("Immuabilité — copies défensives")
    class Immutability {

        @Test
        @DisplayName("modifier la liste tags source après construction ne modifie pas le record")
        void tagsDefensiveCopy() {
            var mutableTags = new ArrayList<>(List.of("tag1", "tag2"));
            var scenario = new ScenarioDefinition(
                validId(), null, null, mutableTags, null, null, null, null
            );

            mutableTags.add("tag3");

            assertEquals(List.of("tag1", "tag2"), scenario.tags(),
                "modifying source list must not affect record");
        }

        @Test
        @DisplayName("modifier la map metadata source après construction ne modifie pas le record")
        void metadataDefensiveCopy() {
            var mutableMeta = new HashMap<>(Map.of("key1", "val1"));
            var scenario = new ScenarioDefinition(
                validId(), null, null, null, mutableMeta, null, null, null
            );

            mutableMeta.put("key2", "val2");

            assertEquals(Map.of("key1", "val1"), scenario.metadata(),
                "modifying source map must not affect record");
        }

        @Test
        @DisplayName("modifier la liste steps source après construction ne modifie pas le record")
        void stepsDefensiveCopy() {
            var mutableSteps = new ArrayList<>(List.of(validStep()));
            var scenario = new ScenarioDefinition(
                validId(), null, null, null, null, null, mutableSteps, null
            );

            mutableSteps.add(validStep());

            assertEquals(1, scenario.steps().size(),
                "modifying source list must not affect record");
        }

        @Test
        @DisplayName("modifier la map loadModels source après construction ne modifie pas le record")
        void loadModelsDefensiveCopy() {
            var mutableModels = new HashMap<>(Map.of("main", validLoadModel()));
            var scenario = new ScenarioDefinition(
                validId(), null, null, null, null, null, null, mutableModels
            );

            mutableModels.put("extra", validLoadModel());

            assertEquals(1, scenario.loadModels().size(),
                "modifying source map must not affect record");
        }

        @Test
        @DisplayName("record.tags() lève UnsupportedOperationException si tentative de modification")
        void tagsReturnedListIsUnmodifiable() {
            var scenario = new ScenarioDefinition(
                validId(), null, null, List.of("tag1"), null, null, null, null
            );

            assertThrows(UnsupportedOperationException.class,
                () -> scenario.tags().add("new-tag"));
        }

        @Test
        @DisplayName("record.steps() lève UnsupportedOperationException si tentative de modification")
        void stepsReturnedListIsUnmodifiable() {
            var scenario = new ScenarioDefinition(
                validId(), null, null, null, null, null, List.of(validStep()), null
            );

            assertThrows(UnsupportedOperationException.class,
                () -> scenario.steps().add(validStep()));
        }

        @Test
        @DisplayName("record.loadModels() lève UnsupportedOperationException si tentative de modification")
        void loadModelsReturnedMapIsUnmodifiable() {
            var scenario = new ScenarioDefinition(
                validId(), null, null, null, null, null, null, Map.of("m", validLoadModel())
            );

            assertThrows(UnsupportedOperationException.class,
                () -> scenario.loadModels().put("new", validLoadModel()));
        }
    }

    // ─── toString / equals ─────────────────────────────────────────────

    @Nested
    @DisplayName("toString / equals")
    class ToStringEquals {

        @Test
        @DisplayName("deux records identiques sont égaux")
        void identicalRecordsAreEqual() {
            var steps = List.of(validStep());
            var models = Map.of("main", validLoadModel());

            var s1 = new ScenarioDefinition(
                ScenarioId.of("s1"), "name", "1.0",
                List.of("tag"), Map.of("k", "v"),
                ExecutionMode.LOCAL, steps, models
            );
            var s2 = new ScenarioDefinition(
                ScenarioId.of("s1"), "name", "1.0",
                List.of("tag"), Map.of("k", "v"),
                ExecutionMode.LOCAL, steps, models
            );

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("deux records avec id différent ne sont pas égaux")
        void differentIdNotEqual() {
            var s1 = new ScenarioDefinition(
                ScenarioId.of("s1"), null, null, null, null, null, null, null
            );
            var s2 = new ScenarioDefinition(
                ScenarioId.of("s2"), null, null, null, null, null, null, null
            );

            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("toString contient le nom du record")
        void toStringContainsRecordName() {
            var scenario = new ScenarioDefinition(
                validId(), "my-scenario", null, null, null, null, null, null
            );

            assertTrue(scenario.toString().contains("ScenarioDefinition"));
            assertTrue(scenario.toString().contains("my-scenario"));
        }
    }
}
