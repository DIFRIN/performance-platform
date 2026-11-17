package com.performance.platform.injection.gatling.kafka;

import io.gatling.core.action.Action;
import io.gatling.core.action.builder.ActionBuilder;
import io.gatling.core.structure.ScenarioContext;

import com.performance.platform.injection.gatling.http.RequestMix;
import com.performance.platform.injection.gatling.load.ShareCounter;

import java.util.random.RandomGenerator;

/** Scala-side builder of {@link KafkaPublishAction}; the message index is global to the run (1-based). */
final class KafkaPublishActionBuilder implements ActionBuilder {

    private final KafkaInjectionSpec spec;
    private final ShareCounter counter = ShareCounter.fromSystemProperties();
    private final RandomGenerator random = RequestMix.generator(seed());

    KafkaPublishActionBuilder(KafkaInjectionSpec spec) {
        this.spec = spec;
    }

    private static long seed() {
        String seed = System.getProperty(ShareCounter.PROP_SEED);
        return seed == null ? 0L : Long.parseUnsignedLong(seed);
    }

    @Override
    public Action build(ScenarioContext ctx, Action next) {
        return new KafkaPublishAction(spec.stepId(), spec, counter, random, ctx.coreComponents().statsEngine(),
                ctx.coreComponents().clock(), next);
    }
}
