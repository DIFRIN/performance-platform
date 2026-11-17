package com.performance.platform.injection.gatling.http;

import com.performance.platform.injection.gatling.worker.WorkerStateRestorer;
import com.performance.platform.injection.gatling.worker.WorkerStates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serialises the HTTP request mix of a step for the worker JVM and restores it there. */
public final class HttpWorkerState implements WorkerStateRestorer {

    public static final String TYPE = "http";

    public static Map<String, Object> toState(RequestMix<HttpRequestSpec> mix) {
        List<Object> requests = new ArrayList<>();
        for (var r : mix.requests()) {
            var h = r.spec();
            var spec = new LinkedHashMap<String, Object>();
            spec.put("executionId", h.executionId());
            spec.put("stepId", h.stepId());
            spec.put("method", h.method());
            spec.put("baseUrl", h.baseUrl());
            spec.put("path", h.path());
            spec.put("pathParams", h.pathParams());
            spec.put("queryParams", h.queryParams());
            spec.put("headers", h.headers());
            spec.put("body", h.body());
            spec.put("expectedStatus", h.expectedStatus());
            var entry = new LinkedHashMap<String, Object>();
            entry.put("name", r.name());
            entry.put("weight", r.weight());
            entry.put("spec", spec);
            requests.add(entry);
        }
        return WorkerStates.envelope(TYPE, Map.of("requests", requests));
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(String simulationId, Map<String, Object> data) {
        List<WeightedRequest<HttpRequestSpec>> requests = new ArrayList<>();
        for (Object o : (List<Object>) data.get("requests")) {
            var e = (Map<String, Object>) o;
            var s = (Map<String, Object>) e.get("spec");
            var spec = new HttpRequestSpec((String) s.get("executionId"), (String) s.get("stepId"),
                    (String) s.get("method"), (String) s.get("baseUrl"), (String) s.get("path"),
                    (Map<String, String>) s.get("pathParams"), (Map<String, List<String>>) s.get("queryParams"),
                    (Map<String, String>) s.get("headers"), (String) s.get("body"),
                    ((Number) s.get("expectedStatus")).intValue());
            requests.add(new WeightedRequest<>((String) e.get("name"), ((Number) e.get("weight")).doubleValue(), spec));
        }
        var mix = new RequestMix<>(requests);
        HttpRequestSpecHolder.set(simulationId, requests.getFirst().spec());
        HttpRequestSpecHolder.setMix(simulationId, mix);
    }
}
