package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une tâche échoue après toutes ses tentatives de retry.
 * Record immuable, 0 annotation framework.
 */
public record TaskFailed(ExecutionId executionId, TaskId taskId, AgentId agentId, String error, int attempt, Instant timestamp) {

    public TaskFailed {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(timestamp, "timestamp required");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1, got: " + attempt);
        }
    }
}
