package com.performance.platform.infrastructure.persistence.mapper;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.infrastructure.persistence.TaskResultEntity;
import com.performance.platform.infrastructure.persistence.TaskResultId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("TaskResultMapper")
class TaskResultMapperTest {

    private TaskResultMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TaskResultMapper();
    }

    // -----------------------------------------------------------------------
    // Round-trip
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("should round-trip domain -> entity -> domain preserving all fields")
    void shouldRoundTripDomainToEntityToDomain() {
        var execId = ExecutionId.of("exec-001");
        var taskId = TaskId.of("task-db");
        var agentId = AgentId.of("agent-001");

        var original = new TaskResult(
                taskId,
                "database-seed",
                TaskStatus.SUCCESS,
                Duration.ofSeconds(3),
                Map.of("rowsInserted", 5000, "tablesCreated", 3),
                null,
                null,
                Instant.parse("2026-06-19T10:02:00Z")
        );

        TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, original);
        TaskResult restored = mapper.toDomain(entity);

        assertThat(restored.taskId()).isEqualTo(taskId);
        assertThat(restored.taskName()).isEqualTo("database-seed");
        assertThat(restored.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(restored.duration()).isEqualTo(Duration.ofSeconds(3));
        assertThat(restored.outputs()).containsEntry("rowsInserted", 5000);
        assertThat(restored.outputs()).containsEntry("tablesCreated", 3);
        assertThat(restored.outputs()).doesNotContainKey(TaskResultMapper.META_KEY);
        assertThat(restored.errorMessage()).isNull();
        assertThat(restored.cause()).isNull();
        assertThat(restored.completedAt()).isEqualTo(Instant.parse("2026-06-19T10:02:00Z"));
    }

    @Test
    @DisplayName("should round-trip failed task result preserving error info")
    void shouldRoundTripFailedTaskResult() {
        var execId = ExecutionId.of("exec-002");
        var taskId = TaskId.of("task-fail");
        var agentId = AgentId.of("agent-002");

        var original = TaskResult.failed(
                taskId, "http-request", Duration.ofMillis(500),
                "Connection timeout", new RuntimeException("Connection timeout"));

        TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, original);
        TaskResult restored = mapper.toDomain(entity);

        assertThat(restored.taskName()).isEqualTo("http-request");
        assertThat(restored.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(restored.duration()).isEqualTo(Duration.ofMillis(500));
        assertThat(restored.errorMessage()).isEqualTo("Connection timeout");
        assertThat(restored.cause()).isNotNull();
        assertThat(restored.cause().getMessage()).isEqualTo("Connection timeout");
        assertThat(restored.outputs()).doesNotContainKey(TaskResultMapper.META_KEY);
    }

    @Test
    @DisplayName("should round-trip task result with empty outputs")
    void shouldRoundTripWithEmptyOutputs() {
        var execId = ExecutionId.generate();
        var taskId = TaskId.of("task-empty");
        var agentId = AgentId.generate();

        var original = TaskResult.success(
                taskId, "noop", Duration.ZERO, Map.of());

        TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, original);
        TaskResult restored = mapper.toDomain(entity);

        assertThat(restored.taskName()).isEqualTo("noop");
        assertThat(restored.outputs()).isEmpty();
        assertThat(restored.outputs()).doesNotContainKey(TaskResultMapper.META_KEY);
    }

    @Test
    @DisplayName("should preserve round-trip with non-string output values")
    void shouldPreserveNonStringOutputValues() {
        var execId = ExecutionId.generate();
        var taskId = TaskId.of("task-mixed");
        var agentId = AgentId.generate();

        Map<String, Object> rawOutputs = Map.of(
                "count", 42,
                "ratio", 0.95,
                "success", true,
                "tags", Map.of("env", "staging", "region", "eu-west-1")
        );

        var original = new TaskResult(
                taskId, "mixed-outputs", TaskStatus.SUCCESS,
                Duration.ofMillis(100), rawOutputs, null, null, Instant.now());

        TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, original);
        TaskResult restored = mapper.toDomain(entity);

        assertThat(restored.outputs()).containsEntry("count", 42);
        assertThat(restored.outputs()).containsEntry("ratio", 0.95);
        assertThat(restored.outputs()).containsEntry("success", true);
        assertThat(restored.outputs()).containsKey("tags");
    }

    @Test
    @DisplayName("should handle null completedAt in entity")
    void shouldRoundTripWithNullCompletedAt() {
        var execId = ExecutionId.generate();
        var taskId = TaskId.of("task-null");
        var agentId = AgentId.generate();

        // Create entity directly with null completedAt
        var id = new TaskResultId(
                execId.value(), taskId.value(), agentId.value());

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("taskName", "in-progress");
        meta.put("duration", "PT5S");
        meta.put("errorMessage", null);
        meta.put("causeMessage", null);

        Map<String, Object> outputs = new java.util.HashMap<>();
        outputs.put(TaskResultMapper.META_KEY, meta);
        outputs.put("progress", "50%");

        var entity = new TaskResultEntity(
                id, TaskStatus.FAILED, outputs, null);

        TaskResult restored = mapper.toDomain(entity);

        assertThat(restored.taskId()).isEqualTo(taskId);
        assertThat(restored.taskName()).isEqualTo("in-progress");
        assertThat(restored.completedAt()).isEqualTo(Instant.EPOCH);
    }

    // -----------------------------------------------------------------------
    // toEntity
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("should build composite key from three IDs")
        void shouldBuildCompositeKey() {
            var execId = ExecutionId.of("exec-key");
            var taskId = TaskId.of("task-key");
            var agentId = AgentId.of("agent-key");

            var result = TaskResult.success(
                    taskId, "test", Duration.ZERO, Map.of());

            TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, result);

            assertThat(entity.id()).isNotNull();
            assertThat(entity.executionId()).isEqualTo("exec-key");
            assertThat(entity.taskId()).isEqualTo("task-key");
            assertThat(entity.agentId()).isEqualTo("agent-key");
        }

        @Test
        @DisplayName("should embed metadata in outputs")
        void shouldEmbedMetadataInOutputs() {
            var execId = ExecutionId.generate();
            var taskId = TaskId.of("task-meta");
            var agentId = AgentId.generate();

            var result = new TaskResult(
                    taskId, "meta-task", TaskStatus.SUCCESS,
                    Duration.ofMillis(350), Map.of("key", "value"),
                    null, null, Instant.now());

            TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, result);

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) entity.outputs()
                    .get(TaskResultMapper.META_KEY);
            assertThat(meta).isNotNull();
            assertThat(meta).containsEntry("taskName", "meta-task");
            assertThat(meta).containsEntry("duration", "PT0.35S");
            assertThat(meta).containsEntry("errorMessage", null);
        }

        @Test
        @DisplayName("should embed error metadata in outputs")
        void shouldEmbedErrorMetadata() {
            var execId = ExecutionId.generate();
            var taskId = TaskId.of("task-err");
            var agentId = AgentId.generate();

            var result = TaskResult.failed(
                    taskId, "failing-task", Duration.ofSeconds(1),
                    "something broke", new RuntimeException("something broke"));

            TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, result);

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) entity.outputs()
                    .get(TaskResultMapper.META_KEY);
            assertThat(meta).containsEntry("errorMessage", "something broke");
            assertThat(meta).containsEntry("causeMessage", "something broke");
        }

        @Test
        @DisplayName("should reject null executionId")
        void shouldRejectNullExecutionId() {
            assertThatNullPointerException().isThrownBy(() ->
                    mapper.toEntity(null, TaskId.of("t"), AgentId.of("a"),
                            TaskResult.success(TaskId.of("t"), "x", Duration.ZERO, Map.of())));
        }

        @Test
        @DisplayName("should reject null taskId")
        void shouldRejectNullTaskId() {
            assertThatNullPointerException().isThrownBy(() ->
                    mapper.toEntity(ExecutionId.generate(), null, AgentId.of("a"),
                            TaskResult.success(TaskId.of("t"), "x", Duration.ZERO, Map.of())));
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            assertThatNullPointerException().isThrownBy(() ->
                    mapper.toEntity(ExecutionId.generate(), TaskId.of("t"), null,
                            TaskResult.success(TaskId.of("t"), "x", Duration.ZERO, Map.of())));
        }

        @Test
        @DisplayName("should reject null result")
        void shouldRejectNullResult() {
            assertThatNullPointerException().isThrownBy(() ->
                    mapper.toEntity(ExecutionId.generate(), TaskId.of("t"), AgentId.of("a"), null));
        }
    }

    // -----------------------------------------------------------------------
    // toDomain
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        @DisplayName("should reconstruct taskId from entity composite key")
        void shouldReconstructTaskIdFromCompositeKey() {
            var id = new TaskResultId("exec-xyz", "task-xyz", "agent-xyz");

            Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("taskName", "reconstructed");
            meta.put("duration", "PT0.1S");
            meta.put("errorMessage", null);
            meta.put("causeMessage", null);

            Map<String, Object> outputs = new java.util.HashMap<>();
            outputs.put(TaskResultMapper.META_KEY, meta);

            var entity = new TaskResultEntity(
                    id, TaskStatus.SUCCESS, outputs,
                    Instant.parse("2026-06-19T10:00:00Z"));

            TaskResult result = mapper.toDomain(entity);

            assertThat(result.taskId()).isEqualTo(TaskId.of("task-xyz"));
            assertThat(result.taskName()).isEqualTo("reconstructed");
        }

        @Test
        @DisplayName("should strip metadata from outputs")
        void shouldStripMetadataFromOutputs() {
            var execId = ExecutionId.generate();
            var taskId = TaskId.of("task-strip");
            var agentId = AgentId.generate();

            var original = new TaskResult(
                    taskId, "strip-task", TaskStatus.SUCCESS,
                    Duration.ofMillis(100),
                    Map.of("data", "payload"),
                    null, null, Instant.now());

            TaskResultEntity entity = mapper.toEntity(execId, taskId, agentId, original);
            TaskResult restored = mapper.toDomain(entity);

            assertThat(restored.outputs()).containsEntry("data", "payload");
            assertThat(restored.outputs()).doesNotContainKey(TaskResultMapper.META_KEY);
        }

        @Test
        @DisplayName("should handle entity with no metadata key gracefully")
        void shouldHandleNoMetadataKey() {
            var id = new TaskResultId("exec-no-meta", "task-no-meta", "agent-no");
            Map<String, Object> outputs = Map.of("payload", "no-meta-here");

            var entity = new TaskResultEntity(
                    id, TaskStatus.SUCCESS, outputs,
                    Instant.parse("2026-06-19T10:00:00Z"));

            TaskResult result = mapper.toDomain(entity);

            // Should default to "unknown" taskName and Duration.ZERO
            assertThat(result.taskName()).isEqualTo("unknown");
            assertThat(result.duration()).isEqualTo(Duration.ZERO);
            assertThat(result.errorMessage()).isNull();
            assertThat(result.outputs()).containsEntry("payload", "no-meta-here");
        }

        @Test
        @DisplayName("should reject null entity")
        void shouldRejectNullEntity() {
            assertThatNullPointerException().isThrownBy(() -> mapper.toDomain(null));
        }
    }

    // -----------------------------------------------------------------------
    // buildOutputsWithMetadata / extractMetadata / stripMetadata
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("helper methods")
    class HelperMethods {

        @Test
        @DisplayName("buildOutputsWithMetadata should preserve user outputs and add meta")
        void shouldBuildOutputsWithMetadata() {
            var result = new TaskResult(
                    TaskId.of("t"), "helper-task", TaskStatus.SUCCESS,
                    Duration.ofSeconds(10), Map.of("x", 1, "y", 2),
                    null, null, Instant.now());

            Map<String, Object> outputs = mapper.buildOutputsWithMetadata(result);

            assertThat(outputs).containsEntry("x", 1);
            assertThat(outputs).containsEntry("y", 2);
            assertThat(outputs).containsKey(TaskResultMapper.META_KEY);

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) outputs.get(TaskResultMapper.META_KEY);
            assertThat(meta).containsEntry("taskName", "helper-task");
            assertThat(meta).containsEntry("duration", "PT10S");
        }

        @Test
        @DisplayName("extractMetadata should extract from meta entry")
        void shouldExtractMetadata() {
            var taskId = TaskId.of("task-extract");

            var original = new TaskResult(
                    taskId, "extract-me", TaskStatus.SUCCESS,
                    Duration.ofMillis(750), Map.of(), "some error",
                    new RuntimeException("some error"), Instant.now());

            Map<String, Object> built = mapper.buildOutputsWithMetadata(original);
            TaskResultMapper.Metadata meta = mapper.extractMetadata(built);

            assertThat(meta.taskName()).isEqualTo("extract-me");
            assertThat(meta.duration()).isEqualTo(Duration.ofMillis(750));
            assertThat(meta.errorMessage()).isEqualTo("some error");
        }

        @Test
        @DisplayName("extractMetadata should return MISSING default when meta key absent")
        void shouldReturnMissingWhenMetaKeyAbsent() {
            Map<String, Object> outputs = Map.of("just", "data");

            TaskResultMapper.Metadata meta = mapper.extractMetadata(outputs);

            assertThat(meta.taskName()).isEqualTo("unknown");
            assertThat(meta.duration()).isEqualTo(Duration.ZERO);
            assertThat(meta.errorMessage()).isNull();
        }

        @Test
        @DisplayName("stripMetadata should remove _meta_ key")
        void shouldStripMetadata() {
            var taskId = TaskId.of("task-strip2");

            var original = TaskResult.success(
                    taskId, "strip-me", Duration.ofMillis(10), Map.of("a", "b"));

            Map<String, Object> built = mapper.buildOutputsWithMetadata(original);
            Map<String, Object> stripped = mapper.stripMetadata(built);

            assertThat(stripped).containsEntry("a", "b");
            assertThat(stripped).doesNotContainKey(TaskResultMapper.META_KEY);
            assertThat(stripped).hasSize(1); // only user data, no meta
        }
    }
}
