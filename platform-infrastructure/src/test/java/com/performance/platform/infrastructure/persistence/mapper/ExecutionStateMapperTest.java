package com.performance.platform.infrastructure.persistence.mapper;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.infrastructure.persistence.ExecutionStateEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionStateMapper")
class ExecutionStateMapperTest {

    private ExecutionStateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ExecutionStateMapper();
    }

    // -----------------------------------------------------------------------
    // Round-trip
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("should round-trip domain -> entity -> domain preserving all fields")
    void shouldRoundTripDomainToEntityToDomain() {
        ExecutionId execId = ExecutionId.generate();
        ScenarioId scId = ScenarioId.of("sc-roundtrip");
        TaskId taskId = TaskId.of("task-1");

        TaskResult result = TaskResult.success(
                taskId, "db-prep", Duration.ofMillis(450), Map.of("rows", 100));

        ExecutionContext ctx = ExecutionContext.initial(execId, scId)
                .with(taskId.value(), "agent-1", result);

        ExecutionState original = new ExecutionState(
                execId,
                scId,
                ExecutionStatus.RUNNING,
                Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                       Phase.INJECTION, PhaseStatus.RUNNING),
                ctx,
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:05:00Z")
        );

        ExecutionStateEntity entity = mapper.toEntity(original);
        ExecutionState restored = mapper.toDomain(entity);

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.scenarioId()).isEqualTo(original.scenarioId());
        assertThat(restored.status()).isEqualTo(original.status());
        assertThat(restored.phaseStatuses()).isEqualTo(original.phaseStatuses());
        assertThat(restored.startedAt()).isEqualTo(original.startedAt());
        assertThat(restored.updatedAt()).isEqualTo(original.updatedAt());

        // Context round-trip
        ExecutionContext restoredCtx = restored.context();
        assertThat(restoredCtx.executionId()).isEqualTo(ctx.executionId());
        assertThat(restoredCtx.scenarioId()).isEqualTo(ctx.scenarioId());
        assertThat(restoredCtx.getAll(taskId.value())).hasSize(1);
        assertThat(restoredCtx.getAll(taskId.value())).containsKey("agent-1");

        TaskResult restoredResult = restoredCtx.getAll(taskId.value()).get("agent-1");
        assertThat(restoredResult.taskId()).isEqualTo(taskId);
        assertThat(restoredResult.taskName()).isEqualTo("db-prep");
        assertThat(restoredResult.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(restoredResult.duration()).isEqualTo(Duration.ofMillis(450));
        assertThat(restoredResult.outputs()).containsEntry("rows", 100);
    }

    // -----------------------------------------------------------------------
    // toEntity
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("should convert domain ExecutionState to entity")
        void shouldConvertToEntity() {
            ExecutionId execId = ExecutionId.of("exec-001");
            ScenarioId scId = ScenarioId.of("sc-001");

            ExecutionState state = new ExecutionState(
                    execId,
                    scId,
                    ExecutionStatus.STARTED,
                    Map.of(),
                    ExecutionContext.initial(execId, scId),
                    Instant.parse("2026-06-19T09:00:00Z"),
                    Instant.parse("2026-06-19T09:00:00Z")
            );

            ExecutionStateEntity entity = mapper.toEntity(state);

            assertThat(entity.id()).isEqualTo("exec-001");
            assertThat(entity.scenarioId()).isEqualTo("sc-001");
            assertThat(entity.status()).isEqualTo(ExecutionStatus.STARTED);
            assertThat(entity.phases()).isEmpty();
            assertThat(entity.context()).containsEntry("executionId", "exec-001");
            assertThat(entity.context()).containsEntry("scenarioId", "sc-001");
            assertThat(entity.context()).containsKey("store");
            assertThat(entity.startedAt()).isEqualTo(Instant.parse("2026-06-19T09:00:00Z"));
        }

        @Test
        @DisplayName("should convert phase statuses to string-keyed map")
        void shouldConvertPhaseStatusesToStringMap() {
            ExecutionId execId = ExecutionId.generate();
            ScenarioId scId = ScenarioId.of("sc-phases");

            ExecutionState state = new ExecutionState(
                    execId, scId, ExecutionStatus.COMPLETED,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                           Phase.INJECTION, PhaseStatus.COMPLETED,
                           Phase.ASSERTION, PhaseStatus.RUNNING),
                    ExecutionContext.initial(execId, scId),
                    Instant.now(), Instant.now()
            );

            ExecutionStateEntity entity = mapper.toEntity(state);

            assertThat(entity.phases())
                    .containsEntry("PREPARATION", "COMPLETED")
                    .containsEntry("INJECTION", "COMPLETED")
                    .containsEntry("ASSERTION", "RUNNING")
                    .hasSize(3);
        }
    }

    // -----------------------------------------------------------------------
    // toDomain
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        @DisplayName("should convert entity to domain ExecutionState")
        void shouldConvertToDomain() {
            ExecutionStateEntity entity = new ExecutionStateEntity(
                    "exec-002", "sc-002", ExecutionStatus.COMPLETED,
                    Map.of("PREPARATION", "COMPLETED", "ASSERTION", "COMPLETED"),
                    Map.of("executionId", "exec-002",
                           "scenarioId", "sc-002",
                           "store", Map.of()),
                    Instant.parse("2026-06-19T08:00:00Z"),
                    Instant.parse("2026-06-19T08:10:00Z")
            );

            ExecutionState state = mapper.toDomain(entity);

            assertThat(state.id()).isEqualTo(ExecutionId.of("exec-002"));
            assertThat(state.scenarioId()).isEqualTo(ScenarioId.of("sc-002"));
            assertThat(state.status()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(state.phaseStatuses())
                    .containsEntry(Phase.PREPARATION, PhaseStatus.COMPLETED)
                    .containsEntry(Phase.ASSERTION, PhaseStatus.COMPLETED)
                    .hasSize(2);
            assertThat(state.context().store()).isEmpty();
            assertThat(state.startedAt()).isEqualTo(Instant.parse("2026-06-19T08:00:00Z"));
            assertThat(state.updatedAt()).isEqualTo(Instant.parse("2026-06-19T08:10:00Z"));
        }

        @Test
        @DisplayName("should handle empty phase statuses gracefully")
        void shouldHandleEmptyPhaseStatuses() {
            ExecutionStateEntity entity = new ExecutionStateEntity(
                    "exec-003", "sc-003", ExecutionStatus.STARTED,
                    Map.of(),
                    Map.of("executionId", "exec-003",
                           "scenarioId", "sc-003",
                           "store", Map.of()),
                    Instant.now(), Instant.now()
            );

            ExecutionState state = mapper.toDomain(entity);

            assertThat(state.phaseStatuses()).isEmpty();
        }

        @Test
        @DisplayName("should handle unknown phase names gracefully via valueOf")
        void shouldThrowOnUnknownPhase() {
            ExecutionStateEntity entity = new ExecutionStateEntity(
                    "exec-004", "sc-004", ExecutionStatus.STARTED,
                    Map.of("UNKNOWN_PHASE", "COMPLETED"),
                    Map.of("executionId", "exec-004",
                           "scenarioId", "sc-004",
                           "store", Map.of()),
                    Instant.now(), Instant.now()
            );

            try {
                mapper.toDomain(entity);
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("UNKNOWN_PHASE");
            }
        }
    }

    // -----------------------------------------------------------------------
    // contextToMap / mapToContext
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("context serialization")
    class ContextSerialization {

        @Test
        @DisplayName("should round-trip ExecutionContext with multiple tasks and agents")
        void shouldRoundTripContextWithMultipleTasks() {
            ExecutionId execId = ExecutionId.of("exec-ctx");
            ScenarioId scId = ScenarioId.of("sc-ctx");

            TaskResult r1 = TaskResult.success(
                    TaskId.of("t1"), "prep-db", Duration.ofSeconds(2),
                    Map.of("tables", 5));
            TaskResult r2 = TaskResult.failed(
                    TaskId.of("t2"), "load-http", Duration.ofMillis(500),
                    "connection refused", new RuntimeException("connection refused"));

            ExecutionContext ctx = ExecutionContext.initial(execId, scId)
                    .with("t1", "agent-A", r1)
                    .with("t2", "agent-B", r2);

            Map<String, Object> map = mapper.contextToMap(ctx);
            ExecutionContext restored = mapper.mapToContext(execId, scId, map);

            assertThat(restored.executionId()).isEqualTo(execId);
            assertThat(restored.scenarioId()).isEqualTo(scId);

            // Verify task t1 / agent-A
            Map<String, TaskResult> t1Results = restored.getAll("t1");
            assertThat(t1Results).hasSize(1);
            TaskResult restoredR1 = t1Results.get("agent-A");
            assertThat(restoredR1.taskName()).isEqualTo("prep-db");
            assertThat(restoredR1.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(restoredR1.duration()).isEqualTo(Duration.ofSeconds(2));
            assertThat(restoredR1.outputs()).containsEntry("tables", 5);

            // Verify task t2 / agent-B (failed)
            Map<String, TaskResult> t2Results = restored.getAll("t2");
            assertThat(t2Results).hasSize(1);
            TaskResult restoredR2 = t2Results.get("agent-B");
            assertThat(restoredR2.taskName()).isEqualTo("load-http");
            assertThat(restoredR2.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(restoredR2.errorMessage()).isEqualTo("connection refused");
        }

        @Test
        @DisplayName("should handle empty store map")
        void shouldHandleEmptyStore() {
            ExecutionContext ctx = ExecutionContext.initial(
                    ExecutionId.of("exec-empty"), ScenarioId.of("sc-empty"));

            Map<String, Object> map = mapper.contextToMap(ctx);
            ExecutionContext restored = mapper.mapToContext(
                    ExecutionId.of("exec-empty"), ScenarioId.of("sc-empty"), map);

            assertThat(restored.store()).isEmpty();
        }

        @Test
        @DisplayName("should handle null store in context map gracefully")
        void shouldHandleNullStore() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("executionId", "exec-null");
            map.put("scenarioId", "sc-null");
            map.put("store", null);

            ExecutionContext restored = mapper.mapToContext(
                    ExecutionId.of("exec-null"), ScenarioId.of("sc-null"), map);

            assertThat(restored.store()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // taskResult serialization helpers
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("taskResult serialization helpers")
    class TaskResultSerialization {

        @Test
        @DisplayName("should round-trip successful TaskResult via map")
        void shouldRoundTripSuccessfulTaskResult() {
            TaskResult original = TaskResult.success(
                    TaskId.of("task-ok"), "http-get", Duration.ofMillis(120),
                    Map.of("status", 200, "body", "OK"));

            Map<String, Object> map = mapper.taskResultToMap(original);
            TaskResult restored = mapper.mapToTaskResult(map);

            assertThat(restored.taskId()).isEqualTo(TaskId.of("task-ok"));
            assertThat(restored.taskName()).isEqualTo("http-get");
            assertThat(restored.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(restored.duration()).isEqualTo(Duration.ofMillis(120));
            assertThat(restored.outputs()).containsEntry("status", 200);
            assertThat(restored.outputs()).containsEntry("body", "OK");
            assertThat(restored.errorMessage()).isNull();
            assertThat(restored.cause()).isNull();
        }

        @Test
        @DisplayName("should round-trip failed TaskResult via map")
        void shouldRoundTripFailedTaskResult() {
            TaskResult original = TaskResult.failed(
                    TaskId.of("task-fail"), "kafka-produce", Duration.ofSeconds(5),
                    "broker not available", new RuntimeException("broker not available"));

            Map<String, Object> map = mapper.taskResultToMap(original);
            TaskResult restored = mapper.mapToTaskResult(map);

            assertThat(restored.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(restored.errorMessage()).isEqualTo("broker not available");
            assertThat(restored.cause()).isNotNull();
            assertThat(restored.cause().getMessage()).isEqualTo("broker not available");
        }

        @Test
        @DisplayName("should handle TaskResult with null cause")
        void shouldHandleNullCause() {
            TaskResult original = new TaskResult(
                    TaskId.of("t-nocause"), "no-cause-task", TaskStatus.SKIPPED,
                    Duration.ZERO, Map.of(), "skipped reason", null,
                    Instant.parse("2026-06-19T10:00:00Z"));

            Map<String, Object> map = mapper.taskResultToMap(original);
            TaskResult restored = mapper.mapToTaskResult(map);

            assertThat(restored.status()).isEqualTo(TaskStatus.SKIPPED);
            assertThat(restored.errorMessage()).isEqualTo("skipped reason");
            assertThat(restored.cause()).isNotNull(); // reconstructed as RuntimeException
            assertThat(restored.cause().getMessage()).isEqualTo("skipped reason");
        }
    }
}
