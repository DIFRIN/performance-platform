package com.performance.platform.injection.gatling.worker;

import com.performance.platform.domain.injection.GeneratorHealth;

import java.nio.file.Path;

/** Sortie d'un worker d'injection (ADR-066). {@code generatorHealth} : mesuree par le worker (ADR-068). */
public record WorkerOutcome(int exitCode, boolean completed, boolean stoppedByPlatform,
                            Path resultsDirectory, Path resultFile, String logTail,
                            GeneratorHealth generatorHealth) {

    public WorkerOutcome {
        logTail = logTail == null ? "" : logTail;
        generatorHealth = generatorHealth == null ? GeneratorHealth.unknown() : generatorHealth;
    }

    public WorkerOutcome(int exitCode, boolean completed, boolean stoppedByPlatform,
                         Path resultsDirectory, Path resultFile, String logTail) {
        this(exitCode, completed, stoppedByPlatform, resultsDirectory, resultFile, logTail, null);
    }
}
