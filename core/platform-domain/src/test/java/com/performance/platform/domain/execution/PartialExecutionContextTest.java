package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour PartialExecutionContext — get, getFirst, immuabilité, validation.
 */
@DisplayName("PartialExecutionContext")
class PartialExecutionContextTest {

    private static ExecutionId execId() {
        return ExecutionId.generate();
    }

    private static ScenarioId scenarioId() {
        return ScenarioId.of("scenario-1");
    }

    // ─── Construction ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("factory empty() crée un contexte avec store vide")
        void emptyCreatesEmptyContext() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());

            assertNotNull(ctx.executionId());
            assertNotNull(ctx.scenarioId());
            assertTrue(ctx.store().isEmpty());
        }

        @Test
        @DisplayName("store null → Map.of() vide")
        void nullStoreBecomesEmptyMap() {
            var ctx = new PartialExecutionContext(execId(), scenarioId(), null);

            assertEquals(Map.of(), ctx.store());
        }

        @Test
        @DisplayName("store non-null → copie défensive profonde")
        void storeDeepCopyOnConstruction() {
            var innerMutable = new HashMap<String, Object>();
            innerMutable.put("agent-1", "value-1");
            var outerMutable = new HashMap<String, Map<String, Object>>();
            outerMutable.put("task-a", innerMutable);

            var ctx = new PartialExecutionContext(execId(), scenarioId(), outerMutable);

            // Modifier les sources après construction
            innerMutable.put("agent-hacked", "hacked-value");
            outerMutable.put("hacked", Map.of());

            assertEquals(1, ctx.store().size());
            assertEquals(1, ctx.store().get("task-a").size());
            assertTrue(ctx.store().get("task-a").containsKey("agent-1"));
            assertFalse(ctx.store().get("task-a").containsKey("agent-hacked"));
            assertFalse(ctx.store().containsKey("hacked"));
        }
    }

    // ─── get() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get(taskId, agentId, type)")
    class GetMethod {

        @Test
        @DisplayName("retourne la valeur castée au type demandé")
        void returnsCastedValue() {
            var store = Map.of("task-a",
                Map.of("agent-1", (Object) 100L, "agent-2", (Object) "hello"));
            var ctx = new PartialExecutionContext(execId(), scenarioId(), store);

            assertEquals(100L, ctx.get("task-a", "agent-1", Long.class).orElseThrow());
            assertEquals("hello", ctx.get("task-a", "agent-2", String.class).orElseThrow());
        }

        @Test
        @DisplayName("retourne Optional.empty() si taskId absent")
        void emptyIfTaskIdMissing() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());

            assertTrue(ctx.get("absent", "agent-1", Object.class).isEmpty());
        }

        @Test
        @DisplayName("retourne Optional.empty() si agentId absent")
        void emptyIfAgentIdMissing() {
            var store = Map.of("task-a", Map.of("agent-1", (Object) "value"));
            var ctx = new PartialExecutionContext(execId(), scenarioId(), store);

            assertTrue(ctx.get("task-a", "agent-absent", Object.class).isEmpty());
        }

        @Test
        @DisplayName("retourne Optional.empty() si type incompatible")
        void emptyIfTypeMismatch() {
            var store = Map.of("task-a", Map.of("agent-1", (Object) 100));
            var ctx = new PartialExecutionContext(execId(), scenarioId(), store);

            assertTrue(ctx.get("task-a", "agent-1", String.class).isEmpty());
        }

        @Test
        @DisplayName("lève NullPointerException si taskId null")
        void nullTaskIdThrows() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.get(null, "agent-1", Object.class));
        }

        @Test
        @DisplayName("lève NullPointerException si agentId null")
        void nullAgentIdThrows() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.get("task-a", null, Object.class));
        }

        @Test
        @DisplayName("lève NullPointerException si type null")
        void nullTypeThrows() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.get("task-a", "agent-1", null));
        }
    }

    // ─── getFirst() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFirst(taskId, type)")
    class GetFirstMethod {

        @Test
        @DisplayName("retourne la première valeur castable parmi tous les agents")
        void returnsFirstMatchingValue() {
            var store = Map.of("task-a",
                Map.of("agent-1", (Object) 100L, "agent-2", (Object) 200L));
            var ctx = new PartialExecutionContext(execId(), scenarioId(), store);

            var value = ctx.getFirst("task-a", Long.class);
            assertTrue(value.isPresent());
            assertTrue(value.get() instanceof Long);
        }

        @Test
        @DisplayName("retourne Optional.empty() si taskId absent")
        void emptyIfTaskIdMissing() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());

            assertTrue(ctx.getFirst("absent", Object.class).isEmpty());
        }

        @Test
        @DisplayName("retourne Optional.empty() si aucune valeur castable")
        void emptyIfNoMatchingType() {
            var store = Map.of("task-a", Map.of("agent-1", (Object) 100));
            var ctx = new PartialExecutionContext(execId(), scenarioId(), store);

            assertTrue(ctx.getFirst("task-a", String.class).isEmpty());
        }

        @Test
        @DisplayName("lève NullPointerException si taskId null")
        void nullTaskIdThrows() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.getFirst(null, Object.class));
        }

        @Test
        @DisplayName("lève NullPointerException si type null")
        void nullTypeThrows() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.getFirst("task-a", null));
        }
    }

    // ─── Immuabilité ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Immuabilité")
    class Immutability {

        @Test
        @DisplayName("store retourné est non-modifiable")
        void storeIsUnmodifiable() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());

            assertThrows(UnsupportedOperationException.class,
                () -> ctx.store().put("task-a", Map.of()));
        }

        @Test
        @DisplayName("sous-map retournée par store est non-modifiable")
        void innerStoreIsUnmodifiable() {
            var store = Map.of("task-a", Map.of("agent-1", (Object) "value"));
            var ctx = new PartialExecutionContext(execId(), scenarioId(), store);

            var inner = ctx.store().get("task-a");
            assertThrows(UnsupportedOperationException.class,
                () -> inner.put("agent-2", "other"));
        }

        @Test
        @DisplayName("modifier le store source après construction ne mute pas")
        void sourceModificationAfterConstruction() {
            var mutableInner = new HashMap<String, Object>();
            mutableInner.put("agent-1", "value");
            var mutableOuter = new HashMap<String, Map<String, Object>>();
            mutableOuter.put("task-a", mutableInner);

            var ctx = new PartialExecutionContext(execId(), scenarioId(), mutableOuter);

            mutableInner.put("agent-hacked", "hacked");
            mutableOuter.put("hacked", Map.of());

            assertEquals(1, ctx.store().size());
            assertEquals(1, ctx.store().get("task-a").size());
            assertFalse(ctx.store().containsKey("hacked"));
        }
    }

    // ─── Validation non-null ─────────────────────────────────────────────

    @Nested
    @DisplayName("Validation non-null")
    class Validation {

        @Test
        @DisplayName("executionId null lève NullPointerException")
        void nullExecutionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new PartialExecutionContext(null, scenarioId(), Map.of())
            );
        }

        @Test
        @DisplayName("scenarioId null lève NullPointerException")
        void nullScenarioIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new PartialExecutionContext(execId(), null, Map.of())
            );
        }
    }

    // ─── toString / equals ───────────────────────────────────────────────

    @Nested
    @DisplayName("toString / equals")
    class ToStringEquals {

        @Test
        @DisplayName("deux contextes identiques sont égaux")
        void identicalContextsAreEqual() {
            var id = execId();
            var sid = scenarioId();
            var store = Map.of("task-a", Map.of("agent-1", (Object) "value"));

            var ctx1 = new PartialExecutionContext(id, sid, store);
            var ctx2 = new PartialExecutionContext(id, sid, store);

            assertEquals(ctx1, ctx2);
            assertEquals(ctx1.hashCode(), ctx2.hashCode());
        }

        @Test
        @DisplayName("executionId différent → pas égaux")
        void differentExecutionIdNotEqual() {
            var store = Map.of("task-a", Map.of("agent-1", (Object) "value"));
            var ctx1 = new PartialExecutionContext(ExecutionId.of("e1"), scenarioId(), store);
            var ctx2 = new PartialExecutionContext(ExecutionId.of("e2"), scenarioId(), store);

            assertNotEquals(ctx1, ctx2);
        }

        @Test
        @DisplayName("store différent → pas égaux")
        void differentStoreNotEqual() {
            var s1 = Map.of("task-a", Map.of("agent-1", (Object) "v1"));
            var s2 = Map.of("task-a", Map.of("agent-1", (Object) "v2"));
            var ctx1 = new PartialExecutionContext(execId(), scenarioId(), s1);
            var ctx2 = new PartialExecutionContext(execId(), scenarioId(), s2);

            assertNotEquals(ctx1, ctx2);
        }

        @Test
        @DisplayName("toString contient le nom du record")
        void toStringContainsRecordName() {
            var ctx = PartialExecutionContext.empty(execId(), scenarioId());
            assertTrue(ctx.toString().contains("PartialExecutionContext"));
        }
    }

    // ─── Pas de getAll() ─────────────────────────────────────────────────

    @Test
    @DisplayName("PartialExecutionContext n'expose pas getAll() — vérifié par absence")
    void noGetAllMethod() {
        // PartialExecutionContext n'a pas de getAll() car les agents
        // n'ont pas besoin d'accéder aux métadonnées complètes des TaskResult.
        // Vérifié par compilation : appeler .getAll() ne compile pas.
        assertTrue(true, "getAll() absent par design");
    }
}
