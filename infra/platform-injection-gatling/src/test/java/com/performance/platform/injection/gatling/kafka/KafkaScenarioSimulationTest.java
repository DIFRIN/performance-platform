package com.performance.platform.injection.gatling.kafka;

import com.performance.platform.application.ports.out.MessageChannel;
import com.performance.platform.application.ports.out.MessageChannelFactory;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.injection.gatling.load.DefaultLoadModelTranslator;
import com.performance.platform.injection.gatling.protocol.PreparedInjection;
import com.performance.platform.injection.gatling.result.DefaultGatlingResultParser;
import com.performance.platform.injection.gatling.runner.DefaultGatlingRunner;
import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaScenarioSimulationTest {

    private record Sent(String key, String value, Map<String, String> headers) {
    }

    private static final class FakeFactory implements MessageChannelFactory {
        final List<Sent> sent = new CopyOnWriteArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean();
        String openedCluster;
        String openedTopic;
        boolean failPublishing;

        @Override
        public MessageChannel open(String cluster, String logicalTopic, Duration timeout) {
            openedCluster = cluster;
            openedTopic = logicalTopic;
            return new MessageChannel() {
                @Override
                public CompletableFuture<Void> publish(String key, String value, Map<String, String> headers) {
                    sent.add(new Sent(key, value, headers));
                    return CompletableFuture.runAsync(() -> {
                        if (failPublishing) throw new RuntimeException("broker down");
                    }, CompletableFuture.delayedExecutor(30, TimeUnit.MILLISECONDS));
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            };
        }
    }

    @TempDir
    Path resultsDir;

    private static Map<String, Object> params() {
        return Map.of("cluster", "iot", "topic", "device-events",
                "key", "dev-{index:3}",
                "headers", Map.of("x-run", "{executionId}"),
                "payload", Map.of("id", "{index}"));
    }

    /** Runs the generated simulation and returns the REQUEST lines of Gatling's simulation.log. */
    private List<String> run(FakeFactory factory, String simId) throws Exception {
        PreparedInjection prepared = new KafkaInjectionProtocol(factory).prepare("exec-1", simId, params());
        try {
            var config = new GatlingRunConfig(prepared.simulationClass(),
                    new LoadModel(LoadModelType.CONSTANT, Map.of("usersPerSec", 5, "durationSeconds", 1)),
                    Map.of(), resultsDir, simId, Duration.ofSeconds(60));
            Path dir = new DefaultGatlingRunner(new DefaultLoadModelTranslator()).run(config);
            assertThat(dir).isNotEqualTo(resultsDir.toAbsolutePath().normalize());
            Path log = dir.resolve("simulation.log");
            assertThat(log).isRegularFile();
            List<String> requests = Files.readAllLines(log).stream()
                    .filter(l -> l.startsWith("REQUEST")).toList();
            var result = new DefaultGatlingResultParser().parse(dir, TaskId.of(simId));
            assertThat(result.totalRequests()).isEqualTo(5).isEqualTo(requests.size());
            assertThat(result.successfulRequests()).isEqualTo(factory.failPublishing ? 0 : 5);
            assertThat(result.failedRequests()).isEqualTo(factory.failPublishing ? 5 : 0);
            assertThat(result.errorRate()).isEqualTo(factory.failPublishing ? 100.0 : 0.0);
            assertThat(result.minMs()).isGreaterThanOrEqualTo(25);
            long[] latencies = requests.stream().map(l -> l.split("\t", -1))
                    .mapToLong(f -> Long.parseLong(f[4]) - Long.parseLong(f[3])).sorted().toArray();
            assertThat(result.minMs()).isEqualTo(latencies[0]);
            assertThat(result.maxMs()).isEqualTo(latencies[4]);
            assertThat(result.meanMs()).isEqualTo(java.util.Arrays.stream(latencies).average().orElseThrow());
            assertThat(result.p50Ms()).isEqualTo(latencies[2]);
            assertThat(result.p75Ms()).isEqualTo(latencies[3]);
            assertThat(result.p90Ms()).isEqualTo(latencies[4]);
            assertThat(result.p95Ms()).isEqualTo(latencies[4]);
            assertThat(result.p99Ms()).isEqualTo(latencies[4]);
            assertThat(result.duration()).isPositive();
            assertThat(result.throughput()).isEqualTo(5000.0 / result.duration().toMillis());
            assertThat(result.simulationClass()).isEqualTo(KafkaScenarioSimulation.class.getName());
            assertThat(result.rawStats()).containsKey("numberOfRequests");
            return requests;
        } finally {
            prepared.close();
        }
    }

    @Test
    @DisplayName("publishes one dynamic message per virtual user and reports it to Gatling")
    void shouldPublishDynamicMessagesAndReportStats() throws Exception {
        var factory = new FakeFactory();

        String simId = "kafka-sim-" + System.nanoTime();
        List<String> requests = run(factory, simId);

        assertThat(factory.openedCluster).isEqualTo("iot");
        assertThat(factory.openedTopic).isEqualTo("device-events");
        assertThat(factory.sent).hasSize(5).hasSize(requests.size());
        assertThat(requests).allSatisfy(l -> assertThat(l).contains(simId).contains("\tOK\t"));
        assertThat(factory.sent).extracting(Sent::key).doesNotHaveDuplicates()
                .allMatch(k -> k.matches("dev-\\d{3}"));
        assertThat(factory.sent).allSatisfy(s -> {
            assertThat(s.headers()).containsEntry("x-run", "exec-1");
            assertThat(s.value()).matches("\\{\"id\":\"\\d+\"}");
        });
        assertThat(factory.closed).isTrue();
    }

    @Test
    @DisplayName("counts publication failures as KO requests without stopping the run")
    void shouldReportFailuresAsKo() throws Exception {
        var factory = new FakeFactory();
        factory.failPublishing = true;
        String simId = "kafka-ko-" + System.nanoTime();
        List<String> requests = run(factory, simId);

        assertThat(requests).isNotEmpty().allSatisfy(l -> assertThat(l).contains("KO").contains("broker down"));
    }

    @Test
    @DisplayName("rejects missing cluster/topic, missing factory and bad templates at prepare time")
    void shouldValidateAtPrepare() {
        var factory = new FakeFactory();
        assertThatThrownBy(() -> new KafkaInjectionProtocol(factory).prepare("e", "s", Map.of("cluster", "c")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topic");
        assertThatThrownBy(() -> new KafkaInjectionProtocol((MessageChannelFactory) null)
                .prepare("e", "s", Map.of("cluster", "c", "topic", "t")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kafka-clusters");
        assertThatThrownBy(() -> new KafkaInjectionProtocol(factory)
                .prepare("e", "s", Map.of("cluster", "c", "topic", "t", "key", "{random:9-1}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(factory.openedCluster).isNull();
    }
}
