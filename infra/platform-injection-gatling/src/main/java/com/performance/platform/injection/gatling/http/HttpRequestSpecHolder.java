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

    private static final Map<String, RequestMix> MIXES = new ConcurrentHashMap<>();

    private HttpRequestSpecHolder() {
    }

    public static void set(String simulationId, HttpRequestSpec spec) {
        SPECS.put(simulationId, spec);
    }

    public static HttpRequestSpec get(String simulationId) {
        return SPECS.get(simulationId);
    }

    public static void setMix(String simulationId, RequestMix mix) {
        MIXES.put(simulationId, mix);
    }

    /** Registered mix, or the single-request mix ({@code default}) built from the spec. */
    public static RequestMix getMix(String simulationId) {
        RequestMix mix = MIXES.get(simulationId);
        if (mix != null) return mix;
        HttpRequestSpec spec = SPECS.get(simulationId);
        return spec == null ? null : RequestMix.single(spec);
    }

    public static void remove(String simulationId) {
        SPECS.remove(simulationId);
        MIXES.remove(simulationId);
    }
}
