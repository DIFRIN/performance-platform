package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un scénario est annulé avant sa fin normale.
 * Record immuable, 0 annotation framework.
 */
public record ScenarioCancelled(ExecutionId executionId, ScenarioId scenarioId, String reason, Instant timestamp) {

    public ScenarioCancelled {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
