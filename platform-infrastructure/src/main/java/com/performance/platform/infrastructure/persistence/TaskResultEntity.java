package com.performance.platform.infrastructure.persistence;

import com.performance.platform.domain.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * JPA entity mapping the {@code task_result} table.
 * Composite primary key {@code (execution_id, task_id, agent_id)}
 * supports the multi-claim pattern where multiple agents produce
 * results for the same task within one execution (ADR-011).
 *
 * <p>Package-private — not exposed outside the {@code .persistence} package.
 * Mapper layer (ISSUE-051) converts between this entity and the domain
 * {@link com.performance.platform.domain.task.TaskResult} record.</p>
 *
 * <p>{@code outputs} is stored as JSONB using Hibernate 6 native JSON support.</p>
 */
@Entity
@Table(name = "task_result")
class TaskResultEntity {

    @EmbeddedId
    private TaskResultId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TaskStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> outputs;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** JPA-only constructor. */
    TaskResultEntity() {}

    TaskResultEntity(TaskResultId id, TaskStatus status,
                     Map<String, Object> outputs, Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id required");
        this.status = Objects.requireNonNull(status, "status required");
        this.outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        this.completedAt = completedAt;
    }

    TaskResultId id() { return id; }
    String executionId() { return id.executionId(); }
    String taskId() { return id.taskId(); }
    String agentId() { return id.agentId(); }
    TaskStatus status() { return status; }
    Map<String, Object> outputs() { return outputs; }
    Instant completedAt() { return completedAt; }

    @Override
    public String toString() {
        return "TaskResultEntity{executionId=%s, taskId=%s, agentId=%s, status=%s}"
                .formatted(id.executionId(), id.taskId(), id.agentId(), status);
    }
}
