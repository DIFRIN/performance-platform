package com.performance.platform.injection.gatling.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands the {@link HttpRequestSpec} of a step to the Gatling-instantiated
 * {@link HttpScenarioSimulation}, keyed by simulation id (same pattern as
 * {@code SimulationInjectionHolder}).
 */
public final class HttpRequestSpecHolder {

    private static final Map<String, HttpRequestSpec> SPECS = new ConcurrentHashMap<>();

    private HttpRequestSpecHolder() {
    }

    public static void set(String simulationId, HttpRequestSpec spec) {
        SPECS.put(simulationId, spec);
    }

    public static HttpRequestSpec get(String simulationId) {
        return SPECS.get(simulationId);
    }

    public static void remove(String simulationId) {
        SPECS.remove(simulationId);
    }
}
