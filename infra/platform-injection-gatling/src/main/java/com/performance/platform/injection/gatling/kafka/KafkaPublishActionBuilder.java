package com.performance.platform.injection.gatling.kafka;

import io.gatling.core.action.Action;
import io.gatling.core.action.builder.ActionBuilder;
import io.gatling.core.structure.ScenarioContext;

import com.performance.platform.injection.gatling.load.ShareCounter;

/** Scala-side builder of {@link KafkaPublishAction}; the message index is global to the run (1-based). */
final class KafkaPublishActionBuilder implements ActionBuilder {

    private final KafkaInjectionSpec spec;
    private final ShareCounter counter = ShareCounter.fromSystemProperties();

    KafkaPublishActionBuilder(KafkaInjectionSpec spec) {
        this.spec = spec;
    }

    @Override
    public Action build(ScenarioContext ctx, Action next) {
        return new KafkaPublishAction(spec.stepId(), spec, counter, ctx.coreComponents().statsEngine(),
                ctx.coreComponents().clock(), next);
    }
}
