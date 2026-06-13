package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un agent commence effectivement l'exécution d'une tâche.
 * Record immuable, 0 annotation framework.
 */
public record TaskStarted(ExecutionId executionId, TaskId taskId, AgentId agentId, Instant timestamp) {

    public TaskStarted {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
