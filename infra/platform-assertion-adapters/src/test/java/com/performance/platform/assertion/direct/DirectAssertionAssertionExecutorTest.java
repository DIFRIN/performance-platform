package com.performance.platform.assertion.direct;

import com.performance.platform.assertion.measure.DirectMeasurementResolver;
import com.performance.platform.plugin.resource.Endpoint;
import com.performance.platform.plugin.resource.EndpointResolver;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import com.sun.net.httpserver.HttpServer;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DirectAssertionAssertionExecutor")
class DirectAssertionAssertionExecutorTest {

    @TempDir
    Path workspace;

    private final DirectAssertionAssertionExecutor executor =
            new DirectAssertionAssertionExecutor(new DirectMeasurementResolver(emptyProvider()));

    @Test
    @DisplayName("file existence is asserted as a STATE metric")
    void fileExists() throws Exception {
        Path file = workspace.resolve("report.pdf");
        Files.writeString(file, "content");

        var step = step("report-file", Map.of(
                "metric", "filesystem.exists",
                "path", file.toString(),
                "expect", true,
                "description", Map.of("title", "Report produced",
                        "sli", "presence of the report file",
                        "slo", "the file exists at the end of the run")));

        TaskResult result = executor.execute(context(), step);
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(summary.checks()).hasSize(1);
        assertThat(summary.checks().get(0).metric().toString()).isEqualTo("filesystem.exists");
        assertThat(summary.slo().title()).isEqualTo("Report produced");
    }

    @Test
    @DisplayName("a missing expected file fails with a readable rationale")
    void missingFile() {
        var step = step("report-file", Map.of(
                "metric", "filesystem.exists",
                "path", workspace.resolve("absent.pdf").toString(),
                "expect", true));

        TaskResult result = executor.execute(context(), step);
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(summary.verdict()).isEqualTo(AssertionStatus.FAILED);
        assertThat(summary.checks().get(0).rationale()).isNotBlank();
    }

    @Test
    @DisplayName("an unknown metric is refused at evaluation, not silently ignored")
    void unknownMetric() {
        var step = step("bad", Map.of("metric", "unknown.metric", "operator", "GTE", "threshold", 1));

        TaskResult result = executor.execute(context(), step);
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(summary.verdict()).isEqualTo(AssertionStatus.ERROR);
        assertThat(summary.checks().get(0).rationale()).contains("Unknown metric");
    }

    @Test
    @DisplayName("an unknown named datasource is reported, not silently replaced")
    void unknownNamedDatasource() {
        var step = step("orders", Map.of(
                "metric", "database.scalar",
                "datasource", "customer-db",
                "query", "SELECT COUNT(*) FROM orders",
                "operator", "GTE",
                "threshold", 1));

        TaskResult result = executor.execute(context(), step);
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(summary.verdict()).isEqualTo(AssertionStatus.ERROR);
        assertThat(summary.checks().get(0).rationale()).contains("Unknown datasource 'customer-db'");
    }

    @Test
    @DisplayName("a named target is resolved through the EndpointResolver port")
    void namedTargetCountsRequests() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/__admin/requests/count", exchange -> {
            byte[] body = "{\"count\":7}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            var endpoint = new Endpoint("http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofSeconds(1), Map.of(), Map.of());
            var resolver = new DirectMeasurementResolver(emptyProvider(), endpointProvider(endpoint));
            var targetExecutor = new DirectAssertionAssertionExecutor(resolver);
            var step = step("mock-calls", Map.of(
                    "target", "wiremock",
                    "metric", "http.requests.matched",
                    "operator", "GTE",
                    "threshold", 1));

            TaskResult result = targetExecutor.execute(context(), step);
            AssertionSummary summary =
                    (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(summary.checks().get(0).actualValue()).isEqualTo(7.0);
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------

    private static ExecutionContext context() {
        return ExecutionContext.initial(ExecutionId.of("exec-1"), ScenarioId.of("scenario-1"));
    }

    private static StepDefinition step(String id, Map<String, Object> parameters) {
        return new StepDefinition(TaskId.of(id), "direct-assertion", Phase.ASSERTION,
                parameters, List.of(), List.of(), Duration.ofSeconds(30), null);
    }

    private static ObjectProvider<EndpointResolver> endpointProvider(Endpoint endpoint) {
        return new ObjectProvider<>() {
            @Override
            public EndpointResolver getObject() {
                return name -> endpoint;
            }

            @Override
            public EndpointResolver getObject(Object... args) {
                return name -> endpoint;
            }

            @Override
            public EndpointResolver getIfAvailable() {
                return name -> endpoint;
            }

            @Override
            public EndpointResolver getIfUnique() {
                return name -> endpoint;
            }
        };
    }

    private static ObjectProvider<DataSource> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public DataSource getObject() {
                return null;
            }

            @Override
            public DataSource getObject(Object... args) {
                return null;
            }

            @Override
            public DataSource getIfAvailable() {
                return null;
            }

            @Override
            public DataSource getIfUnique() {
                return null;
            }
        };
    }
}
