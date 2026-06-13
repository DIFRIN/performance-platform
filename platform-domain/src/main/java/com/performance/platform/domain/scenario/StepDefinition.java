package com.performance.platform.domain.scenario;

import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Définition d'une étape (tâche) dans un scénario.
 * Remplace l'ancien TaskDefinition — utilise {@code taskName} String libre, pas d'enum TaskType.
 * Record immuable — copies défensives sur toutes les collections.
 * 0 annotation framework.
 */
public record StepDefinition(
    TaskId id,
    String taskName,
    Phase phase,
    Map<String, Object> parameters,
    List<TaskId> dependsOn,
    List<String> requiredContexts,
    Duration timeout,
    RetryPolicy retryPolicy
) {
    public StepDefinition {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(phase, "phase required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requiredContexts = requiredContexts == null ? List.of() : List.copyOf(requiredContexts);
        // timeout peut être null (défaut géré à l'exécution)
        // retryPolicy peut être null (pas de retry)
    }
}
