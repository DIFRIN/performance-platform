package com.performance.platform.transport.tls;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.config.HttpListenerProperties;
import com.performance.platform.transport.config.HttpTransportProperties;
import com.performance.platform.transport.http.HttpExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ssl.SslBundle;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransportTlsIT {

    private static final HttpTransportProperties HTTP = new HttpTransportProperties("ALL_CAPABLE", 5, 5, "/transport");

    @ParameterizedTest
    @ValueSource(strings = {"PEM", "PKCS12", "PLAIN"})
    void httpDeliversTasksSignalsResultsAndLifecycleOnDedicatedListener(String format) throws Exception {
        try (var pair = httpPair(format)) {
            verifyDelivery(pair.orchestrator(), pair.agent());
        }
    }

    @Test
    void httpRejectsMissingAndUntrustedClientCertificatesAndWrongHostname() throws Exception {
        try (var pair = httpPair("PEM")) {
            String url = pair.orchestrator().callbackUrl() + "/events";
            for (String identity : new String[] {null, "rogue"}) {
                try (var client = client(TlsTestCertificates.pem(identity))) {
                    assertThatThrownBy(() -> client.send(post(url), HttpResponse.BodyHandlers.discarding()))
                            .isInstanceOf(IOException.class);
                }
            }
            try (var client = client(TlsTestCertificates.pem("agent"))) {
                assertThatThrownBy(() -> client.send(post(url.replace("localhost", "127.0.0.2")),
                        HttpResponse.BodyHandlers.discarding()))
                        .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
                assertThat(client.send(post(pair.orchestrator().callbackUrl() + "/index.html"),
                        HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
                assertThat(client.send(post(url), HttpResponse.BodyHandlers.discarding()).statusCode())
                        .isEqualTo(400);
            }
        }
    }

    @Test
    void failedHttpCommandIsNotReportedAsSuccessfulDispatch() throws Exception {
        try (var pair = httpPair("PEM")) {
            pair.agent().disconnect();
            assertThatThrownBy(() -> pair.orchestrator().dispatchTask(request()))
                    .isInstanceOf(TransportException.class).hasMessageContaining("Failed to dispatch");
        }
    }

    @Test
    void invalidIdentityAndImplicitTrustCannotEnableMutualTls() {
        assertThatThrownBy(() -> new TransportTls(TlsTestCertificates.pem(null), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("private key");
        assertThatThrownBy(() -> new TransportTls(SslBundle.systemDefault(), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("truststore");
        assertThatThrownBy(() -> new HttpExecutionTransport(HTTP, null,
                new HttpListenerProperties(true, "127.0.0.1", 8091, "http://agent:8091",
                        "https://orchestrator:8091"), tls("PEM", "agent"), "AGENT"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("https");
    }

    private static HttpPair httpPair(String format) throws IOException {
        var registry = mock(AgentRegistryPort.class);
        var orchestrator = new HttpExecutionTransport(HTTP, registry,
                new HttpListenerProperties(true, "0.0.0.0", 0, null, null),
                tls(format, "server"), "ORCHESTRATOR");
        orchestrator.connect();
        String scheme = format.equals("PLAIN") ? "http" : "https";
        int agentPort = freePort();
        var agent = new HttpExecutionTransport(HTTP, null,
                new HttpListenerProperties(true, "127.0.0.1", agentPort,
                        scheme + "://localhost:" + agentPort,
                        orchestrator.callbackUrl().replace("/transport", "")),
                tls(format, "agent"), "AGENT");
        try {
            agent.connect();
            var now = Instant.now();
            var descriptor = new AgentDescriptor(AgentId.generate(), "agent", "localhost", 0, agent.callbackUrl(),
                    Set.of("sample"), new AgentCapabilities(4, "1.0"), AgentState.IDLE,
                    now, now, Duration.ofMinutes(1));
            when(registry.findByTaskName("sample")).thenReturn(List.of(descriptor));
            when(registry.findAll()).thenReturn(List.of(descriptor));
            return new HttpPair(orchestrator, agent);
        } catch (RuntimeException e) {
            agent.disconnect();
            orchestrator.disconnect();
            throw e;
        }
    }

    public static void verifyDelivery(ExecutionTransport orchestrator, ExecutionTransport agent) {
        var tasks = new CopyOnWriteArrayList<TaskExecutionRequest>();
        var signals = new CopyOnWriteArrayList<AgentSignal>();
        var events = new CopyOnWriteArrayList<ExecutionEvent>();
        var lifecycle = new CopyOnWriteArrayList<AgentLifecycleEvent>();
        agent.receiveTask(tasks::add);
        agent.receiveSignal(signals::add);
        orchestrator.subscribe(events::add);
        orchestrator.subscribeAgentEvents(lifecycle::add);
        var request = request();
        orchestrator.dispatchTask(request);
        orchestrator.broadcastSignal(new ScenarioRestartSignal(
                SignalId.generate(), request.executionId(), "test", Instant.now()));
        agent.publishEvent(new ExecutionEvent(EventId.generate(), request.executionId(), request.id(),
                AgentId.of("agent"), ExecutionEvent.TASK_COMPLETED, Map.of("status", "SUCCESS"), Instant.now()));
        agent.publishAgentEvent(new AgentLifecycleEvent(EventId.generate(), AgentId.of("agent"),
                AgentLifecycleEvent.AGENT_HEARTBEAT, Map.of("state", "IDLE"), Instant.now()));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(tasks).hasSize(1);
            assertThat(tasks.getFirst().id()).isEqualTo(request.id());
            assertThat(tasks.getFirst().step().parameters()).containsEntry("queryParams", Map.of("id", 42));
            assertThat(signals).hasSize(1).first().isInstanceOf(ScenarioRestartSignal.class);
            assertThat(events).hasSize(1).first().extracting(ExecutionEvent::eventType)
                    .isEqualTo(ExecutionEvent.TASK_COMPLETED);
            assertThat(lifecycle).hasSize(1).first().extracting(AgentLifecycleEvent::eventType)
                    .isEqualTo(AgentLifecycleEvent.AGENT_HEARTBEAT);
        });
    }

    private static TaskExecutionRequest request() {
        var executionId = ExecutionId.generate();
        var step = new StepDefinition(TaskId.of("step"), "sample", Phase.INJECTION,
                Map.of("queryParams", Map.of("id", 42)), List.of(), List.of(),
                Duration.ofSeconds(5), RetryPolicy.defaults());
        return new TaskExecutionRequest(MessageId.generate(), executionId, step,
                new PartialExecutionContext(executionId, ScenarioId.of("scenario"), Map.of()),
                Instant.now(), RetryPolicy.defaults());
    }

    private static TransportTls tls(String format, String identity) {
        return switch (format) {
            case "PLAIN" -> TransportTls.disabled();
            case "PKCS12" -> new TransportTls(TlsTestCertificates.pkcs12(identity), true);
            default -> new TransportTls(TlsTestCertificates.pem(identity), true);
        };
    }

    private static HttpClient client(SslBundle bundle) {
        return HttpClient.newBuilder().sslContext(bundle.createSslContext())
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static HttpRequest post(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record HttpPair(HttpExecutionTransport orchestrator, HttpExecutionTransport agent) implements AutoCloseable {
        @Override
        public void close() {
            agent.disconnect();
            orchestrator.disconnect();
        }
    }
}
