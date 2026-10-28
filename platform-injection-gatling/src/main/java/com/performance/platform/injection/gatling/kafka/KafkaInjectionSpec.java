package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.application.ports.out.MessageChannel;
import com.performance.platform.domain.template.MessageTemplate;

/** Everything the Gatling-instantiated {@link KafkaScenarioSimulation} needs to publish for one step. */
public record KafkaInjectionSpec(String stepId, MessageTemplate template, MessageChannel publisher) {
}
