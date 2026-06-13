package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Etape du plan d'exécution dérivé du scenario.
 * Lie un {@link StepDefinition} a son niveau DAG et ses dependances résolues.
 * Record immuable — copies défensives sur toutes les collections.
 * 0 annotation framework.
 */
public record ExecutionStep(
    StepDefinition step,
    List<TaskId> dependencies,
    int dagLevel,
    Set<String> requiredContextKeys
) {
    public ExecutionStep {
        Objects.requireNonNull(step, "step required");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        requiredContextKeys = requiredContextKeys == null ? Set.of() : Set.copyOf(requiredContextKeys);
        if (dagLevel < 0) {
            throw new IllegalArgumentException("dagLevel must be non-negative, got " + dagLevel);
        }
    }
}
