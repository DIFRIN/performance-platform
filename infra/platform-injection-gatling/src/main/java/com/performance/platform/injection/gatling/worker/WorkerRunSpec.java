package com.performance.platform.injection.gatling.worker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Entree d'un worker d'injection (ADR-066).
 * {@code environment} n'est jamais persiste dans le run directory : il est transmis
 * a l'environnement du processus fils (secrets).
 */
public record WorkerRunSpec(Path runDirectory, String simulationId, String simulationClass,
                            Map<String, String> systemProperties, Map<String, String> environment,
                            Duration timeout) {

    public WorkerRunSpec {
        Objects.requireNonNull(runDirectory, "runDirectory required");
        Objects.requireNonNull(simulationId, "simulationId required");
        Objects.requireNonNull(simulationClass, "simulationClass required");
        systemProperties = systemProperties == null ? Map.of() : Map.copyOf(systemProperties);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        timeout = timeout == null ? Duration.ZERO : timeout;
    }
}
