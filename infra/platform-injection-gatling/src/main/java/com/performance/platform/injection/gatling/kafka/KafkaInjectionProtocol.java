package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.plugin.resource.MessageChannel;
import com.performance.platform.plugin.resource.MessageChannelFactory;
import com.performance.platform.injection.gatling.protocol.InjectionProtocol;
import com.performance.platform.injection.gatling.protocol.PreparedInjection;
import com.performance.platform.injection.gatling.http.RequestMix;
import com.performance.platform.injection.gatling.http.WeightedRequest;
import com.performance.platform.plugin.template.MessageTemplate;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code protocol: kafka} — Kafka injection described by the step: {@code cluster} (name in
 * {@code platform.kafka-clusters}), {@code topic} (logical name, mapped to the physical topic by
 * the cluster configuration), and the dynamic {@code key}, {@code headers}, {@code payload}
 * ({@link MessageTemplate}). Rate and duration come from the step's {@code loadModel}.
 */
@Component
public class KafkaInjectionProtocol implements InjectionProtocol {

    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(30);

    private final MessageChannelFactory factory;

    @Autowired
    public KafkaInjectionProtocol(ObjectProvider<MessageChannelFactory> factory) {
        this(factory.getIfAvailable());
    }

    /** @param factory may be {@code null} when no Kafka cluster is configured */
    public KafkaInjectionProtocol(MessageChannelFactory factory) {
        this.factory = factory;
    }

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public PreparedInjection prepare(String executionId, String stepId, Map<String, Object> params) {
        String cluster = text(params, "cluster");
        String topic = text(params, "topic");
        if (cluster == null || topic == null) {
            throw new IllegalArgumentException("Kafka injection requires parameters 'cluster' and 'topic'");
        }
        if (factory == null) {
            throw new IllegalArgumentException("No Kafka cluster configured (platform.kafka-clusters)");
        }
        RequestMix<MessageTemplate> mix = mix(executionId, stepId, params);
        MessageChannel publisher = factory.open(cluster, topic, PUBLISH_TIMEOUT);
        KafkaInjectionSpecHolder.set(stepId, new KafkaInjectionSpec(stepId, mix, publisher));
        return new PreparedInjection() {
            @Override
            public String simulationClass() {
                return KafkaScenarioSimulation.class.getName();
            }

            @Override
            public void close() {
                KafkaInjectionSpecHolder.remove(stepId);
                publisher.close();
            }
        };
    }

    /** {@code requests} list = weighted mix of templates; absent = single template (mix of one). */
    private static RequestMix<MessageTemplate> mix(String executionId, String stepId, Map<String, Object> params) {
        Object raw = params.get("requests");
        if (raw == null) {
            return RequestMix.single(MessageTemplate.of(executionId, stepId, params));
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'requests' must be a non-empty list");
        }
        List<WeightedRequest<MessageTemplate>> requests = new ArrayList<>();
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
            requests.add(new WeightedRequest<>(name.toString(), w.doubleValue(),
                    MessageTemplate.of(executionId, stepId, values)));
        }
        return new RequestMix<>(requests);
    }

    private static String text(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }
}
