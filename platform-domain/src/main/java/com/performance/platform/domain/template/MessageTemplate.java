package com.performance.platform.domain.template;

import com.performance.platform.domain.template.DynamicValues.Context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-neutral message description taken from a step ({@code key}, {@code headers},
 * {@code payload} / {@code messageTemplate}). Every part accepts the {@link DynamicValues}
 * placeholders and is rendered per message with a 1-based {@code {index}}. A map/list payload is
 * serialized as JSON after placeholder resolution of its string values.
 *
 * <p>Shared by every executor that emits messages (kafka-producer preparation, Gatling Kafka
 * injection) so that scenario syntax and rendering rules are identical everywhere.</p>
 */
public final class MessageTemplate {

    public static final String PARAM_PAYLOAD = "payload";
    public static final String PARAM_MESSAGE_TEMPLATE = "messageTemplate";
    public static final String PARAM_KEY = "key";
    public static final String PARAM_HEADERS = "headers";
    public static final String DEFAULT_PAYLOAD = "perf-message-{index}";
    public static final String DEFAULT_KEY = "msg-{index}";

    /** A fully rendered message. */
    public record Rendered(String key, String value, Map<String, String> headers) {
    }

    private final String executionId;
    private final String stepId;
    private final Object payload;
    private final String key;
    private final Map<String, String> headers;

    private MessageTemplate(String executionId, String stepId, Object payload, String key,
                            Map<String, String> headers) {
        this.executionId = executionId;
        this.stepId = stepId;
        this.payload = payload;
        this.key = key;
        this.headers = headers;
    }

    /** @throws IllegalArgumentException when a parameter is malformed or a placeholder is invalid */
    public static MessageTemplate of(String executionId, String stepId, Map<String, Object> params) {
        Object payload = params.get(PARAM_PAYLOAD);
        if (payload == null) payload = params.get(PARAM_MESSAGE_TEMPLATE);
        if (payload == null) payload = DEFAULT_PAYLOAD;
        Object key = params.get(PARAM_KEY);

        Map<String, String> headers = new LinkedHashMap<>();
        Object rawHeaders = params.get(PARAM_HEADERS);
        if (rawHeaders != null) {
            if (!(rawHeaders instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Parameter 'headers' must be a map of name -> value");
            }
            map.forEach((k, v) -> {
                if (k == null || k.toString().isBlank()) {
                    throw new IllegalArgumentException("Parameter 'headers' contains a blank name");
                }
                headers.put(k.toString(), String.valueOf(v));
            });
        }
        var template = new MessageTemplate(Objects.requireNonNull(executionId), Objects.requireNonNull(stepId),
                payload, key == null ? DEFAULT_KEY : key.toString(), headers);
        template.render(1);
        return template;
    }

    public Rendered render(long index) {
        Context ctx = new Context(executionId, stepId, () -> index);
        Map<String, String> renderedHeaders = new LinkedHashMap<>();
        headers.forEach((name, template) -> renderedHeaders.put(name, DynamicValues.resolve(template, ctx)));
        return new Rendered(DynamicValues.resolve(key, ctx), value(ctx), renderedHeaders);
    }

    private String value(Context ctx) {
        if (payload instanceof String s) return DynamicValues.resolve(s, ctx);
        return JsonText.write(DynamicValues.resolveDeep(payload, ctx));
    }
}
