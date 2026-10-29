package com.performance.platform.reporting.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Informations sur l'environnement d'exécution de la campagne.
 * Record immuable — copies défensives sur {@code agentIds} et {@code systemProperties}.
 */
public record EnvironmentInfo(
    List<String> agentIds,
    String jvmVersion,
    Map<String, String> systemProperties
) {
    public EnvironmentInfo {
        Objects.requireNonNull(agentIds, "agentIds required");
        Objects.requireNonNull(jvmVersion, "jvmVersion required");
        agentIds = List.copyOf(agentIds);
        systemProperties = systemProperties == null ? Map.of() : Map.copyOf(systemProperties);
    }
}
