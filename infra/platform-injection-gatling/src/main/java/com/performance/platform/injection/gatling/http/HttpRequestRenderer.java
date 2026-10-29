package com.performance.platform.injection.gatling.http;

import com.performance.platform.domain.template.DynamicValues;
import com.performance.platform.domain.template.DynamicValues.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders an {@link HttpRequestSpec} into a concrete request for a given request index.
 * All parts of one request share the same {@code index}, so the same {@code {index}}
 * yields the same value in the path, query, headers and body.
 */
public final class HttpRequestRenderer {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^{}/?]+)}");

    /** @param target path + encoded query string, relative to the base URL */
    public record Rendered(String target, Map<String, String> headers, String body) {
    }

    private HttpRequestRenderer() {
    }

    public static Rendered render(HttpRequestSpec spec, long index) {
        Context ctx = new Context(spec.executionId(), spec.stepId(), () -> index);
        String target = withQuery(withPathParams(spec, ctx), spec, ctx);

        Map<String, String> headers = new LinkedHashMap<>();
        spec.headers().forEach((name, value) -> headers.put(name, DynamicValues.resolve(value, ctx)));
        String body = spec.body() == null ? null : DynamicValues.resolve(spec.body(), ctx);
        return new Rendered(target, headers, body);
    }

    private static String withPathParams(HttpRequestSpec spec, Context ctx) {
        Map<String, String> values = new LinkedHashMap<>();
        spec.pathParams().forEach((name, value) -> values.put(name, DynamicValues.resolve(value, ctx)));

        String path = DynamicValues.resolve(spec.path(), ctx);
        Matcher m = PATH_VARIABLE.matcher(path);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Path placeholder '{" + m.group(1)
                        + "}' has no matching 'pathParams' entry");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(encode(value)));
        }
        return m.appendTail(out).toString();
    }

    private static String withQuery(String path, HttpRequestSpec spec, Context ctx) {
        if (spec.queryParams().isEmpty()) return path;
        StringBuilder uri = new StringBuilder(path);
        char separator = path.contains("?") ? '&' : '?';
        for (Map.Entry<String, List<String>> entry : spec.queryParams().entrySet()) {
            for (String value : entry.getValue()) {
                uri.append(separator).append(encode(DynamicValues.resolve(entry.getKey(), ctx)))
                        .append('=').append(encode(DynamicValues.resolve(value, ctx)));
                separator = '&';
            }
        }
        return uri.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
