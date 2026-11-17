package com.performance.platform.injection.gatling.worker;

/** Lance un worker d'injection (JVM fille) pour une etape (ADR-066). */
public interface InjectionWorkerLauncher {

    /** Demarre le worker ; le run directory de {@code spec} doit deja contenir ses entrees. */
    WorkerHandle start(WorkerRunSpec spec);
}
