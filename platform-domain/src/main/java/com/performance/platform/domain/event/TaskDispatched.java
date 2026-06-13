package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une tâche est envoyée au transport pour exécution.
 * Utilise {@code String taskName} (jamais d'enum TaskType).
 * Record immuable, 0 annotation framework.
 */
public record TaskDispatched(ExecutionId executionId, TaskId taskId, String taskName, MessageId messageId, Instant timestamp) {

    public TaskDispatched {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(messageId, "messageId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
