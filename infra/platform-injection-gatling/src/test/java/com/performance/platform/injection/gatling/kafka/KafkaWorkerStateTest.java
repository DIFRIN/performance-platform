package com.performance.platform.injection.gatling.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.injection.gatling.worker.WorkerStates;
import com.performance.platform.plugin.resource.ChannelDescriptor;
import com.performance.platform.plugin.resource.ChannelOpener;
import com.performance.platform.plugin.resource.MessageChannel;
import com.performance.platform.plugin.resource.MessageChannelFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaWorkerStateTest {

    static final List<ChannelDescriptor> OPENED = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Declared in META-INF/services (test resources): stands for the Kafka opener of the capability module. */
    public static final class FakeOpener implements ChannelOpener {
        @Override public String type() { return "fake"; }
        @Override public MessageChannel open(ChannelDescriptor d, Duration timeout) {
            OPENED.add(d);
            return new MessageChannel() {
                @Override public CompletableFuture<Void> publish(String k, String v, Map<String, String> h) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public void close() { }
            };
        }
    }

    private static final class Factory implements MessageChannelFactory {
        @Override public MessageChannel open(String target, String destination, Duration timeout) {
            return new FakeOpener().open(describe(target, destination), timeout);
        }
        @Override public ChannelDescriptor describe(String target, String destination) {
            return new ChannelDescriptor("fake", Map.of("bootstrapServers", "k:9092", "topic", "phys-" + destination));
        }
    }

    @Test
    void mixAndChannelSurviveJsonIntoWorkerHolders() throws Exception {
        var protocol = new KafkaInjectionProtocol(new Factory());
        Map<String, Object> params = Map.of("cluster", "c", "topic", "events", "requests", List.of(
                Map.of("name", "a", "weight", 70, "payload", "A-{index}"),
                Map.of("name", "b", "weight", 30, "payload", "B-{index}")));
        String id = "kstep";
        OPENED.clear();
        try (var prepared = protocol.prepare("exec", id, params)) {
            var json = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> state = json.readValue(json.writeValueAsString(prepared.workerState()), Map.class);
            KafkaInjectionSpecHolder.remove(id); // simule la JVM fille : holder vide
            OPENED.clear();

            WorkerStates.restore(id, state);

            var spec = KafkaInjectionSpecHolder.get(id);
            assertThat(spec.mix().requests()).extracting(r -> r.name()).containsExactly("a", "b");
            assertThat(spec.mix().normalizedWeight("a")).isEqualTo(0.7);
            assertThat(spec.mix().requests().getFirst().spec().render(3).value()).isEqualTo("A-3");
            assertThat(OPENED).singleElement().satisfies(d ->
                    assertThat(d.settings()).containsEntry("topic", "phys-events"));
        } finally {
            KafkaInjectionSpecHolder.remove(id);
        }
    }
}
