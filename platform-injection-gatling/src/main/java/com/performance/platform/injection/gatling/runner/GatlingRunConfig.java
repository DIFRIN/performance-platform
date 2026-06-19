package com.performance.platform.injection.gatling.runner;

import com.performance.platform.domain.injection.LoadModel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration d'une simulation Gatling.
 * <p>
 * Porte les parametres necessaires au lancement d'une simulation
 * Gatling in-process : la classe de simulation, le modele de charge,
 * les proprietes systeme, le repertoire de resultats, un identifiant
 * unique et un timeout.
 */
public record GatlingRunConfig(
        String simulationClass,
        LoadModel loadModel,
        Map<String, String> systemProperties,
        Path resultsDirectory,
        String simulationId,
        Duration timeout
) {
    public GatlingRunConfig {
        Objects.requireNonNull(simulationClass, "simulationClass must not be null");
        Objects.requireNonNull(loadModel, "loadModel must not be null");
        systemProperties = systemProperties == null ? Map.of() : Map.copyOf(systemProperties);
        Objects.requireNonNull(resultsDirectory, "resultsDirectory must not be null");
        Objects.requireNonNull(simulationId, "simulationId must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
    }
}
