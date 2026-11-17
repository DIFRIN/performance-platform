package com.performance.platform.injection.gatling.protocol;

import java.util.Map;

/**
 * A simulation generated for one step. Its spec is published (keyed by the step id, which is the
 * Gatling simulation id) until {@link #close()} releases it together with any resource it opened.
 */
public interface PreparedInjection extends AutoCloseable {

    /** Fully qualified name of the public Gatling {@code Simulation} to run. */
    String simulationClass();

    /**
     * JSON-serialisable snapshot of what the simulation needs, written to the run directory so the
     * worker JVM (ADR-066) can rebuild it: static holders do not cross processes. Empty when the
     * simulation needs nothing beyond the plan.
     */
    default Map<String, Object> workerState() {
        return Map.of();
    }

    @Override
    void close();
}
