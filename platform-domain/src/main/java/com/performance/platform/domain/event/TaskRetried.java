package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une tâche échoue mais qu'une nouvelle tentative est programmée.
 * Record immuable, 0 annotation framework.
 */
public record TaskRetried(ExecutionId executionId, TaskId taskId, int attempt, Instant nextAttemptAt, Instant timestamp) {

    public TaskRetried {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt required");
        Objects.requireNonNull(timestamp, "timestamp required");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1, got: " + attempt);
        }
    }
}
