package com.performance.platform.infrastructure.executor.kafka;

import com.performance.platform.plugin.resource.ChannelOpener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaChannelOpenerTest {

    @Test
    void describeResolvesPhysicalTopicAndOpenerIsDiscoverable() {
        var registry = new KafkaClusterRegistry(Map.of("c",
                new KafkaClusterProperties("localhost:9999", "1", "g", Map.of("events", "phys.events"))));
        var factory = new KafkaClusterPublisherFactory(registry);

        var descriptor = factory.describe("c", "events");

        assertThat(descriptor.type()).isEqualTo("kafka");
        assertThat(descriptor.settings()).containsEntry("bootstrapServers", "localhost:9999")
                .containsEntry("acks", "1").containsEntry("topic", "phys.events");
        var opener = ServiceLoader.load(ChannelOpener.class).stream().map(ServiceLoader.Provider::get)
                .filter(o -> o.type().equals(descriptor.type())).findFirst().orElseThrow();
        try (var channel = opener.open(descriptor, Duration.ofSeconds(5))) {
            assertThat(channel).isNotNull();
        }
    }

    @Test
    void describeRejectsUnknownCluster() {
        var factory = new KafkaClusterPublisherFactory(new KafkaClusterRegistry(Map.of()));
        assertThatThrownBy(() -> factory.describe("nope", "t")).isInstanceOf(IllegalArgumentException.class);
    }
}
