package com.performance.platform.injection.gatling.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.application.ports.out.Endpoint;
import com.performance.platform.application.ports.out.EndpointResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds an {@link HttpRequestSpec} from the parameters of a {@code gatling} step that has no
 * {@code simulation}: the injection is then an HTTP one described entirely by the scenario
 * ({@code target} / {@code baseUrl}, {@code method}, {@code path}, {@code pathParams},
 * {@code queryParams}, {@code headers}, {@code body}, {@code contentType}, {@code expectedStatus}).
 */
public final class HttpRequestSpecFactory {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final String DEFAULT_CONTENT_TYPE = "application/json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpRequestSpecFactory() {
    }

    /**
     * @param resolver resolves {@code target}; may be {@code null} when only {@code baseUrl} is supported
     * @throws IllegalArgumentException on any invalid or inconsistent parameter
     */
    public static HttpRequestSpec create(String executionId, String stepId, Map<String, Object> params,
                                         EndpointResolver resolver) {
        Endpoint target = resolveTarget(params, resolver);
        String baseUrl = target != null ? target.baseUrl() : text(params, "baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "HTTP injection requires parameter 'target' (or 'baseUrl') when 'simulation' is not set");
        }
        String method = Objects.requireNonNullElse(text(params, "method"), "GET").toUpperCase();
        if (!METHODS.contains(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        String path = text(params, "path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Required parameter 'path' is missing or blank");
        }
        if (target != null) path = target.paths().getOrDefault(path, path);

        Map<String, String> headers = new LinkedHashMap<>();
        if (target != null) headers.putAll(target.defaultHeaders());
        stringMap(params.get("headers"), "headers").forEach(headers::put);
        String body = body(params.get("body"));
        if (body != null && headers.keySet().stream().noneMatch("Content-Type"::equalsIgnoreCase)) {
            headers.put("Content-Type", Objects.requireNonNullElse(text(params, "contentType"), DEFAULT_CONTENT_TYPE));
        }

        var spec = new HttpRequestSpec(executionId, stepId, method, baseUrl, path,
                stringMap(params.get("pathParams"), "pathParams"), queryParams(params.get("queryParams")),
                headers, body, expectedStatus(params.get("expectedStatus")));
        HttpRequestRenderer.render(spec, 1);
        return spec;
    }

    private static Endpoint resolveTarget(Map<String, Object> params, EndpointResolver resolver) {
        String name = text(params, "target");
        if (name == null || name.isBlank()) return null;
        if (resolver == null) {
            throw new IllegalArgumentException("No HTTP target resolver available for target '" + name + "'");
        }
        Endpoint target = resolver.resolve(name);
        if (target == null) throw new IllegalArgumentException("Unknown HTTP target '" + name + "'");
        return target;
    }

    private static Map<String, List<String>> queryParams(Object raw) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : asMap(raw, "queryParams").entrySet()) {
            List<String> values = new ArrayList<>();
            if (e.getValue() instanceof Collection<?> c) {
                c.stream().filter(Objects::nonNull).forEach(v -> values.add(v.toString()));
            } else {
                values.add(e.getValue() == null ? "" : e.getValue().toString());
            }
            result.put(name(e.getKey(), "queryParams"), values);
        }
        return result;
    }

    private static Map<String, String> stringMap(Object raw, String param) {
        Map<String, String> result = new LinkedHashMap<>();
        asMap(raw, param).forEach((k, v) -> result.put(name(k, param), String.valueOf(v)));
        return result;
    }

    private static String body(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) return s;
        try {
            return JSON.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Parameter 'body' cannot be serialized: " + e.getOriginalMessage());
        }
    }

    private static int expectedStatus(Object raw) {
        if (raw == null) return 0;
        try {
            return raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter 'expectedStatus' must be an integer: " + raw);
        }
    }

    private static Map<?, ?> asMap(Object raw, String param) {
        if (raw == null) return Map.of();
        if (raw instanceof Map<?, ?> m) return m;
        throw new IllegalArgumentException("Parameter '" + param + "' must be a map of name -> value");
    }

    private static String name(Object key, String param) {
        if (key == null || key.toString().isBlank()) {
            throw new IllegalArgumentException("Parameter '" + param + "' contains a blank name");
        }
        return key.toString();
    }

    private static String text(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }
}
