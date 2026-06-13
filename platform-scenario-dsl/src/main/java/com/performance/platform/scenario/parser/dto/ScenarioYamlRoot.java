package com.performance.platform.scenario.parser.dto;

import java.util.Map;

/**
 * Racine du document YAML de scenario.
 * Contient la section scenario et les loadModels.
 * DTO interne — jamais expose hors du module.
 */
public record ScenarioYamlRoot(
    ScenarioYamlDto scenario,
    Map<String, LoadModelYamlDto> loadModels
) {}
