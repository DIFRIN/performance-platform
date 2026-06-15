package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.scenario.Phase;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Etat courant d'une exécution de scenario.
 * Suit le statut global, le statut de chaque phase, et le contexte d'exécution.
 * Record immuable — copie défensive sur {@code phaseStatuses}.
 * 0 annotation framework.
 */
public record ExecutionState(
    ExecutionId id,
    ScenarioId scenarioId,
    ExecutionStatus status,
    Map<Phase, PhaseStatus> phaseStatuses,
    ExecutionContext context,
    Instant startedAt,
    Instant updatedAt
) {
    public ExecutionState {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(context, "context required");
        Objects.requireNonNull(startedAt, "startedAt required");
        Objects.requireNonNull(updatedAt, "updatedAt required");
        if (phaseStatuses == null) {
            phaseStatuses = Map.of();
        } else {
            phaseStatuses = Map.copyOf(phaseStatuses);
        }
    }
}
