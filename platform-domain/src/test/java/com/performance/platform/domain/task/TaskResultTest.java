package com.performance.platform.domain.task;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour TaskResult — factories, isSuccess(), immuabilité outputs, validation.
 */
@DisplayName("TaskResult")
class TaskResultTest {

    private static TaskId taskId() {
        return TaskId.of("task-1");
    }

    // ─── Factory success ──────────────────────────────────────────────

    @Nested
    @DisplayName("factory success()")
    class SuccessFactory {

        @Test
        @DisplayName("crée un TaskResult avec status SUCCESS")
        void createsSuccessResult() {
            var outputs = Map.<String, Object>of("rps", 1500L, "latency_p99", 45);
            var result = TaskResult.success(taskId(), "gatling-http", Duration.ofSeconds(30), outputs);

            assertEquals(taskId(), result.taskId());
            assertEquals("gatling-http", result.taskName());
            assertEquals(TaskStatus.SUCCESS, result.status());
            assertEquals(Duration.ofSeconds(30), result.duration());
            assertEquals(outputs, result.outputs());
            assertNull(result.errorMessage());
            assertNull(result.cause());
            assertNotNull(result.completedAt());
        }

        @Test
        @DisplayName("isSuccess() retourne true")
        void isSuccessReturnsTrue() {
            var result = TaskResult.success(taskId(), "gatling", Duration.ofSeconds(10), Map.of());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("outputs null devient Map vide")
        void nullOutputsBecomesEmptyMap() {
            var result = TaskResult.success(taskId(), "task", Duration.ZERO, null);
            assertEquals(Map.of(), result.outputs());
        }
    }

    // ─── Factory failed ───────────────────────────────────────────────

    @Nested
    @DisplayName("factory failed()")
    class FailedFactory {

        @Test
        @DisplayName("crée un TaskResult avec status FAILED")
        void createsFailedResult() {
            var cause = new RuntimeException("connection refused");
            var result = TaskResult.failed(taskId(), "database-purge", Duration.ofSeconds(5),
                "Database error: connection refused", cause);

            assertEquals(taskId(), result.taskId());
            assertEquals("database-purge", result.taskName());
            assertEquals(TaskStatus.FAILED, result.status());
            assertEquals(Duration.ofSeconds(5), result.duration());
            assertEquals(Map.of(), result.outputs());
            assertEquals("Database error: connection refused", result.errorMessage());
            assertSame(cause, result.cause());
            assertNotNull(result.completedAt());
        }

        @Test
        @DisplayName("isSuccess() retourne false")
        void isSuccessReturnsFalse() {
            var result = TaskResult.failed(taskId(), "task", Duration.ofSeconds(1),
                "error", new Exception("boom"));
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("message et cause peuvent être null")
        void nullMessageAndCause() {
            var result = TaskResult.failed(taskId(), "task", Duration.ofSeconds(1), null, null);

            assertNull(result.errorMessage());
            assertNull(result.cause());
            assertEquals(TaskStatus.FAILED, result.status());
        }
    }

    // ─── Factory skipped ──────────────────────────────────────────────

    @Nested
    @DisplayName("factory skipped()")
    class SkippedFactory {

        @Test
        @DisplayName("crée un TaskResult avec status SKIPPED et duration ZERO")
        void createsSkippedResult() {
            var result = TaskResult.skipped(taskId(), "optional-task", "dependency failed");

            assertEquals(taskId(), result.taskId());
            assertEquals("optional-task", result.taskName());
            assertEquals(TaskStatus.SKIPPED, result.status());
            assertEquals(Duration.ZERO, result.duration());
            assertEquals(Map.of(), result.outputs());
            assertEquals("dependency failed", result.errorMessage());
            assertNull(result.cause());
            assertNotNull(result.completedAt());
        }

        @Test
        @DisplayName("isSuccess() retourne false")
        void isSuccessReturnsFalse() {
            var result = TaskResult.skipped(taskId(), "task", "skipped because upstream failed");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("reason peut être null")
        void nullReason() {
            var result = TaskResult.skipped(taskId(), "task", null);
            assertNull(result.errorMessage());
            assertEquals(TaskStatus.SKIPPED, result.status());
        }
    }

    // ─── Immuabilité outputs ──────────────────────────────────────────

    @Nested
    @DisplayName("Immuabilité outputs")
    class Immutability {

        @Test
        @DisplayName("modifier la Map source après success() ne modifie pas le record")
        void successDefensiveCopy() {
            var mutableOutputs = new HashMap<String, Object>(Map.of("rps", 100));
            var result = TaskResult.success(taskId(), "task", Duration.ofSeconds(1), mutableOutputs);

            mutableOutputs.put("rps", 9999);

            assertEquals(Map.of("rps", 100), result.outputs(),
                "modifying source map must not affect record");
        }

        @Test
        @DisplayName("modifier la Map source via constructeur direct ne modifie pas le record")
        void constructorDefensiveCopy() {
            var mutableOutputs = new HashMap<String, Object>(Map.of("key", "val"));
            var result = new TaskResult(
                taskId(), "task", TaskStatus.SUCCESS, Duration.ofSeconds(1),
                mutableOutputs, null, null, java.time.Instant.now()
            );

            mutableOutputs.put("hacked", true);

            assertEquals(1, result.outputs().size());
            assertEquals("val", result.outputs().get("key"));
        }

        @Test
        @DisplayName("record.outputs() lève UnsupportedOperationException si tentative de modification")
        void outputsReturnedMapIsUnmodifiable() {
            var result = TaskResult.success(taskId(), "task", Duration.ofSeconds(1),
                Map.<String, Object>of("k", "v"));

            assertThrows(UnsupportedOperationException.class,
                () -> result.outputs().put("new", "val"));
        }
    }

    // ─── Validation non-null ──────────────────────────────────────────

    @Nested
    @DisplayName("Validation non-null")
    class Validation {

        @Test
        @DisplayName("taskId null lève NullPointerException")
        void nullTaskIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskResult(null, "task", TaskStatus.SUCCESS, Duration.ZERO,
                    Map.of(), null, null, java.time.Instant.now())
            );
        }

        @Test
        @DisplayName("taskName null lève NullPointerException")
        void nullTaskNameThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskResult(taskId(), null, TaskStatus.SUCCESS, Duration.ZERO,
                    Map.of(), null, null, java.time.Instant.now())
            );
        }

        @Test
        @DisplayName("status null lève NullPointerException")
        void nullStatusThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskResult(taskId(), "task", null, Duration.ZERO,
                    Map.of(), null, null, java.time.Instant.now())
            );
        }

        @Test
        @DisplayName("duration null lève NullPointerException")
        void nullDurationThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskResult(taskId(), "task", TaskStatus.SUCCESS, null,
                    Map.of(), null, null, java.time.Instant.now())
            );
        }

        @Test
        @DisplayName("completedAt null lève NullPointerException")
        void nullCompletedAtThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskResult(taskId(), "task", TaskStatus.SUCCESS, Duration.ZERO,
                    Map.of(), null, null, null)
            );
        }
    }

    // ─── toString / equals ────────────────────────────────────────────

    @Nested
    @DisplayName("toString / equals")
    class ToStringEquals {

        @Test
        @DisplayName("deux success identiques sont égaux")
        void identicalSuccessAreEqual() {
            var now = java.time.Instant.now();
            var r1 = new TaskResult(taskId(), "task", TaskStatus.SUCCESS,
                Duration.ofSeconds(1), Map.of(), null, null, now);
            var r2 = new TaskResult(taskId(), "task", TaskStatus.SUCCESS,
                Duration.ofSeconds(1), Map.of(), null, null, now);

            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("status différent → pas égaux")
        void differentStatusNotEqual() {
            var now = java.time.Instant.now();
            var r1 = new TaskResult(taskId(), "task", TaskStatus.SUCCESS,
                Duration.ZERO, Map.of(), null, null, now);
            var r2 = new TaskResult(taskId(), "task", TaskStatus.FAILED,
                Duration.ZERO, Map.of(), "err", null, now);

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("taskId différent → pas égaux")
        void differentTaskIdNotEqual() {
            var now = java.time.Instant.now();
            var r1 = new TaskResult(TaskId.of("id1"), "task", TaskStatus.SUCCESS,
                Duration.ZERO, Map.of(), null, null, now);
            var r2 = new TaskResult(TaskId.of("id2"), "task", TaskStatus.SUCCESS,
                Duration.ZERO, Map.of(), null, null, now);

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("toString contient le nom du record et le taskName")
        void toStringContainsRecordAndTaskName() {
            var result = TaskResult.success(taskId(), "my-executor", Duration.ofSeconds(5), Map.of());
            assertTrue(result.toString().contains("TaskResult"));
            assertTrue(result.toString().contains("my-executor"));
        }
    }

    // ─── Pas de TaskType ──────────────────────────────────────────────

    @Test
    @DisplayName("aucune référence à TaskType — taskName est String")
    void noTaskTypeReference() {
        var result = TaskResult.success(taskId(), "any-custom-task", Duration.ZERO, Map.of());
        assertTrue(result.taskName() instanceof String);
        assertEquals("any-custom-task", result.taskName());
    }
}
