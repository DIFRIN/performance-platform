package com.performance.platform.injection.gatling.http;

import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.injection.gatling.result.DefaultGatlingResultParser;
import com.performance.platform.injection.gatling.load.DefaultLoadModelTranslator;
import com.performance.platform.injection.gatling.runner.DefaultGatlingRunner;
import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpScenarioSimulationTest {

    private record Received(String method, String uri, String requestId, String body) {
    }

    @TempDir
    Path resultsDir;

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("X-Request-Id"), body));
            LockSupport.parkNanos(Duration.ofMillis(30).toNanos());
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpRequestSpec spec(String body) {
        return HttpRequestSpecFactory.create("exec-1", "sim-1", Map.of(
                "baseUrl", "http://localhost:" + server.getAddress().getPort(),
                "method", "POST",
                "path", "/api/devices/{deviceId}/events",
                "pathParams", Map.of("deviceId", "device-{index:3}"),
                "queryParams", Map.of("run", "{executionId}", "n", "{index}"),
                "headers", Map.of("X-Request-Id", "req-{index}"),
                "body", body,
                "expectedStatus", 201), null);
    }

    @Test
    @DisplayName("sends path, query, headers and body rendered per request with an incrementing index")
    void shouldSendDynamicRequests() {
        String simId = "http-sim-" + System.nanoTime();
        HttpRequestSpecHolder.set(simId, spec("{\"id\":\"{index}\"}"));
        var config = new GatlingRunConfig(HttpScenarioSimulation.class.getName(),
                new LoadModel(LoadModelType.CONSTANT, Map.of("usersPerSec", 4, "durationSeconds", 1)),
                Map.of(), resultsDir, simId, Duration.ofSeconds(60));
        try {
            Path directory = new DefaultGatlingRunner(new DefaultLoadModelTranslator()).run(config);
            assertThat(directory).isNotEqualTo(resultsDir.toAbsolutePath().normalize());
            assertThat(directory.resolve("simulation.log")).isRegularFile();
            assertThat(Files.exists(directory.resolve("stats.json"))).isFalse();
            var result = new DefaultGatlingResultParser().parse(directory, TaskId.of("http-ok"));
            assertThat(result.totalRequests()).isEqualTo(4);
            assertThat(result.successfulRequests()).isEqualTo(4);
            assertThat(result.failedRequests()).isZero();
            assertThat(result.errorRate()).isZero();
            assertThat(result.minMs()).isGreaterThanOrEqualTo(25);
            assertThat(result.p50Ms()).isBetween(result.minMs(), result.maxMs());
            assertThat(result.meanMs()).isBetween((double) result.minMs(), (double) result.maxMs());
            assertThat(result.duration()).isPositive();
            assertThat(result.throughput()).isEqualTo(4000.0 / result.duration().toMillis());
        } finally {
            HttpRequestSpecHolder.remove(simId);
        }

        assertThat(received).hasSize(4);
        assertThat(received).allSatisfy(r -> assertThat(r.method()).isEqualTo("POST"));
        assertThat(received).extracting(Received::uri).doesNotHaveDuplicates()
                .allMatch(u -> u.matches("/api/devices/device-\\d{3}/events\\?((run=exec-1&n=\\d+)|(n=\\d+&run=exec-1))"));
        for (Received r : received) {
            String index = r.uri().replaceAll(".*[?&]n=(\\d+).*", "$1");
            assertThat(r.requestId()).isEqualTo("req-" + index);
            assertThat(r.body()).isEqualTo("{\"id\":\"" + index + "\"}");
        }

    }

    @Test
    @DisplayName("runner output parses failed HTTP checks as measured KO requests")
    void shouldParseHttpFailures() {
        String simId = "http-ko-" + System.nanoTime();
        HttpRequestSpecHolder.set(simId, HttpRequestSpecFactory.create("e", simId, Map.of(
                "baseUrl", "http://localhost:" + server.getAddress().getPort(),
                "path", "/unexpected-status", "expectedStatus", 200), null));
        var config = new GatlingRunConfig(HttpScenarioSimulation.class.getName(),
                new LoadModel(LoadModelType.CONSTANT, Map.of("usersPerSec", 4, "durationSeconds", 1)),
                Map.of(), resultsDir, simId, Duration.ofSeconds(60));
        try {
            Path directory = new DefaultGatlingRunner(new DefaultLoadModelTranslator()).run(config);
            var result = new DefaultGatlingResultParser().parse(directory, TaskId.of("http-ko"));
            assertThat(result.totalRequests()).isEqualTo(4);
            assertThat(result.failedRequests()).isEqualTo(4);
            assertThat(result.successfulRequests()).isZero();
            assertThat(result.errorRate()).isEqualTo(100.0);
            assertThat(result.minMs()).isGreaterThanOrEqualTo(25);
            assertThat(result.p99Ms()).isEqualTo(result.maxMs());
            assertThat(result.throughput()).isPositive();
            assertThat(result.simulationClass()).isEqualTo(HttpScenarioSimulation.class.getName());
        } finally {
            HttpRequestSpecHolder.remove(simId);
        }
        assertThat(received).hasSize(4);
    }

    @Test
    @DisplayName("rejects a path variable without pathParams entry at spec creation")
    void shouldRejectMissingPathParam() {
        assertThatThrownBy(() -> HttpRequestSpecFactory.create("e", "s",
                Map.of("baseUrl", "http://localhost:1", "path", "/a/{id}"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pathParams");
    }

    @Test
    @DisplayName("renders encoded path and query values with the same index for every part")
    void shouldRenderConsistently() {
        var spec = HttpRequestSpecFactory.create("e", "s", Map.of(
                "baseUrl", "http://localhost:1",
                "path", "/d/{id}",
                "pathParams", Map.of("id", "a b-{index+10}"),
                "queryParams", Map.of("q", List.of("x&y", "{index}"))), null);

        var rendered = HttpRequestRenderer.render(spec, 5);

        assertThat(rendered.target()).isEqualTo("/d/a%20b-15?q=x%26y&q=5");
    }
}
