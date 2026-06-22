package com.performance.platform.infrastructure.persistence.mapper;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;

import com.performance.platform.infrastructure.persistence.TaskResultEntity;
import com.performance.platform.infrastructure.persistence.TaskResultId;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts between the domain {@link TaskResult} record and the
 * {@link TaskResultEntity} JPA entity.
 *
 * <p>The entity table ({@code task_result}) stores only {@code status},
 * {@code outputs} (JSONB), and {@code completed_at} alongside the composite
 * primary key. Fields absent from the entity schema ({@code taskName},
 * {@code duration}, {@code errorMessage}) are embedded as metadata entries
 * inside the {@code outputs} JSONB column under the reserved key
 * {@value #META_KEY}, ensuring round-trip fidelity.</p>
 *
 * <p>This class is a Spring {@code @Component} so it can be injected into
 * the {@link com.performance.platform.infrastructure.persistence.JpaExecutionRepository}
 * adapter.</p>
 */
@Component
public class TaskResultMapper {

    /** Reserved key in the outputs map for round-trip metadata. */
    static final String META_KEY = "_meta_";

    /**
     * Converts domain identifiers and a {@link TaskResult} to a JPA entity.
     * Fields absent from the entity schema are embedded in the
     * {@code outputs} JSONB column as metadata.
     *
     * @param executionId execution identifier
     * @param taskId      task identifier
     * @param agentId     agent identifier
     * @param result      domain task result (non-null)
     * @return JPA entity representation
     */
    public TaskResultEntity toEntity(ExecutionId executionId, TaskId taskId,
                                     AgentId agentId, TaskResult result) {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(result, "result required");

        var id = new TaskResultId(
                executionId.value(),
                taskId.value(),
                agentId.value());

        Map<String, Object> outputs = buildOutputsWithMetadata(result);

        return new TaskResultEntity(
                id,
                result.status(),
                outputs,
                result.completedAt()
        );
    }

    /**
     * Converts a JPA entity back to the domain {@link TaskResult} record.
     * Metadata embedded in the outputs map is extracted and stripped,
     * restoring the original {@code taskName}, {@code duration}, and
     * {@code errorMessage}.
     *
     * <p>If the entity has a {@code null} {@code completedAt}, the domain
     * {@link TaskResult#completedAt()} defaults to {@link Instant#EPOCH}
     * since the domain record requires a non-null value.</p>
     *
     * @param entity JPA entity (non-null)
     * @return domain task result record
     */
    public TaskResult toDomain(TaskResultEntity entity) {
        Objects.requireNonNull(entity, "entity required");

        TaskId taskId = TaskId.of(entity.taskId());
        Map<String, Object> rawOutputs = entity.outputs();

        // Extract metadata and build clean outputs
        Metadata meta = extractMetadata(rawOutputs);
        Map<String, Object> cleanOutputs = stripMetadata(rawOutputs);

        Instant completedAt = entity.completedAt() != null
                ? entity.completedAt()
                : Instant.EPOCH;

        return new TaskResult(
                taskId,
                meta.taskName,
                entity.status(),
                meta.duration,
                cleanOutputs,
                meta.errorMessage,
                meta.errorMessage != null ? new RuntimeException(meta.errorMessage) : null,
                completedAt
        );
    }

    /**
     * Builds the outputs map augmented with round-trip metadata.
     * The user outputs are placed at the top level alongside a
     * {@value #META_KEY} entry containing {@code taskName}, {@code duration}
     * (ISO-8601 string), and {@code errorMessage}.
     */
    Map<String, Object> buildOutputsWithMetadata(TaskResult result) {
        var outputs = new LinkedHashMap<String, Object>();
        outputs.putAll(result.outputs());

        var meta = new LinkedHashMap<String, Object>();
        meta.put("taskName", result.taskName());
        meta.put("duration", result.duration().toString());
        meta.put("errorMessage", result.errorMessage());
        meta.put("causeMessage", result.cause() != null ? result.cause().getMessage() : null);
        outputs.put(META_KEY, meta);

        return outputs;
    }

    /**
     * Extracts metadata from the outputs map without modifying it.
     *
     * @return a {@link Metadata} record with extracted values; defaults are
     *         used when metadata is absent
     */
    @SuppressWarnings("unchecked")
    Metadata extractMetadata(Map<String, Object> outputs) {
        Object metaObj = outputs.get(META_KEY);
        if (!(metaObj instanceof Map)) {
            return Metadata.MISSING;
        }
        Map<String, Object> meta = (Map<String, Object>) metaObj;

        String taskName = meta.get("taskName") instanceof String s ? s : "unknown";
        Duration duration;
        try {
            duration = Duration.parse((String) meta.getOrDefault("duration", "PT0S"));
        } catch (Exception e) {
            duration = Duration.ZERO;
        }
        String errorMessage = meta.get("errorMessage") instanceof String s ? s : null;

        return new Metadata(taskName, duration, errorMessage);
    }

    /**
     * Returns a copy of outputs without the metadata entry.
     */
    Map<String, Object> stripMetadata(Map<String, Object> outputs) {
        var clean = new LinkedHashMap<String, Object>(outputs);
        clean.remove(META_KEY);
        return Map.copyOf(clean);
    }

    /**
     * Internal carrier for round-trip metadata extracted from the outputs map.
     */
    record Metadata(String taskName, Duration duration, String errorMessage) {
        static final Metadata MISSING = new Metadata("unknown", Duration.ZERO, null);
    }
}
