package com.performance.platform.domain.scenario;

import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.injection.LoadModel;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Définition complète d'un scénario de test de performance.
 * Record immuable — copies défensives sur toutes les collections.
 * 0 annotation framework.
 */
public record ScenarioDefinition(
    ScenarioId id,
    String name,
    String version,
    List<String> tags,
    Map<String, String> metadata,
    ExecutionMode executionMode,
    List<StepDefinition> steps,
    Map<String, LoadModel> loadModels
) {
    public ScenarioDefinition {
        Objects.requireNonNull(id, "id required");
        // name peut être null (optionnel dans les specs)
        // version peut être null
        tags = tags == null ? List.of() : List.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        // executionMode peut être null (défaut LOCAL)
        steps = steps == null ? List.of() : List.copyOf(steps);
        loadModels = loadModels == null ? Map.of() : Map.copyOf(loadModels);
    }
}
