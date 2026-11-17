package com.performance.platform.injection.gatling.worker;

import java.util.Map;

/**
 * Rebuilds, inside the worker JVM, the state a protocol published in the parent
 * ({@code PreparedInjection#workerState()}). One implementation per state {@link #type()}.
 */
public interface WorkerStateRestorer {

    /** Value of the {@code type} key of the state this restorer handles. */
    String type();

    void restore(String simulationId, Map<String, Object> data);
}
