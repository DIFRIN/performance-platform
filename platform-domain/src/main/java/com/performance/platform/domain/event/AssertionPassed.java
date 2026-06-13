package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une assertion passe avec succès.
 * Record immuable, 0 annotation framework.
 */
public record AssertionPassed(ExecutionId executionId, TaskId assertionId, Instant timestamp) {

    public AssertionPassed {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
