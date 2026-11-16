package com.performance.platform.domain.scenario;

import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.report.ReportDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Définition complète d'un scénario de test de performance.
 * Le mode d'exécution n'est plus déclaré dans le scénario : il vient du runtime
 * uniquement (ADR-053). Les modèles de charge sont inline dans les étapes.
 * Record immuable — copies défensives sur toutes les collections.
 * 0 annotation framework.
 */
public record ScenarioDefinition(
    ScenarioId id,
    String name,
    String version,
    List<String> tags,
    Map<String, String> metadata,
    List<StepDefinition> steps,
    String sourceYaml,
    ReportDefinition report
) {
    public ScenarioDefinition {
        Objects.requireNonNull(id, "id required");
        // name peut être null (optionnel dans les specs)
        // version peut être null
        tags = tags == null ? List.of() : List.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
