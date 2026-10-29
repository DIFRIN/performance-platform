package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.scenario.Phase;

import java.time.Instant;
import java.util.Map;
import java.util.List;
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
    Instant updatedAt,
    int plannedTaskCount,
    List<ExecutionTask> tasks,
    String scenarioYaml,
    ScenarioDetails scenarioDetails
) {
    public ExecutionState(ExecutionId id, ScenarioId scenarioId, ExecutionStatus status,
                          Map<Phase, PhaseStatus> phaseStatuses, ExecutionContext context,
                          Instant startedAt, Instant updatedAt, int plannedTaskCount, List<ExecutionTask> tasks,
                          String scenarioYaml) {
        this(id, scenarioId, status, phaseStatuses, context, startedAt, updatedAt,
                plannedTaskCount, tasks, scenarioYaml, null);
    }

    public ExecutionState(ExecutionId id, ScenarioId scenarioId, ExecutionStatus status,
                          Map<Phase, PhaseStatus> phaseStatuses, ExecutionContext context,
                          Instant startedAt, Instant updatedAt, int plannedTaskCount, List<ExecutionTask> tasks) {
        this(id, scenarioId, status, phaseStatuses, context, startedAt, updatedAt, plannedTaskCount, tasks, null);
    }

    public ExecutionState(ExecutionId id, ScenarioId scenarioId, ExecutionStatus status,
                          Map<Phase, PhaseStatus> phaseStatuses, ExecutionContext context,
                          Instant startedAt, Instant updatedAt) {
        this(id, scenarioId, status, phaseStatuses, context, startedAt, updatedAt, 0);
    }

    public ExecutionState(ExecutionId id, ScenarioId scenarioId, ExecutionStatus status,
                          Map<Phase, PhaseStatus> phaseStatuses, ExecutionContext context,
                          Instant startedAt, Instant updatedAt, int plannedTaskCount) {
        this(id, scenarioId, status, phaseStatuses, context, startedAt, updatedAt, plannedTaskCount, List.of());
    }

    public ExecutionState {
        tasks = List.copyOf(tasks);
        if (plannedTaskCount < 0) {
            throw new IllegalArgumentException("plannedTaskCount must not be negative");
        }
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
