package com.performance.platform.infrastructure.executor.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.domain.template.DynamicValues;
import com.performance.platform.domain.template.DynamicValues.Context;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the request-shaping parameters of an {@code http-client} step into a concrete request.
 * Every part accepts the dynamic placeholders of {@link DynamicValues}:
 * <ul>
 *   <li>{@code path} — {@code /devices/{deviceId}/events} with {@code pathParams: {deviceId: "{index}"}},
 *       or directly {@code /devices/{index}}</li>
 *   <li>{@code queryParams} — name -> value (a list repeats the parameter)</li>
 *   <li>{@code headers} — name -> value</li>
 *   <li>{@code body} — a string, or a YAML map/list serialized as JSON (placeholders in every string)</li>
 * </ul>
 * Names and values go through URI-template variables so RestClient encodes them strictly.
 */
final class HttpRequestPreparer {

    static final String PARAM_PATH_PARAMS = "pathParams";
    static final String PARAM_HEADERS = "headers";

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^{}/?]+)}");
    private static final ObjectMapper JSON = new ObjectMapper();

    record Prepared(String uriTemplate, Map<String, Object> uriVariables,
                    Map<String, String> headers, String body) {
    }

    private HttpRequestPreparer() {
    }

    static Prepared prepare(String path, Map<String, Object> params, Context ctx) {
        Map<String, Object> variables = new LinkedHashMap<>();
        String uri = applyPathParams(path, params.get(PARAM_PATH_PARAMS), ctx, variables);
        uri = appendQueryParams(uri, params.get(HttpClientTaskExecutor.PARAM_QUERY_PARAMS), ctx, variables);
        return new Prepared(uri, variables, headers(params.get(PARAM_HEADERS), ctx),
                body(params.get(HttpClientTaskExecutor.PARAM_BODY), ctx));
    }

    private static String applyPathParams(String path, Object raw, Context ctx, Map<String, Object> variables) {
        Map<?, ?> pathParams = asMap(raw, PARAM_PATH_PARAMS);
        for (Map.Entry<?, ?> entry : pathParams.entrySet()) {
            String name = requireName(entry.getKey(), PARAM_PATH_PARAMS);
            if (DynamicValues.isDynamic("{" + name + "}")) {
                throw new IllegalArgumentException("Path param name '" + name + "' is a reserved placeholder");
            }
            variables.put(name, DynamicValues.resolve(String.valueOf(entry.getValue()), ctx));
        }
        String resolved = DynamicValues.resolve(path, ctx);
        Matcher m = PATH_VARIABLE.matcher(resolved);
        while (m.find()) {
            if (!variables.containsKey(m.group(1))) {
                throw new IllegalArgumentException("Path placeholder '{" + m.group(1)
                        + "}' has no matching '" + PARAM_PATH_PARAMS + "' entry");
            }
        }
        return resolved;
    }

    private static String appendQueryParams(String path, Object raw, Context ctx, Map<String, Object> variables) {
        Map<?, ?> queryParams = asMap(raw, HttpClientTaskExecutor.PARAM_QUERY_PARAMS);
        if (queryParams.isEmpty()) return path;

        StringBuilder uri = new StringBuilder(path);
        char separator = path.contains("?") ? '&' : '?';
        int index = 0;
        for (Map.Entry<?, ?> entry : queryParams.entrySet()) {
            String name = requireName(entry.getKey(), HttpClientTaskExecutor.PARAM_QUERY_PARAMS);
            List<?> values = entry.getValue() instanceof Collection<?> c
                    ? c.stream().filter(Objects::nonNull).toList()
                    : (entry.getValue() == null ? List.of("") : List.of(entry.getValue()));
            for (Object value : values) {
                String nameVar = "qpName" + index;
                String valueVar = "qpValue" + index;
                variables.put(nameVar, DynamicValues.resolve(name, ctx));
                variables.put(valueVar, DynamicValues.resolve(value.toString(), ctx));
                uri.append(separator).append('{').append(nameVar).append("}={").append(valueVar).append('}');
                separator = '&';
                index++;
            }
        }
        return uri.toString();
    }

    private static Map<String, String> headers(Object raw, Context ctx) {
        Map<String, String> headers = new LinkedHashMap<>();
        asMap(raw, PARAM_HEADERS).forEach((k, v) -> headers.put(requireName(k, PARAM_HEADERS),
                DynamicValues.resolve(String.valueOf(v), ctx)));
        return headers;
    }

    private static String body(Object raw, Context ctx) {
        if (raw == null) return null;
        if (raw instanceof String s) return DynamicValues.resolve(s, ctx);
        try {
            return JSON.writeValueAsString(DynamicValues.resolveDeep(raw, ctx));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Parameter 'body' cannot be serialized: " + e.getOriginalMessage());
        }
    }

    private static Map<?, ?> asMap(Object raw, String param) {
        if (raw == null) return Map.of();
        if (raw instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("Parameter '" + param + "' must be a map of name -> value");
    }

    private static String requireName(Object key, String param) {
        if (key == null || key.toString().isBlank()) {
            throw new IllegalArgumentException("Parameter '" + param + "' contains a blank name");
        }
        return key.toString();
    }
}
