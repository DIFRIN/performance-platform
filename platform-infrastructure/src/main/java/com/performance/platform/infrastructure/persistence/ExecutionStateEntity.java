package com.performance.platform.infrastructure.persistence;

import com.performance.platform.domain.execution.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * JPA entity mapping the {@code execution_state} table.
 * Stores the global status, per-phase statuses (JSONB), and
 * execution context snapshot (JSONB).
 *
 * <p>Package-private — not exposed outside the {@code .persistence} package.
 * Mapper layer (ISSUE-051) converts between this entity and the domain
 * {@link com.performance.platform.domain.execution.ExecutionState} record.</p>
 *
 * <p>JSONB columns ({@code phases}, {@code context}) leverage Hibernate 6
 * native JSON support with Jackson on the classpath.</p>
 */
@Entity
@Table(name = "execution_state")
class ExecutionStateEntity {

    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @Column(name = "scenario_id", nullable = false, length = 255)
    private String scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ExecutionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "phases", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> phases;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA-only constructor. */
    ExecutionStateEntity() {}

    ExecutionStateEntity(String id, String scenarioId, ExecutionStatus status,
                         Map<String, String> phases, Map<String, Object> context,
                         Instant startedAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id required");
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId required");
        this.status = Objects.requireNonNull(status, "status required");
        this.phases = phases == null ? Map.of() : Map.copyOf(phases);
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    }

    String id() { return id; }
    String scenarioId() { return scenarioId; }
    ExecutionStatus status() { return status; }
    Map<String, String> phases() { return phases; }
    Map<String, Object> context() { return context; }
    Instant startedAt() { return startedAt; }
    Instant updatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "ExecutionStateEntity{id=%s, scenarioId=%s, status=%s}"
                .formatted(id, scenarioId, status);
    }
}
