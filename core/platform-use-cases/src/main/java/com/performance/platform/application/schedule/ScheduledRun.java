package com.performance.platform.application.schedule;

import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.ScheduledRunId;

import java.time.Instant;
import java.util.Objects;

/**
 * Run planifie persiste : scenario source, expression de planification et
 * prochaine occurrence. Etat immuable — chaque transition retourne une copie.
 * 0 annotation framework.
 */
public record ScheduledRun(
    ScheduledRunId id,
    ScenarioId scenarioId,
    String scenarioYaml,
    ScheduleKind kind,
    String expression,
    Instant nextRunAt,
    ScheduledRunStatus status,
    String lastExecutionId,
    Instant createdAt,
    Instant updatedAt
) {

    public ScheduledRun {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        Objects.requireNonNull(scenarioYaml, "scenarioYaml required");
        Objects.requireNonNull(kind, "kind required");
        Objects.requireNonNull(nextRunAt, "nextRunAt required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(createdAt, "createdAt required");
        Objects.requireNonNull(updatedAt, "updatedAt required");
        expression = expression == null ? "" : expression;
    }

    public static ScheduledRun pending(ScheduledRunId id, ScenarioId scenarioId, String scenarioYaml,
                                       ScheduleKind kind, String expression, Instant nextRunAt,
                                       Instant now) {
        return new ScheduledRun(id, scenarioId, scenarioYaml, kind, expression, nextRunAt,
                ScheduledRunStatus.PENDING, null, now, now);
    }

    /** @return specification reconstruite pour recalculer la prochaine occurrence. */
    public ScheduleSpec spec() {
        return new ScheduleSpec(kind, expression, kind == ScheduleKind.ONCE ? nextRunAt : null);
    }

    public ScheduledRun completed(String executionId, Instant now) {
        return new ScheduledRun(id, scenarioId, scenarioYaml, kind, expression, nextRunAt,
                ScheduledRunStatus.COMPLETED, executionId, createdAt, now);
    }

    public ScheduledRun failed(Instant now) {
        return new ScheduledRun(id, scenarioId, scenarioYaml, kind, expression, nextRunAt,
                ScheduledRunStatus.FAILED, lastExecutionId, createdAt, now);
    }

    public ScheduledRun rescheduled(Instant next, String executionId, Instant now) {
        return new ScheduledRun(id, scenarioId, scenarioYaml, kind, expression, next,
                ScheduledRunStatus.PENDING, executionId, createdAt, now);
    }

    public ScheduledRun cancelled(Instant now) {
        return new ScheduledRun(id, scenarioId, scenarioYaml, kind, expression, nextRunAt,
                ScheduledRunStatus.CANCELLED, lastExecutionId, createdAt, now);
    }

    /**
     * Replanifie un run en attente avec une nouvelle expression.
     * L'identifiant, le scenario source et la date de creation sont conserves.
     *
     * @param kind      nature de la nouvelle planification
     * @param expression expression normalisee
     * @param nextRunAt prochaine occurrence calculee
     * @param now       instant de la modification
     * @return une copie PENDING du run
     */
    public ScheduledRun withSchedule(ScheduleKind kind, String expression, Instant nextRunAt, Instant now) {
        Objects.requireNonNull(kind, "kind required");
        Objects.requireNonNull(nextRunAt, "nextRunAt required");
        Objects.requireNonNull(now, "now required");
        return new ScheduledRun(id, scenarioId, scenarioYaml, kind, expression, nextRunAt,
                ScheduledRunStatus.PENDING, lastExecutionId, createdAt, now);
    }
}
