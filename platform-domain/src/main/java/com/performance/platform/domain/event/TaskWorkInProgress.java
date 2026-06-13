package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis périodiquement par un agent pour reporter la progression d'une tâche en cours.
 * {@code progressPercent} doit être entre 0 et 100.
 * Record immuable, 0 annotation framework.
 */
public record TaskWorkInProgress(ExecutionId executionId, TaskId taskId, AgentId agentId, int progressPercent, String statusMessage, Instant timestamp) {

    public TaskWorkInProgress {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(timestamp, "timestamp required");
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100, got: " + progressPercent);
        }
    }

    /**
     * Crée un événement de progression avec le pourcentage indiqué.
     */
    public static TaskWorkInProgress of(ExecutionId executionId, TaskId taskId, AgentId agentId, int progressPercent, String statusMessage, Instant timestamp) {
        return new TaskWorkInProgress(executionId, taskId, agentId, progressPercent, statusMessage, timestamp);
    }
}
