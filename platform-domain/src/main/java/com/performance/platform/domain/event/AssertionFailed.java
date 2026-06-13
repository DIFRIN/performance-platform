package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une assertion échoue.
 * Record immuable, 0 annotation framework.
 */
public record AssertionFailed(ExecutionId executionId, TaskId assertionId, String reason, Instant timestamp) {

    public AssertionFailed {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(reason, "reason required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
