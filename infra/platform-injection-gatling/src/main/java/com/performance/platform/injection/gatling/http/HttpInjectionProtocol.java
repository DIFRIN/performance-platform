package com.performance.platform.injection.gatling.http;

import com.performance.platform.plugin.resource.EndpointResolver;
import com.performance.platform.injection.gatling.protocol.InjectionProtocol;
import com.performance.platform.injection.gatling.protocol.PreparedInjection;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        RequestMix<HttpRequestSpec> mix = mix(executionId, stepId, params);
        HttpRequestSpecHolder.set(stepId, mix.requests().getFirst().spec());
        HttpRequestSpecHolder.setMix(stepId, mix);
        return new PreparedInjection() {
            @Override
            public Map<String, Object> workerState() {
                return HttpWorkerState.toState(mix);
            }

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

    /** {@code requests} = weighted mix (ADR-070); each entry overrides the step parameters. Absent = mix of one. */
    private RequestMix<HttpRequestSpec> mix(String executionId, String stepId, Map<String, Object> params) {
        Object raw = params.get("requests");
        if (raw == null) {
            return RequestMix.single(HttpRequestSpecFactory.create(executionId, stepId, params, resolver));
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'requests' must be a non-empty list");
        }
        Map<String, Object> base = new LinkedHashMap<>(params);
        base.remove("requests");
        List<WeightedRequest<HttpRequestSpec>> requests = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("Each 'requests' entry must be a mapping");
            }
            Map<String, Object> merged = new LinkedHashMap<>(base);
            entry.forEach((k, v) -> merged.put(String.valueOf(k), v));
            Object name = merged.remove("name");
            Object weight = merged.remove("weight");
            if (name == null || !(weight instanceof Number w)) {
                throw new IllegalArgumentException("Each 'requests' entry needs 'name' and numeric 'weight'");
            }
            requests.add(new WeightedRequest<>(name.toString(), w.doubleValue(),
                    HttpRequestSpecFactory.create(executionId, stepId, merged, resolver)));
        }
        return new RequestMix<>(requests);
    }
}
