package com.performance.platform.injection.gatling.http;

import com.performance.platform.application.ports.out.EndpointResolver;
import com.performance.platform.injection.gatling.protocol.InjectionProtocol;
import com.performance.platform.injection.gatling.protocol.PreparedInjection;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/** {@code protocol: http} — generic HTTP injection described by the step ({@link HttpRequestSpecFactory}). */
@Component
public class HttpInjectionProtocol implements InjectionProtocol {

    private final EndpointResolver resolver;

    @Autowired
    public HttpInjectionProtocol(ObjectProvider<EndpointResolver> resolver) {
        this(resolver.getIfAvailable());
    }

    /** @param resolver may be {@code null}: only {@code baseUrl} is then supported */
    public HttpInjectionProtocol(EndpointResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public PreparedInjection prepare(String executionId, String stepId, Map<String, Object> params) {
        HttpRequestSpecHolder.set(stepId, HttpRequestSpecFactory.create(executionId, stepId, params, resolver));
        return new PreparedInjection() {
            @Override
            public String simulationClass() {
                return HttpScenarioSimulation.class.getName();
            }

            @Override
            public void close() {
                HttpRequestSpecHolder.remove(stepId);
            }
        };
    }
}
