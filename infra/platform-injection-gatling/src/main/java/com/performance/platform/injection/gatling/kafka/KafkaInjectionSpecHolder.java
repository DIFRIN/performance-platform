package com.performance.platform.injection.gatling.kafka;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Hands the {@link KafkaInjectionSpec} of a step to {@link KafkaScenarioSimulation}, keyed by simulation id. */
public final class KafkaInjectionSpecHolder {

    private static final Map<String, KafkaInjectionSpec> SPECS = new ConcurrentHashMap<>();

    private KafkaInjectionSpecHolder() {
    }

    public static void set(String simulationId, KafkaInjectionSpec spec) {
        SPECS.put(simulationId, spec);
    }

    public static KafkaInjectionSpec get(String simulationId) {
        return SPECS.get(simulationId);
    }

    public static void remove(String simulationId) {
        SPECS.remove(simulationId);
    }
}
