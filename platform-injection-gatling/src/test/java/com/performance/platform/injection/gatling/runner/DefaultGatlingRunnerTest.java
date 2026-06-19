package com.performance.platform.injection.gatling.runner;

import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.injection.gatling.load.DefaultLoadModelTranslator;
import com.performance.platform.injection.gatling.load.LoadModelTranslator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultGatlingRunner")
class DefaultGatlingRunnerTest {

    @TempDir
    Path resultsDir;

    private HttpServer mockServer;
    private int mockServerPort;
    private String baseUrl;
    private LoadModelTranslator translator;
    private DefaultGatlingRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/ping", exchange -> {
            String response = "{\"status\":\"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        mockServer.start();
        mockServerPort = mockServer.getAddress().getPort();
        baseUrl = "http://localhost:" + mockServerPort;

        System.setProperty("test.baseUrl", baseUrl);

        translator = new DefaultLoadModelTranslator();
        runner = new DefaultGatlingRunner(translator);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop(0);
        System.clearProperty("test.baseUrl");
    }

    // === Fixture ===

    private GatlingRunConfig config(String simulationId) {
        return new GatlingRunConfig(
                "com.performance.platform.injection.gatling.runner.MinimalSimulation",
                new LoadModel(LoadModelType.CONSTANT, Map.of(
                        "usersPerSec", 1,
                        "durationSeconds", 1)),
                Map.of("test.baseUrl", baseUrl),
                resultsDir,
                simulationId,
                Duration.ofSeconds(30)
        );
    }

    // === Tests ===

    @Test
    @DisplayName("should run simulation and produce results directory")
    void shouldRunSimulationAndProduceResultsDirectory() throws Exception {
        String simId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        var config = config(simId);

        Path resultPath = runner.run(config);

        assertThat(resultPath).isNotNull();
        assertThat(Files.exists(resultPath)).isTrue();
        assertThat(Files.isDirectory(resultPath)).isTrue();
    }

    @Test
    @DisplayName("should produce non-empty results directory with Gatling output")
    void shouldProduceGatlingOutput() throws Exception {
        String simId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        var config = config(simId);

        Path resultPath = runner.run(config);

        assertThat(Files.exists(resultPath)).isTrue();
        assertThat(Files.isDirectory(resultPath)).isTrue();
        // Avec -nr, Gatling produit js/stats.json et des logs
        long fileCount = Files.list(resultPath).count();
        assertThat(fileCount).as("results directory should not be empty").isPositive();
    }

    @Test
    @DisplayName("should clean up simulation injection holder after run")
    void shouldCleanUpInjectionHolder() throws Exception {
        String simId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        var config = config(simId);

        runner.run(config);

        assertThat(SimulationInjectionHolder.get(simId)).isEmpty();
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should throw GatlingExecutionException on invalid simulation class")
        void shouldThrowOnInvalidSimulationClass() {
            var config = new GatlingRunConfig(
                    "com.nonexistent.FakeSimulation",
                    new LoadModel(LoadModelType.CONSTANT, Map.of(
                            "usersPerSec", 1,
                            "durationSeconds", 1)),
                    Map.of("test.baseUrl", baseUrl),
                    resultsDir,
                    "test-invalid-" + UUID.randomUUID().toString().substring(0, 8),
                    Duration.ofSeconds(10)
            );

            // Gatling fromArgs() leve une exception ou retourne exit code != 0
            // → DefaultGatlingRunner wrappe en GatlingExecutionException
            assertThatThrownBy(() -> runner.run(config))
                    .isInstanceOf(GatlingExecutionException.class);
        }
    }
}
