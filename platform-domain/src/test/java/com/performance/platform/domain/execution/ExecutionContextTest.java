package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour ExecutionContext — immuabilité, with() copy-on-write, get/getFirst/getAll.
 */
@DisplayName("ExecutionContext")
class ExecutionContextTest {

    private static ExecutionId execId() {
        return ExecutionId.generate();
    }

    private static ScenarioId scenarioId() {
        return ScenarioId.of("scenario-1");
    }

    private static TaskResult successResult(String taskName, String taskId, Map<String, Object> outputs) {
        return TaskResult.success(TaskId.of(taskId), taskName, Duration.ofSeconds(1), outputs);
    }

    // ─── Construction ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("factory initial() crée un contexte avec store vide")
        void initialCreatesEmptyContext() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());

            assertNotNull(ctx.executionId());
            assertNotNull(ctx.scenarioId());
            assertTrue(ctx.store().isEmpty());
        }

        @Test
        @DisplayName("store null → Map.of() vide")
        void nullStoreBecomesEmptyMap() {
            var ctx = new ExecutionContext(execId(), scenarioId(), null);

            assertEquals(Map.of(), ctx.store());
        }

        @Test
        @DisplayName("store non-null → copie défensive profonde")
        void storeDeepCopyOnConstruction() {
            var innerMutable = new HashMap<String, TaskResult>();
            var result = successResult("task-a", "t1", Map.of("k", "v"));
            innerMutable.put("agent-1", result);
            var outerMutable = new HashMap<String, Map<String, TaskResult>>();
            outerMutable.put("task-a", innerMutable);

            var ctx = new ExecutionContext(execId(), scenarioId(), outerMutable);

            // Modifier les sources ne doit pas affecter le record
            innerMutable.put("agent-hacked", result);
            outerMutable.put("hacked", Map.of());

            assertEquals(1, ctx.store().size());
            assertEquals(1, ctx.store().get("task-a").size());
            assertTrue(ctx.store().get("task-a").containsKey("agent-1"));
            assertFalse(ctx.store().get("task-a").containsKey("agent-hacked"));
            assertFalse(ctx.store().containsKey("hacked"));
        }
    }

    // ─── with() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("with()")
    class WithMethod {

        @Test
        @DisplayName("retourne une nouvelle instance différente de l'originale")
        void returnsNewInstance() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var result = successResult("task-a", "t1", Map.of());

            var updated = ctx.with("task-a", "agent-1", result);

            assertNotSame(ctx, updated);
            assertTrue(ctx.store().isEmpty(), "original unchanged");
            assertEquals(1, updated.store().size());
        }

        @Test
        @DisplayName("peut ajouter plusieurs résultats pour une même task")
        void multipleAgentsForSameTask() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var r1 = successResult("task-a", "t1", Map.of("rps", 100));
            var r2 = successResult("task-a", "t1", Map.of("rps", 200));

            var ctx2 = ctx.with("task-a", "agent-1", r1);
            var ctx3 = ctx2.with("task-a", "agent-2", r2);

            assertEquals(1, ctx3.store().size());
            assertEquals(2, ctx3.store().get("task-a").size());
            assertTrue(ctx3.store().get("task-a").containsKey("agent-1"));
            assertTrue(ctx3.store().get("task-a").containsKey("agent-2"));
        }

        @Test
        @DisplayName("peut ajouter des résultats pour des tasks différentes")
        void differentTasks() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var r1 = successResult("purge-db", "t1", Map.of("rows", 50));
            var r2 = successResult("inject", "t2", Map.of("rps", 1000));

            var updated = ctx.with("purge-db", "agent-db", r1)
                             .with("inject", "agent-perf", r2);

            assertEquals(2, updated.store().size());
            assertTrue(updated.store().containsKey("purge-db"));
            assertTrue(updated.store().containsKey("inject"));
        }

        @Test
        @DisplayName("remplace un résultat existant pour le même taskId+agentId")
        void replacesExistingResult() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var r1 = successResult("task-a", "t1", Map.of("version", 1));
            var r2 = successResult("task-a", "t1", Map.of("version", 2));

            var updated = ctx.with("task-a", "agent-1", r1)
                             .with("task-a", "agent-1", r2);

            assertEquals(1, updated.store().get("task-a").size());
            var stored = updated.store().get("task-a").get("agent-1");
            assertEquals(Map.of("version", 2), stored.outputs());
        }

        @Test
        @DisplayName("lève NullPointerException si taskId null")
        void nullTaskIdThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var result = successResult("task-a", "t1", Map.of());

            assertThrows(NullPointerException.class,
                () -> ctx.with(null, "agent-1", result));
        }

        @Test
        @DisplayName("lève NullPointerException si agentId null")
        void nullAgentIdThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var result = successResult("task-a", "t1", Map.of());

            assertThrows(NullPointerException.class,
                () -> ctx.with("task-a", null, result));
        }

        @Test
        @DisplayName("lève NullPointerException si result null")
        void nullResultThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());

            assertThrows(NullPointerException.class,
                () -> ctx.with("task-a", "agent-1", null));
        }
    }

    // ─── get() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get(taskId, agentId, type)")
    class GetMethod {

        @Test
        @DisplayName("retourne la valeur castée au type demandé")
        void returnsCastedValue() {
            var result = successResult("task-a", "t1", Map.of("rps", 100L, "latency", 45));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", result);

            assertEquals(100L, ctx.get("task-a", "agent-1", Long.class).orElseThrow());
            assertEquals(45, ctx.get("task-a", "agent-1", Integer.class).orElseThrow());
        }

        @Test
        @DisplayName("retourne Optional.empty() si taskId absent")
        void emptyIfTaskIdMissing() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());

            assertTrue(ctx.get("absent", "agent-1", Object.class).isEmpty());
        }

        @Test
        @DisplayName("retourne Optional.empty() si agentId absent")
        void emptyIfAgentIdMissing() {
            var result = successResult("task-a", "t1", Map.of("x", 1));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", result);

            assertTrue(ctx.get("task-a", "agent-absent", Object.class).isEmpty());
        }

        @Test
        @DisplayName("retourne Optional.empty() si aucune valeur castable au type")
        void emptyIfNoMatchingType() {
            var result = successResult("task-a", "t1", Map.of("rps", 100));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", result);

            assertTrue(ctx.get("task-a", "agent-1", Double.class).isEmpty());
        }

        @Test
        @DisplayName("lève NullPointerException si taskId null")
        void nullTaskIdThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.get(null, "agent-1", Object.class));
        }

        @Test
        @DisplayName("lève NullPointerException si agentId null")
        void nullAgentIdThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.get("task-a", null, Object.class));
        }

        @Test
        @DisplayName("lève NullPointerException si type null")
        void nullTypeThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
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
            var r1 = successResult("task-a", "t1", Map.of("rps", 100L));
            var r2 = successResult("task-a", "t1", Map.of("rps", 200L));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", r1)
                .with("task-a", "agent-2", r2);

            var value = ctx.getFirst("task-a", Long.class);
            assertTrue(value.isPresent());
            assertTrue(value.get() instanceof Long);
        }

        @Test
        @DisplayName("retourne Optional.empty() si taskId absent")
        void emptyIfTaskIdMissing() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());

            assertTrue(ctx.getFirst("absent", Object.class).isEmpty());
        }

        @Test
        @DisplayName("retourne Optional.empty() si aucune valeur castable")
        void emptyIfNoMatchingType() {
            var result = successResult("task-a", "t1", Map.of("rps", 100));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", result);

            assertTrue(ctx.getFirst("task-a", Double.class).isEmpty());
        }

        @Test
        @DisplayName("lève NullPointerException si taskId null")
        void nullTaskIdThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.getFirst(null, Object.class));
        }

        @Test
        @DisplayName("lève NullPointerException si type null")
        void nullTypeThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.getFirst("task-a", null));
        }
    }

    // ─── getAll() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll(taskId)")
    class GetAllMethod {

        @Test
        @DisplayName("retourne tous les TaskResult par agentId")
        void returnsAllAgentResults() {
            var r1 = successResult("task-a", "t1", Map.of("rps", 100));
            var r2 = successResult("task-a", "t1", Map.of("rps", 200));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", r1)
                .with("task-a", "agent-2", r2);

            var all = ctx.getAll("task-a");

            assertEquals(2, all.size());
            assertEquals(r1, all.get("agent-1"));
            assertEquals(r2, all.get("agent-2"));
        }

        @Test
        @DisplayName("retourne Map vide non-null si taskId absent")
        void emptyMapIfTaskIdMissing() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());

            var all = ctx.getAll("absent");

            assertNotNull(all);
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("lève NullPointerException si taskId null")
        void nullTaskIdThrows() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            assertThrows(NullPointerException.class,
                () -> ctx.getAll(null));
        }
    }

    // ─── Immuabilité ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Immuabilité")
    class Immutability {

        @Test
        @DisplayName("store retourné est non-modifiable")
        void storeIsUnmodifiable() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());

            assertThrows(UnsupportedOperationException.class,
                () -> ctx.store().put("task-a", Map.of()));
        }

        @Test
        @DisplayName("sous-map retournée par store est non-modifiable")
        void innerStoreIsUnmodifiable() {
            var result = successResult("task-a", "t1", Map.of("k", "v"));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", result);

            var inner = ctx.store().get("task-a");
            assertThrows(UnsupportedOperationException.class,
                () -> inner.put("agent-2", result));
        }

        @Test
        @DisplayName("getAll() retourne une map non-modifiable")
        void getAllReturnsUnmodifiable() {
            var result = successResult("task-a", "t1", Map.of("k", "v"));
            var ctx = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", result);

            var all = ctx.getAll("task-a");
            assertThrows(UnsupportedOperationException.class,
                () -> all.put("agent-2", result));
        }

        @Test
        @DisplayName("with() ne mute pas l'original après modifications sur le retour")
        void withDoesNotMutateOriginal() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            var result = successResult("task-a", "t1", Map.of("k", "v"));

            var updated = ctx.with("task-a", "agent-1", result);

            assertTrue(ctx.store().isEmpty(), "original must remain empty");
            assertFalse(updated.store().isEmpty());
        }

        @Test
        @DisplayName("modifier le store source après construction ne mute pas")
        void sourceModificationAfterConstruction() {
            var mutableOuter = new HashMap<String, Map<String, TaskResult>>();
            var mutableInner = new HashMap<String, TaskResult>();
            mutableInner.put("agent-1", successResult("task-a", "t1", Map.of("k", "v")));
            mutableOuter.put("task-a", mutableInner);

            var ctx = new ExecutionContext(execId(), scenarioId(), mutableOuter);

            mutableInner.put("agent-hacked", successResult("task-a", "t1", Map.of()));
            mutableOuter.put("hacked", Map.of());

            assertEquals(1, ctx.store().size());
            assertEquals(1, ctx.store().get("task-a").size());
            assertTrue(ctx.store().get("task-a").containsKey("agent-1"));
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
                new ExecutionContext(null, scenarioId(), Map.of())
            );
        }

        @Test
        @DisplayName("scenarioId null lève NullPointerException")
        void nullScenarioIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new ExecutionContext(execId(), null, Map.of())
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
            var result = successResult("task-a", "t1", Map.of("k", "v"));

            var ctx1 = ExecutionContext.initial(id, sid)
                .with("task-a", "agent-1", result);
            var ctx2 = ExecutionContext.initial(id, sid)
                .with("task-a", "agent-1", result);

            assertEquals(ctx1, ctx2);
            assertEquals(ctx1.hashCode(), ctx2.hashCode());
        }

        @Test
        @DisplayName("executionId différent → pas égaux")
        void differentExecutionIdNotEqual() {
            var result = successResult("task-a", "t1", Map.of());
            var ctx1 = ExecutionContext.initial(ExecutionId.of("e1"), scenarioId())
                .with("task-a", "agent-1", result);
            var ctx2 = ExecutionContext.initial(ExecutionId.of("e2"), scenarioId())
                .with("task-a", "agent-1", result);

            assertNotEquals(ctx1, ctx2);
        }

        @Test
        @DisplayName("store différent → pas égaux")
        void differentStoreNotEqual() {
            var r1 = successResult("task-a", "t1", Map.of("k", "v1"));
            var r2 = successResult("task-a", "t1", Map.of("k", "v2"));
            var ctx1 = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", r1);
            var ctx2 = ExecutionContext.initial(execId(), scenarioId())
                .with("task-a", "agent-1", r2);

            assertNotEquals(ctx1, ctx2);
        }

        @Test
        @DisplayName("toString contient le nom du record")
        void toStringContainsRecordName() {
            var ctx = ExecutionContext.initial(execId(), scenarioId());
            assertTrue(ctx.toString().contains("ExecutionContext"));
        }
    }
}
