package com.performance.platform.injection.gatling.worker;

import java.time.Duration;

/** Poignee sur un worker en cours d'execution (ADR-066). */
public interface WorkerHandle {

    /** Bloquant, a appeler depuis un Virtual Thread. */
    WorkerOutcome await() throws InterruptedException;

    /** {@code destroy()} puis, apres {@code grace}, {@code destroyForcibly()}. */
    void stop(Duration grace);

    boolean alive();
}
