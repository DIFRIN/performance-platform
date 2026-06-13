package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.report.Verdict;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsque l'exécution d'un scénario se termine, quel que soit le verdict.
 * Record immuable, 0 annotation framework.
 */
public record ScenarioFinished(ExecutionId executionId, ScenarioId scenarioId, Verdict verdict, Duration duration, Instant timestamp) {

    public ScenarioFinished {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(duration, "duration required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}
