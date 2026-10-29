package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;

import java.util.Objects;
import java.util.Map;

/** Planned task identity and dispatch state; terminal outcomes remain in task results. */
public record ExecutionTask(TaskId id, String taskName, Phase phase, boolean started,
                            Map<String, Object> configuration) {
    public ExecutionTask(TaskId id, String taskName, Phase phase, boolean started) {
        this(id, taskName, phase, started, Map.of());
    }

    public ExecutionTask {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(phase, "phase required");
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }

    public ExecutionTask start() {
        return new ExecutionTask(id, taskName, phase, true, configuration);
    }
}
