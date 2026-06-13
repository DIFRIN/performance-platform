package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.scenario.Phase;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une phase se termine, avec son statut final.
 * Record immuable, 0 annotation framework.
 */
public record PhaseCompleted(ExecutionId executionId, Phase phase, PhaseStatus status, Instant timestamp) {

    public PhaseCompleted {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(phase, "phase required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
