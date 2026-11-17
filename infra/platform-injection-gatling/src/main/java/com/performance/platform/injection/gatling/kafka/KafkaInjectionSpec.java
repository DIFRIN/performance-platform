package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.injection.gatling.http.RequestMix;
import com.performance.platform.plugin.resource.MessageChannel;
import com.performance.platform.plugin.template.MessageTemplate;

/**
 * Everything the Gatling-instantiated {@link KafkaScenarioSimulation} needs to publish for one step:
 * a weighted mix of message templates (ADR-070; single template = mix of one named {@code default}).
 */
public record KafkaInjectionSpec(String stepId, RequestMix<MessageTemplate> mix, MessageChannel publisher) {

    public KafkaInjectionSpec(String stepId, MessageTemplate template, MessageChannel publisher) {
        this(stepId, RequestMix.single(template), publisher);
    }
}
