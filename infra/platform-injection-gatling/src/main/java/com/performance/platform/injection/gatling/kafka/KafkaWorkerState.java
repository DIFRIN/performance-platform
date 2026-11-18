package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.injection.gatling.http.RequestMix;
import com.performance.platform.injection.gatling.http.WeightedRequest;
import com.performance.platform.injection.gatling.worker.WorkerStateRestorer;
import com.performance.platform.injection.gatling.worker.WorkerStates;
import com.performance.platform.plugin.resource.ChannelDescriptor;
import com.performance.platform.plugin.resource.ChannelOpener;
import com.performance.platform.plugin.template.MessageTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Kafka state of a step for the worker JVM (ADR-071): the declared requests (re-parsed there into
 * {@link MessageTemplate}s) and a {@link ChannelDescriptor} from which the worker opens its own producer.
 */
public final class KafkaWorkerState implements WorkerStateRestorer {

    public static final String TYPE = "kafka";
    static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(30);

    /** A declared request: name, weight and its raw (JSON-safe) parameters. */
    public record RawRequest(String name, double weight, Map<String, Object> values) {}

    /** {@code requests} = weighted mix of templates (ADR-070); absent = one request named {@code default}. */
    static List<RawRequest> rawRequests(Map<String, Object> params) {
        Object raw = params.get("requests");
        if (raw == null) {
            return List.of(new RawRequest(RequestMix.DEFAULT_NAME, 1.0, params));
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'requests' must be a non-empty list");
        }
        List<RawRequest> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("Each 'requests' entry must be a mapping");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            entry.forEach((k, v) -> values.put(String.valueOf(k), v));
            Object name = values.get("name");
            Object weight = values.get("weight");
            if (name == null || !(weight instanceof Number w)) {
                throw new IllegalArgumentException("Each 'requests' entry needs 'name' and numeric 'weight'");
            }
            out.add(new RawRequest(name.toString(), w.doubleValue(), values));
        }
        return out;
    }

    static RequestMix<MessageTemplate> mix(String executionId, String stepId, List<RawRequest> raw) {
        if (raw.size() == 1 && RequestMix.DEFAULT_NAME.equals(raw.getFirst().name())) {
            return RequestMix.single(MessageTemplate.of(executionId, stepId, raw.getFirst().values()));
        }
        List<WeightedRequest<MessageTemplate>> requests = new ArrayList<>();
        for (RawRequest r : raw) {
            requests.add(new WeightedRequest<>(r.name(), r.weight(),
                    MessageTemplate.of(executionId, stepId, r.values())));
        }
        return new RequestMix<>(requests);
    }

    public static Map<String, Object> toState(String executionId, String stepId, List<RawRequest> raw,
                                              ChannelDescriptor channel) {
        List<Object> requests = new ArrayList<>();
        for (RawRequest r : raw) {
            var e = new LinkedHashMap<String, Object>();
            e.put("name", r.name());
            e.put("weight", r.weight());
            e.put("values", r.values());
            requests.add(e);
        }
        var data = new LinkedHashMap<String, Object>();
        data.put("executionId", executionId);
        data.put("stepId", stepId);
        data.put("requests", requests);
        data.put("channel", Map.of("type", channel.type(), "settings", channel.settings()));
        return WorkerStates.envelope(TYPE, data);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(String simulationId, Map<String, Object> data) {
        String executionId = (String) data.get("executionId");
        String stepId = (String) data.get("stepId");
        List<RawRequest> raw = new ArrayList<>();
        for (Object o : (List<Object>) data.get("requests")) {
            var e = (Map<String, Object>) o;
            raw.add(new RawRequest((String) e.get("name"), ((Number) e.get("weight")).doubleValue(),
                    (Map<String, Object>) e.get("values")));
        }
        var ch = (Map<String, Object>) data.get("channel");
        var descriptor = new ChannelDescriptor((String) ch.get("type"), (Map<String, String>) ch.get("settings"));
        var opener = ServiceLoader.load(ChannelOpener.class).stream().map(ServiceLoader.Provider::get)
                .filter(o -> o.type().equals(descriptor.type())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No ChannelOpener for type '" + descriptor.type() + "'"));
        var channel = opener.open(descriptor, PUBLISH_TIMEOUT);
        Runtime.getRuntime().addShutdownHook(new Thread(channel::close, "kafka-channel-close"));
        KafkaInjectionSpecHolder.set(simulationId, new KafkaInjectionSpec(stepId, mix(executionId, stepId, raw), channel));
    }
}
