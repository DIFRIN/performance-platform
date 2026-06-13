package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une tâche se termine avec succès.
 * Record immuable, 0 annotation framework.
 */
public record TaskCompleted(ExecutionId executionId, TaskId taskId, AgentId agentId, TaskResult result, Duration duration, Instant timestamp) {

    public TaskCompleted {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(result, "result required");
        Objects.requireNonNull(duration, "duration required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
