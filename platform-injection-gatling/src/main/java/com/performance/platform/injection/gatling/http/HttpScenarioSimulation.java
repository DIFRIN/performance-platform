package com.performance.platform.injection.gatling.http;

import com.performance.platform.injection.gatling.runner.SimulationInjectionHolder;

import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Generic HTTP simulation used by the {@code gatling} step when no {@code simulation} class is
 * given. The request shape comes from the {@link HttpRequestSpec} published by the executor and
 * the load profile from {@link SimulationInjectionHolder}. Path, query, headers and body are
 * rendered for every request with a global 1-based {@code {index}}.
 */
@SuppressWarnings("this-escape") // Gatling requires setUp() during Simulation construction.
public class HttpScenarioSimulation extends Simulation {

    private static final String SESSION_KEY = "platform.request";
    private static final String SIM_ID_PROPERTY = "gatling.simulationId";

    {
        String simId = System.getProperty(SIM_ID_PROPERTY, "unknown");
        HttpRequestSpec spec = HttpRequestSpecHolder.get(simId);
        if (spec == null) {
            throw new IllegalStateException("No HTTP request spec registered for simulation '" + simId + "'");
        }
        List<OpenInjectionStep> steps = SimulationInjectionHolder.get(simId);
        if (steps.isEmpty()) {
            throw new IllegalStateException("No injection steps registered for simulation '" + simId + "'");
        }

        AtomicLong counter = new AtomicLong();
        HttpRequestActionBuilder request = http(spec.stepId())
                .httpRequest(spec.method(), s -> rendered(s).target());
        for (String header : spec.headers().keySet()) {
            request = request.header(header, s -> rendered(s).headers().get(header));
        }
        if (spec.body() != null) {
            request = request.body(StringBody(s -> rendered(s).body()));
        }
        if (spec.expectedStatus() > 0) {
            request = request.check(status().is(spec.expectedStatus()));
        }

        ScenarioBuilder scn = scenario(spec.stepId())
                .exec(s -> s.set(SESSION_KEY, HttpRequestRenderer.render(spec, counter.incrementAndGet())))
                .exec(request);
        HttpProtocolBuilder protocol = http.baseUrl(spec.baseUrl());

        setUp(scn.injectOpen(steps.toArray(new OpenInjectionStep[0]))).protocols(protocol);
    }

    private static HttpRequestRenderer.Rendered rendered(io.gatling.javaapi.core.Session session) {
        return session.get(SESSION_KEY);
    }
}
