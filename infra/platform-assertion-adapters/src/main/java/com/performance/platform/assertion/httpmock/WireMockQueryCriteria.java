package com.performance.platform.assertion.httpmock;

import com.performance.platform.domain.template.DynamicValues;
import java.util.Collection;
import java.util.Map;


/**
 * Builds the WireMock request-criteria JSON used by {@code /__admin/requests/count}
 * from an optional {@code queryParams} map.
 * <p>
 * Static values produce {@code equalTo}; values containing any {@link DynamicValues}
 * placeholder produce a {@code matches} regex, so that {@code device-{index}} matches
 * {@code device-42} and {@code {random:1-9}} matches one digit.
 * {@code {executionId}} and {@code {stepId}} are substituted with their actual value.
 */
final class WireMockQueryCriteria {

    static final String EMPTY = "{}";

    private WireMockQueryCriteria() {
    }

    static String build(Object rawQueryParams, String executionId, String stepId) {
        if (rawQueryParams == null) return EMPTY;
        if (!(rawQueryParams instanceof Map<?, ?> queryParams)) {
            throw new IllegalArgumentException("Parameter 'queryParams' must be a map of name -> value");
        }
        if (queryParams.isEmpty()) return EMPTY;

        StringBuilder json = new StringBuilder("{\"queryParameters\":{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : queryParams.entrySet()) {
            if (entry.getKey() == null || entry.getKey().toString().isBlank()) {
                throw new IllegalArgumentException("Parameter 'queryParams' contains a blank name");
            }
            Object value = entry.getValue();
            // For a list, a request is counted when it carries the first listed value.
            if (value instanceof Collection<?> c) {
                value = c.stream().filter(java.util.Objects::nonNull).map(Object.class::cast)
                        .findFirst().orElse("");
            }
            String text = value == null ? "" : value.toString()
                    .replace("{executionId}", executionId)
                    .replace("{stepId}", stepId);
            if (!first) json.append(',');
            first = false;
            json.append(quote(entry.getKey().toString())).append(':').append(matcher(text));
        }
        return json.append("}}").toString();
    }

    private static String matcher(String value) {
        if (!DynamicValues.isDynamic(value)) {
            return "{\"equalTo\":" + quote(value) + "}";
        }
        return "{\"matches\":" + quote(DynamicValues.toRegex(value, DynamicValues.Context.of("", ""))) + "}";
    }
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
