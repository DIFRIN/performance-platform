package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.injection.gatling.runner.SimulationInjectionHolder;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.Simulation;

import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Generic Kafka simulation used by the {@code gatling} step with {@code protocol: kafka}: every
 * virtual user publishes one message built from the {@link KafkaInjectionSpec} published by the
 * executor; the load profile comes from {@link SimulationInjectionHolder}.
 */
@SuppressWarnings("this-escape") // Gatling requires setUp() during Simulation construction.
public class KafkaScenarioSimulation extends Simulation {

    private static final String SIM_ID_PROPERTY = "gatling.simulationId";

    {
        String simId = System.getProperty(SIM_ID_PROPERTY, "unknown");
        KafkaInjectionSpec spec = KafkaInjectionSpecHolder.get(simId);
        if (spec == null) {
            throw new IllegalStateException("No Kafka spec registered for simulation '" + simId + "'");
        }
        List<OpenInjectionStep> steps = SimulationInjectionHolder.get(simId);
        if (steps.isEmpty()) {
            throw new IllegalStateException("No injection steps registered for simulation '" + simId + "'");
        }

        var action = new KafkaPublishActionBuilder(spec);
        ChainBuilder publish = ChainBuilder.EMPTY.make(empty -> action.toChainBuilder());
        setUp(scenario(spec.stepId()).exec(publish).injectOpen(steps.toArray(new OpenInjectionStep[0])));
    }
}
