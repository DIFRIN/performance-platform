package com.performance.platform.reporting.model;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskStatus;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Entrée de rapport pour une tâche individuelle (préparation, injection ou assertion).
 * Utilise {@code String taskName} (pas {@code TaskType}) — conforme au glossaire.
 * Record immuable — copie défensive sur {@code outputs}.
 */
public record TaskReportEntry(
    TaskId taskId,
    String taskName,
    TaskStatus status,
    Duration duration,
    Map<String, Object> outputs
) {
    public TaskReportEntry {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(duration, "duration required");
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
