package com.performance.platform.scenario.parser.dto;

/**
 * DTO pour la sous-section "execution" du YAML.
 * DTO interne — jamais expose hors du module.
 */
public record ExecutionYamlDto(
    String mode
) {}
