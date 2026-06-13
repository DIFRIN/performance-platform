package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un agent réclame une tâche via le mécanisme de multi-claim.
 * Record immuable, 0 annotation framework.
 */
public record TaskClaimedByAgent(ExecutionId executionId, TaskId taskId, AgentId agentId, MessageId messageId, Instant timestamp) {

    public TaskClaimedByAgent {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(messageId, "messageId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
