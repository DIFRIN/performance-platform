package com.performance.platform.infrastructure.persistence.mapper;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.infrastructure.persistence.ExecutionStateEntity;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts between the domain {@link ExecutionState} record and the
 * {@link ExecutionStateEntity} JPA entity.
 *
 * <p>Phase statuses are mapped from {@code Map<Phase, PhaseStatus>} to
 * {@code Map<String, String>} and back. The {@link ExecutionContext} is
 * serialized as a {@code Map<String, Object>} structure for JSONB storage,
 * using a deterministic manual mapping to avoid Jackson dependency
 * requirements for nested domain types ({@code Duration}, {@code Throwable}).</p>
 *
 * <p>This class is a Spring {@code @Component} so it can be injected into
 * the {@link JpaExecutionRepository} adapter.</p>
 */
@Component
public class ExecutionStateMapper {

    /**
     * Converts a domain {@link ExecutionState} to a JPA entity.
     *
     * @param state domain state (non-null)
     * @return JPA entity representation
     */
    public ExecutionStateEntity toEntity(ExecutionState state) {
        Map<String, String> phases = state.phaseStatuses().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> e.getValue().name(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, Object> context = contextToMap(state.context());

        return new ExecutionStateEntity(
                state.id().value(),
                state.scenarioId().value(),
                state.status(),
                phases,
                context,
                state.startedAt(),
                state.updatedAt()
        );
    }

    /**
     * Converts a JPA entity back to the domain {@link ExecutionState} record.
     *
     * @param entity JPA entity (non-null)
     * @return domain state record
     */
    public ExecutionState toDomain(ExecutionStateEntity entity) {
        Map<Phase, PhaseStatus> phaseStatuses = entity.phases().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Phase.valueOf(e.getKey()),
                        e -> PhaseStatus.valueOf(e.getValue()),
                        (a, b) -> a,
                        () -> new EnumMap<>(Phase.class)));

        ExecutionContext context = mapToContext(
                ExecutionId.of(entity.id()),
                ScenarioId.of(entity.scenarioId()),
                entity.context());

        return new ExecutionState(
                ExecutionId.of(entity.id()),
                ScenarioId.of(entity.scenarioId()),
                entity.status(),
                phaseStatuses,
                context,
                entity.startedAt(),
                entity.updatedAt()
        );
    }

    /**
     * Serializes an {@link ExecutionContext} to a deterministic map structure.
     * Uses manual conversion to avoid Jackson dependency for
     * {@link Duration} and {@link Throwable} serialization.
     *
     * <p>The produced map is compatible with Hibernate 6 JSONB via Jackson
     * since all leaf values are standard types ({@code String}, {@code Number},
     * nested {@code Map}).</p>
     */
    Map<String, Object> contextToMap(ExecutionContext ctx) {
        var map = new LinkedHashMap<String, Object>();
        map.put("executionId", ctx.executionId().value());
        map.put("scenarioId", ctx.scenarioId().value());

        Map<String, Map<String, Map<String, Object>>> storeMap = new LinkedHashMap<>();
        for (var taskEntry : ctx.store().entrySet()) {
            Map<String, Map<String, Object>> agentMap = new LinkedHashMap<>();
            for (var agentEntry : taskEntry.getValue().entrySet()) {
                agentMap.put(agentEntry.getKey(), taskResultToMap(agentEntry.getValue()));
            }
            storeMap.put(taskEntry.getKey(), agentMap);
        }
        map.put("store", storeMap);
        return map;
    }

    /**
     * Serializes a single {@link TaskResult} to a map, converting
     * {@link Duration} to ISO-8601 string and {@link Throwable#getMessage()} to string.
     */
    Map<String, Object> taskResultToMap(TaskResult result) {
        var map = new LinkedHashMap<String, Object>();
        map.put("taskId", result.taskId().value());
        map.put("taskName", result.taskName());
        map.put("status", result.status().name());
        map.put("duration", result.duration().toString());
        map.put("outputs", new LinkedHashMap<>(result.outputs()));
        map.put("errorMessage", result.errorMessage());
        map.put("causeMessage", result.cause() != null ? result.cause().getMessage() : null);
        map.put("completedAt", result.completedAt().toString());
        return map;
    }

    /**
     * Deserializes a context map back to an {@link ExecutionContext}.
     *
     * @param execId execution identifier extracted from the entity
     * @param scId   scenario identifier extracted from the entity
     * @param map    raw context map from the JSONB column
     * @return reconstructed {@link ExecutionContext}
     */
    ExecutionContext mapToContext(ExecutionId execId, ScenarioId scId, Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Map<String, Object>>> rawStore =
                (Map<String, Map<String, Map<String, Object>>>) map.get("store");

        Map<String, Map<String, TaskResult>> store = new HashMap<>();
        if (rawStore != null) {
            for (var taskEntry : rawStore.entrySet()) {
                Map<String, TaskResult> agentMap = new HashMap<>();
                for (var agentEntry : taskEntry.getValue().entrySet()) {
                    Map<String, Object> resultMap = agentEntry.getValue();
                    agentMap.put(agentEntry.getKey(), mapToTaskResult(resultMap));
                }
                store.put(taskEntry.getKey(), agentMap);
            }
        }
        return new ExecutionContext(execId, scId, store);
    }

    /**
     * Deserializes a single task result map back to a {@link TaskResult}.
     * The original {@link Throwable} cause is not reconstructible;
     * a {@link RuntimeException} with the error message is created instead.
     */
    TaskResult mapToTaskResult(Map<String, Object> map) {
        TaskId taskId = TaskId.of((String) map.get("taskId"));
        String taskName = (String) map.get("taskName");
        TaskStatus status = TaskStatus.valueOf((String) map.get("status"));
        Duration duration = Duration.parse((String) map.get("duration"));

        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) map.getOrDefault("outputs", Map.of());

        String errorMessage = (String) map.get("errorMessage");
        Throwable cause = (errorMessage != null)
                ? new RuntimeException(errorMessage)
                : null;

        Instant completedAt = Instant.parse((String) map.get("completedAt"));
        return new TaskResult(taskId, taskName, status, duration, outputs, errorMessage, cause, completedAt);
    }
}
