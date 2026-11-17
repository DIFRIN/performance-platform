package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.plugin.template.MessageTemplate;

import com.typesafe.scalalogging.Logger;
import io.gatling.commons.stats.KO$;
import io.gatling.commons.stats.OK$;
import io.gatling.commons.stats.Status;
import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.session.Session;
import io.gatling.core.stats.StatsEngine;
import scala.Option;

import com.performance.platform.injection.gatling.http.RequestMix;
import com.performance.platform.injection.gatling.http.WeightedRequest;

import java.util.random.RandomGenerator;

import com.performance.platform.injection.gatling.load.ShareCounter;

/**
 * Gatling action publishing one message per virtual user. The publication is asynchronous (the
 * Gatling thread is never blocked) and its latency is reported to Gatling's {@link StatsEngine}
 * under the step id, so results/assertions are the same as for an HTTP request.
 */
final class KafkaPublishAction implements Action {

    private Logger logger = Logger.apply(KafkaPublishAction.class.getName());

    private final String requestName;
    private final KafkaInjectionSpec spec;
    private final ShareCounter counter;
    private final RandomGenerator random;
    private final StatsEngine stats;
    private final Clock clock;
    private final Action next;

    KafkaPublishAction(String requestName, KafkaInjectionSpec spec, ShareCounter counter, RandomGenerator random, StatsEngine stats,
                       Clock clock, Action next) {
        this.requestName = requestName;
        this.spec = spec;
        this.counter = counter;
        this.random = random;
        this.stats = stats;
        this.clock = clock;
        this.next = next;
    }

    @Override
    public String name() {
        return requestName;
    }

    @Override
    public void execute(Session session) {
        long start = clock.nowMillis();
        // One arrival = one message, drawn by weight; a single default template keeps the step id as name.
        var mix = spec.mix();
        WeightedRequest<MessageTemplate> chosen = mix.requests().size() == 1 ? mix.requests().get(0) : mix.pick(random);
        String name = mix.requests().size() == 1 && RequestMix.DEFAULT_NAME.equals(chosen.name())
                ? requestName : chosen.name();
        MessageTemplate.Rendered message;
        try {
            message = chosen.spec().render(counter.next());
        } catch (RuntimeException e) {
            complete(session, start, name, false, "render: " + e.getMessage());
            return;
        }
        try {
            spec.publisher().publish(message.key(), message.value(), message.headers())
                    .whenComplete((ok, error) -> complete(session, start, name, error == null,
                            error == null ? null : rootMessage(error)));
        } catch (RuntimeException e) {
            complete(session, start, name, false, "publish: " + e.getMessage());
        }
    }

    private void complete(Session session, long start, String name, boolean success, String error) {
        long end = clock.nowMillis();
        Status status = success ? OK$.MODULE$ : KO$.MODULE$;
        stats.logResponse(session.scenario(), session.groups(), name, start, end, status,
                Option.empty(), Option.apply(error));
        Session updated = session.logGroupRequestTimings(start, end);
        next.$bang(success ? updated : updated.markAsFailed());
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return String.valueOf(cause.getMessage());
    }

    @Override
    public void com$typesafe$scalalogging$StrictLogging$_setter_$logger_$eq(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Logger logger() {
        return logger;
    }
}
