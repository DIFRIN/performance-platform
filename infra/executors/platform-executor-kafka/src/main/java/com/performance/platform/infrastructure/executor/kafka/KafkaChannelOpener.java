package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.plugin.resource.ChannelDescriptor;
import com.performance.platform.plugin.resource.ChannelOpener;
import com.performance.platform.plugin.resource.MessageChannel;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

/** Rouvre un canal Kafka dans le worker d'injection depuis son descripteur (ADR-071). Enregistre via ServiceLoader. */
public final class KafkaChannelOpener implements ChannelOpener {

    public static final String TYPE = "kafka";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public MessageChannel open(ChannelDescriptor descriptor, Duration timeout) {
        var settings = descriptor.settings();
        var config = KafkaClusterRegistry.producerConfig(
                require(settings, "bootstrapServers"), settings.getOrDefault("acks", "all"), timeout);
        var template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, String>(config));
        return new KafkaClusterPublisherFactory.TemplatePublisher(template, require(settings, "topic"));
    }

    private static String require(java.util.Map<String, String> settings, String key) {
        String v = settings.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Kafka channel descriptor lacks '" + key + "'");
        }
        return v;
    }
}
