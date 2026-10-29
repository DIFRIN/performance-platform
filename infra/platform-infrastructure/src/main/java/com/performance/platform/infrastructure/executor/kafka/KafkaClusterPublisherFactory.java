package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.application.ports.out.MessageChannel;
import com.performance.platform.application.ports.out.MessageChannelFactory;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Adapter of the {@link MessageChannelFactory} port backed by the {@code platform.kafka-clusters} registry. */
@Component
public class KafkaClusterPublisherFactory implements MessageChannelFactory {

    private final KafkaClusterRegistry registry;

    public KafkaClusterPublisherFactory(KafkaClusterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public MessageChannel open(String cluster, String logicalTopic, Duration timeout) {
        String topic = registry.resolveTopic(cluster, logicalTopic);
        var template = new KafkaTemplate<>(registry.producerFactory(cluster, timeout));
        return new TemplatePublisher(template, topic);
    }

    /** Builds the Kafka record (key, value, headers) shared with the kafka-producer preparation. */
    static ProducerRecord<String, String> record(String topic, String key, String value, Map<String, String> headers) {
        var record = new ProducerRecord<>(topic, key, value);
        headers.forEach((name, v) ->
                record.headers().add(new RecordHeader(name, v.getBytes(StandardCharsets.UTF_8))));
        return record;
    }

    private record TemplatePublisher(KafkaTemplate<String, String> template, String topic) implements MessageChannel {

        @Override
        public CompletableFuture<Void> publish(String key, String value, Map<String, String> headers) {
            return template.send(record(topic, key, value, headers)).thenApply(r -> null);
        }

        @Override
        public void close() {
            template.destroy();
        }
    }
}
