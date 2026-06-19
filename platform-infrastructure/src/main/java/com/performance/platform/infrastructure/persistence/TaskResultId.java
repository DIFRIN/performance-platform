package com.performance.platform.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link TaskResultEntity}.
 * Supports multi-claim pattern (ADR-011) — multiple agents can produce
 * independent results for the same {@code taskId} within one execution.
 *
 * <p>Package-private — not exposed outside the {@code .persistence} package.</p>
 */
@Embeddable
class TaskResultId implements Serializable {

    @Column(name = "execution_id", nullable = false, length = 255)
    private String executionId;

    @Column(name = "task_id", nullable = false, length = 255)
    private String taskId;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    /** JPA-only constructor. */
    TaskResultId() {}

    TaskResultId(String executionId, String taskId, String agentId) {
        this.executionId = Objects.requireNonNull(executionId, "executionId required");
        this.taskId = Objects.requireNonNull(taskId, "taskId required");
        this.agentId = Objects.requireNonNull(agentId, "agentId required");
    }

    String executionId() { return executionId; }
    String taskId() { return taskId; }
    String agentId() { return agentId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskResultId that)) return false;
        return executionId.equals(that.executionId)
                && taskId.equals(that.taskId)
                && agentId.equals(that.agentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, taskId, agentId);
    }

    @Override
    public String toString() {
        return "TaskResultId{executionId=%s, taskId=%s, agentId=%s}"
                .formatted(executionId, taskId, agentId);
    }
}
