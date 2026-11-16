package com.performance.platform.infrastructure.persistence;

import com.performance.platform.application.schedule.ScheduleKind;
import com.performance.platform.application.schedule.ScheduledRunStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** Entite JPA d'un run planifie ({@code scheduled_run}). */
@Entity
@Table(name = "scheduled_run")
public class ScheduledRunEntity {

    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @Column(name = "scenario_id", nullable = false, length = 255)
    private String scenarioId;

    @Column(name = "scenario_yaml", nullable = false, columnDefinition = "text")
    private String scenarioYaml;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_kind", nullable = false, length = 20)
    private ScheduleKind kind;

    @Column(name = "schedule_expression", nullable = false, length = 255)
    private String expression;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduledRunStatus status;

    @Column(name = "last_execution_id", length = 255)
    private String lastExecutionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA-only constructor. */
    public ScheduledRunEntity() {
    }

    public ScheduledRunEntity(String id, String scenarioId, String scenarioYaml,
                              ScheduleKind kind, String expression, Instant nextRunAt,
                              ScheduledRunStatus status, String lastExecutionId,
                              Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id required");
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId required");
        this.scenarioYaml = Objects.requireNonNull(scenarioYaml, "scenarioYaml required");
        this.kind = Objects.requireNonNull(kind, "kind required");
        this.expression = expression == null ? "" : expression;
        this.nextRunAt = Objects.requireNonNull(nextRunAt, "nextRunAt required");
        this.status = Objects.requireNonNull(status, "status required");
        this.lastExecutionId = lastExecutionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    }

    public String id() { return id; }
    public String scenarioId() { return scenarioId; }
    public String scenarioYaml() { return scenarioYaml; }
    public ScheduleKind kind() { return kind; }
    public String expression() { return expression; }
    public Instant nextRunAt() { return nextRunAt; }
    public ScheduledRunStatus status() { return status; }
    public String lastExecutionId() { return lastExecutionId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "ScheduledRunEntity{id=%s, scenarioId=%s, kind=%s, status=%s}"
                .formatted(id, scenarioId, kind, status);
    }
}
