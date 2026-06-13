package com.performance.platform.scenario.parser.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO pour un step dans le YAML.
 * DTO interne — jamais expose hors du module.
 */
public record StepYamlDto(
    String id,
    String task,
    String phase,
    List<String> requiredContexts,
    List<String> dependsOn,
    Map<String, Object> parameters,
    String timeout,
    RetryPolicyYamlDto retryPolicy
) {}
