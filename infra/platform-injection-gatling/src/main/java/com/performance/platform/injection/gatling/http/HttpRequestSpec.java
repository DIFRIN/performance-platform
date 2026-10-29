package com.performance.platform.injection.gatling.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario-driven description of the HTTP request replayed by {@link HttpScenarioSimulation}.
 * Every textual part is a template: {@code com.performance.platform.domain.template.DynamicValues}
 * placeholders are resolved for each request sent.
 *
 * @param path        path template, may contain {@code {name}} variables bound by {@code pathParams}
 * @param queryParams name -> values (several values repeat the parameter)
 * @param headers     target default headers merged with the step headers (step wins)
 * @param body        body template (structured bodies are already serialized to JSON), or {@code null}
 * @param expectedStatus expected HTTP status, {@code 0} to keep the Gatling default (2xx/3xx)
 */
public record HttpRequestSpec(
        String executionId,
        String stepId,
        String method,
        String baseUrl,
        String path,
        Map<String, String> pathParams,
        Map<String, List<String>> queryParams,
        Map<String, String> headers,
        String body,
        int expectedStatus) {

    public HttpRequestSpec {
        pathParams = Collections.unmodifiableMap(new LinkedHashMap<>(pathParams));
        queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }
}
