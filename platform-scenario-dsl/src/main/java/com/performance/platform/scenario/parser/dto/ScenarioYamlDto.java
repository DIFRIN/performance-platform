package com.performance.platform.scenario.parser.dto;

import java.util.List;
import java.util.Map;
import com.performance.platform.domain.report.ReportDefinition;

/**
 * DTO pour la section "scenario" du YAML.
 * DTO interne — jamais expose hors du module.
 */
public record ScenarioYamlDto(
    String id,
    String name,
    String version,
    List<String> tags,
    Map<String, String> metadata,
    ExecutionYamlDto execution,
    Integer taskAvailabilityTimeoutSeconds,
    List<StepYamlDto> steps,
    ReportDefinition report
) {}
