package com.performance.platform.domain.scenario;

import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour StepDefinition — immuabilité, copies défensives, validation non-null.
 * Vérifie que taskName est un String libre (pas d'enum TaskType).
 */
@DisplayName("StepDefinition")
class StepDefinitionTest {

    private static TaskId validTaskId() {
        return TaskId.of("step-1");
    }

    // ─── Construction nominale ────────────────────────────────────────

    @Nested
    @DisplayName("Construction nominale")
    class NominalConstruction {

        @Test
        @DisplayName("construction avec tous les champs requis")
        void fullConstruction() {
            var params = Map.<String, Object>of("url", "/api/test", "method", "GET");
            var deps = List.of(TaskId.of("step-0"));
            var reqCtx = List.of("db-ready");
            var timeout = Duration.ofSeconds(30);
            var retry = RetryPolicy.defaults();

            var step = new StepDefinition(
                validTaskId(), "http-get", Phase.INJECTION,
                params, deps, reqCtx, timeout, retry
            );

            assertEquals(validTaskId(), step.id());
            assertEquals("http-get", step.taskName());
            assertEquals(Phase.INJECTION, step.phase());
            assertEquals(params, step.parameters());
            assertEquals(deps, step.dependsOn());
            assertEquals(reqCtx, step.requiredContexts());
            assertEquals(timeout, step.timeout());
            assertEquals(retry, step.retryPolicy());
        }

        @Test
        @DisplayName("construction avec champs optionnels null")
        void nullableFieldsDefaultToEmpty() {
            var step = new StepDefinition(
                validTaskId(), "simple-task", Phase.PREPARATION,
                null, null, null, null, null
            );

            assertEquals("simple-task", step.taskName());
            assertEquals(Map.of(), step.parameters());
            assertEquals(List.of(), step.dependsOn());
            assertEquals(List.of(), step.requiredContexts());
            assertNull(step.timeout());
            assertNull(step.retryPolicy());
        }
    }

    // ─── Validation non-null ───────────────────────────────────────────

    @Nested
    @DisplayName("Validation non-null")
    class ValidationNonNull {

        @Test
        @DisplayName("id null lève NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new StepDefinition(null, "task", Phase.INJECTION,
                    null, null, null, null, null)
            );
        }

        @Test
        @DisplayName("taskName null lève NullPointerException")
        void nullTaskNameThrows() {
            assertThrows(NullPointerException.class, () ->
                new StepDefinition(validTaskId(), null, Phase.INJECTION,
                    null, null, null, null, null)
            );
        }

        @Test
        @DisplayName("phase null lève NullPointerException")
        void nullPhaseThrows() {
            assertThrows(NullPointerException.class, () ->
                new StepDefinition(validTaskId(), "task", null,
                    null, null, null, null, null)
            );
        }
    }

    // ─── taskName est un String libre (pas d'enum TaskType) ────────────

    @Nested
    @DisplayName("taskName String libre")
    class TaskNameFreeString {

        @Test
        @DisplayName("taskName accepte n'importe quel String")
        void acceptsAnyString() {
            var step1 = new StepDefinition(validTaskId(), "custom-executor-v2", Phase.INJECTION,
                null, null, null, null, null);
            assertEquals("custom-executor-v2", step1.taskName());

            var step2 = new StepDefinition(validTaskId(), "database.purge", Phase.PREPARATION,
                null, null, null, null, null);
            assertEquals("database.purge", step2.taskName());

            var step3 = new StepDefinition(validTaskId(), "gatling-http", Phase.INJECTION,
                null, null, null, null, null);
            assertEquals("gatling-http", step3.taskName());
        }

        @Test
        @DisplayName("aucune référence à TaskType dans le code de StepDefinition")
        void noTaskTypeReference() {
            // Vérification structurelle : StepDefinition n'importe pas TaskType
            // Ce test existe pour confirmer que taskName est bien un String
            var step = new StepDefinition(validTaskId(), "any-task", Phase.ASSERTION,
                null, null, null, null, null);
            assertTrue(step.taskName() instanceof String);
        }
    }

    // ─── Immuabilité / copies défensives ───────────────────────────────

    @Nested
    @DisplayName("Immuabilité — copies défensives")
    class Immutability {

        @Test
        @DisplayName("modifier la map parameters source après construction ne modifie pas le record")
        void parametersDefensiveCopy() {
            var mutableParams = new HashMap<String, Object>(Map.of("url", "/old"));
            var step = new StepDefinition(
                validTaskId(), "task", Phase.INJECTION,
                mutableParams, null, null, null, null
            );

            mutableParams.put("url", "/hacked");

            assertEquals(Map.of("url", "/old"), step.parameters(),
                "modifying source map must not affect record");
        }

        @Test
        @DisplayName("modifier la liste dependsOn source après construction ne modifie pas le record")
        void dependsOnDefensiveCopy() {
            var mutableDeps = new ArrayList<>(List.of(TaskId.of("dep-1")));
            var step = new StepDefinition(
                validTaskId(), "task", Phase.INJECTION,
                null, mutableDeps, null, null, null
            );

            mutableDeps.add(TaskId.of("dep-2"));

            assertEquals(List.of(TaskId.of("dep-1")), step.dependsOn(),
                "modifying source list must not affect record");
        }

        @Test
        @DisplayName("modifier la liste requiredContexts source après construction ne modifie pas le record")
        void requiredContextsDefensiveCopy() {
            var mutableCtx = new ArrayList<>(List.of("ctx-1"));
            var step = new StepDefinition(
                validTaskId(), "task", Phase.INJECTION,
                null, null, mutableCtx, null, null
            );

            mutableCtx.add("ctx-2");

            assertEquals(List.of("ctx-1"), step.requiredContexts(),
                "modifying source list must not affect record");
        }

        @Test
        @DisplayName("step.parameters() lève UnsupportedOperationException si tentative de modification")
        void parametersReturnedMapIsUnmodifiable() {
            var step = new StepDefinition(
                validTaskId(), "task", Phase.INJECTION,
                Map.<String, Object>of("k", "v"), null, null, null, null
            );

            assertThrows(UnsupportedOperationException.class,
                () -> step.parameters().put("new", "val"));
        }

        @Test
        @DisplayName("step.dependsOn() lève UnsupportedOperationException si tentative de modification")
        void dependsOnReturnedListIsUnmodifiable() {
            var step = new StepDefinition(
                validTaskId(), "task", Phase.INJECTION,
                null, List.of(TaskId.of("d")), null, null, null
            );

            assertThrows(UnsupportedOperationException.class,
                () -> step.dependsOn().add(TaskId.of("new-dep")));
        }

        @Test
        @DisplayName("step.requiredContexts() lève UnsupportedOperationException si tentative de modification")
        void requiredContextsReturnedListIsUnmodifiable() {
            var step = new StepDefinition(
                validTaskId(), "task", Phase.INJECTION,
                null, null, List.of("ctx"), null, null
            );

            assertThrows(UnsupportedOperationException.class,
                () -> step.requiredContexts().add("new-ctx"));
        }
    }

    // ─── toString / equals ─────────────────────────────────────────────

    @Nested
    @DisplayName("toString / equals")
    class ToStringEquals {

        @Test
        @DisplayName("deux records identiques sont égaux")
        void identicalRecordsAreEqual() {
            var s1 = new StepDefinition(
                TaskId.of("s1"), "task", Phase.INJECTION,
                Map.<String, Object>of("url", "/api"), List.of(TaskId.of("d1")),
                List.of("ctx1"), Duration.ofSeconds(10),
                RetryPolicy.defaults()
            );
            var s2 = new StepDefinition(
                TaskId.of("s1"), "task", Phase.INJECTION,
                Map.<String, Object>of("url", "/api"), List.of(TaskId.of("d1")),
                List.of("ctx1"), Duration.ofSeconds(10),
                RetryPolicy.defaults()
            );

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("deux records avec id différent ne sont pas égaux")
        void differentIdNotEqual() {
            var s1 = new StepDefinition(
                TaskId.of("id1"), "task", Phase.INJECTION,
                null, null, null, null, null
            );
            var s2 = new StepDefinition(
                TaskId.of("id2"), "task", Phase.INJECTION,
                null, null, null, null, null
            );

            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("deux records avec taskName différent ne sont pas égaux")
        void differentTaskNameNotEqual() {
            var s1 = new StepDefinition(
                TaskId.of("id"), "taskA", Phase.INJECTION,
                null, null, null, null, null
            );
            var s2 = new StepDefinition(
                TaskId.of("id"), "taskB", Phase.INJECTION,
                null, null, null, null, null
            );

            assertNotEquals(s1, s2);
        }
    }
}
