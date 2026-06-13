package com.performance.platform.domain.task;

import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Résultat final d'une exécution de tâche.
 * Record immuable — copies défensives sur outputs.
 * Utilise {@code String taskName} (jamais d'enum TaskType).
 * 0 annotation framework.
 */
public record TaskResult(
    TaskId taskId,
    String taskName,
    TaskStatus status,
    Duration duration,
    Map<String, Object> outputs,
    String errorMessage,
    Throwable cause,
    Instant completedAt
) {
    public TaskResult {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(duration, "duration required");
        Objects.requireNonNull(completedAt, "completedAt required");
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }

    public static TaskResult success(TaskId id, String taskName, Duration duration, Map<String, Object> outputs) {
        return new TaskResult(id, taskName, TaskStatus.SUCCESS, duration, outputs, null, null, Instant.now());
    }

    public static TaskResult failed(TaskId id, String taskName, Duration duration, String message, Throwable cause) {
        return new TaskResult(id, taskName, TaskStatus.FAILED, duration, Map.of(), message, cause, Instant.now());
    }

    public static TaskResult skipped(TaskId id, String taskName, String reason) {
        return new TaskResult(id, taskName, TaskStatus.SKIPPED, Duration.ZERO, Map.of(), reason, null, Instant.now());
    }

    public boolean isSuccess() {
        return status == TaskStatus.SUCCESS;
    }
}
