package com.performance.platform.injection.gatling.protocol;

/**
 * A simulation generated for one step. Its spec is published (keyed by the step id, which is the
 * Gatling simulation id) until {@link #close()} releases it together with any resource it opened.
 */
public interface PreparedInjection extends AutoCloseable {

    /** Fully qualified name of the public Gatling {@code Simulation} to run. */
    String simulationClass();

    @Override
    void close();
}
