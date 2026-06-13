package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsque l'exécution d'un scénario démarre.
 * Record immuable, 0 annotation framework.
 */
public record ScenarioStarted(ExecutionId executionId, ScenarioId scenarioId, Instant timestamp) {

    public ScenarioStarted {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
