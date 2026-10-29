package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.domain.template.MessageTemplate;

import com.typesafe.scalalogging.Logger;
import io.gatling.commons.stats.KO$;
import io.gatling.commons.stats.OK$;
import io.gatling.commons.stats.Status;
import io.gatling.commons.util.Clock;
import io.gatling.core.action.Action;
import io.gatling.core.session.Session;
import io.gatling.core.stats.StatsEngine;
import scala.Option;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gatling action publishing one message per virtual user. The publication is asynchronous (the
 * Gatling thread is never blocked) and its latency is reported to Gatling's {@link StatsEngine}
 * under the step id, so results/assertions are the same as for an HTTP request.
 */
final class KafkaPublishAction implements Action {

    private Logger logger = Logger.apply(KafkaPublishAction.class.getName());

    private final String requestName;
    private final KafkaInjectionSpec spec;
    private final AtomicLong counter;
    private final StatsEngine stats;
    private final Clock clock;
    private final Action next;

    KafkaPublishAction(String requestName, KafkaInjectionSpec spec, AtomicLong counter, StatsEngine stats,
                       Clock clock, Action next) {
        this.requestName = requestName;
        this.spec = spec;
        this.counter = counter;
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
        MessageTemplate.Rendered message;
        try {
            message = spec.template().render(counter.incrementAndGet());
        } catch (RuntimeException e) {
            complete(session, start, false, "render: " + e.getMessage());
            return;
        }
        try {
            spec.publisher().publish(message.key(), message.value(), message.headers())
                    .whenComplete((ok, error) -> complete(session, start, error == null,
                            error == null ? null : rootMessage(error)));
        } catch (RuntimeException e) {
            complete(session, start, false, "publish: " + e.getMessage());
        }
    }

    private void complete(Session session, long start, boolean success, String error) {
        long end = clock.nowMillis();
        Status status = success ? OK$.MODULE$ : KO$.MODULE$;
        stats.logResponse(session.scenario(), session.groups(), requestName, start, end, status,
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
