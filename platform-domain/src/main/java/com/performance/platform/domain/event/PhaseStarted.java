package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.Phase;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'une phase (PREPARATION, INJECTION, ASSERTION) démarre.
 * Record immuable, 0 annotation framework.
 */
public record PhaseStarted(ExecutionId executionId, Phase phase, Instant timestamp) {

    public PhaseStarted {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(phase, "phase required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
