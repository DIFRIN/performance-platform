package com.performance.platform.scenario.parser.dto;

import java.util.List;

/**
 * DTO pour la politique de retry dans le YAML.
 * Tous les champs sont optionnels ; des valeurs par defaut sont appliquees au mapping.
 * DTO interne — jamais expose hors du module.
 */
public record RetryPolicyYamlDto(
    Integer maxAttempts,
    String initialDelay,
    Double multiplier,
    String maxDelay,
    List<String> retryableExceptions
) {}
