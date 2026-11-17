package com.performance.platform.injection.gatling.worker;

import java.nio.file.Path;

/** Sortie d'un worker d'injection (ADR-066). */
public record WorkerOutcome(int exitCode, boolean completed, boolean stoppedByPlatform,
                            Path resultsDirectory, Path resultFile, String logTail) {

    public WorkerOutcome {
        logTail = logTail == null ? "" : logTail;
    }
}
