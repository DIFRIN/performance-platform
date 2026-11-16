package com.performance.platform.infrastructure.persistence.mapper;

import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.application.schedule.ScheduledRun;
import com.performance.platform.infrastructure.persistence.ScheduledRunEntity;

import org.springframework.stereotype.Component;

/** Converts between {@link ScheduledRun} and {@link ScheduledRunEntity}. */
@Component
public class ScheduledRunMapper {

    public ScheduledRunEntity toEntity(ScheduledRun run) {
        return new ScheduledRunEntity(
                run.id().value(),
                run.scenarioId().value(),
                run.scenarioYaml(),
                run.kind(),
                run.expression(),
                run.nextRunAt(),
                run.status(),
                run.lastExecutionId(),
                run.createdAt(),
                run.updatedAt());
    }

    public ScheduledRun toDomain(ScheduledRunEntity entity) {
        return new ScheduledRun(
                ScheduledRunId.of(entity.id()),
                ScenarioId.of(entity.scenarioId()),
                entity.scenarioYaml(),
                entity.kind(),
                entity.expression(),
                entity.nextRunAt(),
                entity.status(),
                entity.lastExecutionId(),
                entity.createdAt(),
                entity.updatedAt());
    }
}
