package com.performance.platform.infrastructure.persistence;

import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.ScenarioDetails;
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
import java.util.List;
import java.util.Objects;

/**
 * JPA entity mapping the {@code execution_state} table.
 * Stores the global status, per-phase statuses (JSONB), and
 * execution context snapshot and planned task dispatch state (JSONB).
 *
 * <p>Technically public to allow access from the {@code .persistence.mapper}
 * subpackage, but conceptually confined to the {@code .persistence} package
 * tree. The mapper layer (ISSUE-051) converts between this entity and the
 * domain {@link com.performance.platform.domain.execution.ExecutionState} record.</p>
 *
 * <p>JSONB columns ({@code phases}, {@code context}) leverage Hibernate 6
 * native JSON support with Jackson on the classpath.</p>
 */
@Entity
@Table(name = "execution_state")
public class ExecutionStateEntity {

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

    @Column(name = "planned_task_count", nullable = false)
    private int plannedTaskCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tasks", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> tasks = List.of();

    @Column(name = "scenario_yaml", columnDefinition = "text")
    private String scenarioYaml;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_details", columnDefinition = "jsonb")
    private ScenarioDetails scenarioDetails;

    /** JPA-only constructor. */
    public ExecutionStateEntity() {}

    public ExecutionStateEntity(String id, String scenarioId, ExecutionStatus status,
                                Map<String, String> phases, Map<String, Object> context,
                                Instant startedAt, Instant updatedAt) {
        this(id, scenarioId, status, phases, context, startedAt, updatedAt, 0);
    }

    public ExecutionStateEntity(String id, String scenarioId, ExecutionStatus status,
                                Map<String, String> phases, Map<String, Object> context,
                                Instant startedAt, Instant updatedAt, int plannedTaskCount) {
        this(id, scenarioId, status, phases, context, startedAt, updatedAt, plannedTaskCount, List.of());
    }

    public ExecutionStateEntity(String id, String scenarioId, ExecutionStatus status,
                                Map<String, String> phases, Map<String, Object> context,
                                Instant startedAt, Instant updatedAt, int plannedTaskCount,
                                List<Map<String, Object>> tasks) {
        this(id, scenarioId, status, phases, context, startedAt, updatedAt, plannedTaskCount, tasks, null);
    }

    public ExecutionStateEntity(String id, String scenarioId, ExecutionStatus status,
                                Map<String, String> phases, Map<String, Object> context,
                                Instant startedAt, Instant updatedAt, int plannedTaskCount,
                                List<Map<String, Object>> tasks, String scenarioYaml) {
        this(id, scenarioId, status, phases, context, startedAt, updatedAt,
                plannedTaskCount, tasks, scenarioYaml, null);
    }

    public ExecutionStateEntity(String id, String scenarioId, ExecutionStatus status,
                                Map<String, String> phases, Map<String, Object> context,
                                Instant startedAt, Instant updatedAt, int plannedTaskCount,
                                List<Map<String, Object>> tasks, String scenarioYaml, ScenarioDetails scenarioDetails) {
        this.id = Objects.requireNonNull(id, "id required");
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId required");
        this.status = Objects.requireNonNull(status, "status required");
        this.phases = phases == null ? Map.of() : Map.copyOf(phases);
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
        if (plannedTaskCount < 0) {
            throw new IllegalArgumentException("plannedTaskCount must not be negative");
        }
        this.plannedTaskCount = plannedTaskCount;
        this.tasks = tasks.stream().map(Map::copyOf).toList();
        this.scenarioYaml = scenarioYaml;
        this.scenarioDetails = scenarioDetails;
    }

    public String id() { return id; }
    public String scenarioId() { return scenarioId; }
    public ExecutionStatus status() { return status; }
    public Map<String, String> phases() { return phases; }
    public Map<String, Object> context() { return context; }
    public Instant startedAt() { return startedAt; }
    public Instant updatedAt() { return updatedAt; }
    public int plannedTaskCount() { return plannedTaskCount; }
    public List<Map<String, Object>> tasks() { return tasks; }
    public String scenarioYaml() { return scenarioYaml; }
    public ScenarioDetails scenarioDetails() { return scenarioDetails; }

    @Override
    public String toString() {
        return "ExecutionStateEntity{id=%s, scenarioId=%s, status=%s}"
                .formatted(id, scenarioId, status);
    }
}
