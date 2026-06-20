package com.performance.platform.transport.http;


import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.*;
import com.performance.platform.transport.config.HttpTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HttpExecutionTransport")
class HttpExecutionTransportTest {

    private HttpServer mockAgentServer;
    private int agentPort;
    private List<byte[]> receivedBodies;
    private HttpTransportProperties props;
    private StubAgentRegistry registry;
    private HttpExecutionTransport transport;

    private ExecutionId executionId;
    private ScenarioId scenarioId;
    private MessageId messageId;
    private EventId eventId;
    private AgentId agentId;

    @BeforeEach
    void setUp() throws IOException {
        receivedBodies = new ArrayList<>();
        mockAgentServer = HttpServer.create(new InetSocketAddress(0), 0);

        // Handler that captures the request body and returns 202
        mockAgentServer.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBodies.add(body);
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });

        mockAgentServer.start();
        agentPort = mockAgentServer.getAddress().getPort();

        props = new HttpTransportProperties(
                "ALL_CAPABLE", 30, 120, "/api/callback");

        registry = new StubAgentRegistry();
        transport = new HttpExecutionTransport(props, registry);
        transport.connect();

        executionId = ExecutionId.generate();
        scenarioId = ScenarioId.of("scenario-1");
        messageId = MessageId.generate();
        eventId = EventId.generate();
        agentId = AgentId.generate();
    }

    @AfterEach
    void tearDown() {
        transport.disconnect();
        mockAgentServer.stop(0);
    }

    // === Fixtures ===

    private AgentDescriptor agentDescriptor(AgentId id, String httpCallbackUrl,
                                             Set<String> taskNames) {
        return new AgentDescriptor(
                id,
                "agent-" + id.value(),
                "localhost",
                0,
                httpCallbackUrl,
                taskNames,
                new AgentCapabilities(4, "1.0.0"),
                AgentState.IDLE,
                Instant.now(),
                Instant.now(),
                Duration.ofSeconds(30)
        );
    }

    private StepDefinition sampleStep(String taskName) {
        return new StepDefinition(
                TaskId.of("task-" + taskName),
                taskName,
                Phase.INJECTION,
                Map.of("key", "value"),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                        Duration.ofSeconds(5), Set.of(RuntimeException.class))
        );
    }

    private PartialExecutionContext sampleContext() {
        return new PartialExecutionContext(
                executionId,
                scenarioId,
                Map.of("task-0", Map.of("agent-1", "result"))
        );
    }

    private TaskExecutionRequest sampleRequest(String taskName) {
        return new TaskExecutionRequest(
                messageId,
                executionId,
                sampleStep(taskName),
                sampleContext(),
                Instant.now(),
                new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                        Duration.ofSeconds(5), Set.of(RuntimeException.class))
        );
    }

    private ExecutionEvent sampleEvent() {
        return new ExecutionEvent(
                eventId,
                executionId,
                messageId,
                agentId,
                ExecutionEvent.TASK_COMPLETED,
                Map.of("status", "SUCCESS"),
                Instant.now()
        );
    }

    private AgentSignal sampleSignal() {
        return new ScenarioRestartSignal(
                SignalId.generate(),
                executionId,
                "Test restart",
                Instant.now()
        );
    }

    // === Tests ===

    @Nested
    @DisplayName("Connection lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("should be connected after connect")
        void shouldBeConnectedAfterConnect() {
            assertThat(transport.isConnected()).isTrue();
        }

        @Test
        @DisplayName("should be disconnected after disconnect")
        void shouldBeDisconnectedAfterDisconnect() {
            transport.disconnect();
            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("should return HTTP type")
        void shouldReturnHttpType() {
            assertThat(transport.getType()).isEqualTo(TransportType.HTTP);
        }

        @Test
        @DisplayName("should reject dispatchTask when not connected")
        void shouldRejectDispatchTaskWhenNotConnected() {
            transport.disconnect();
            assertThatThrownBy(() -> transport.dispatchTask(sampleRequest("test")))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("transport is not connected");
        }

        @Test
        @DisplayName("should reject publishEvent when not connected")
        void shouldRejectPublishEventWhenNotConnected() {
            transport.disconnect();
            assertThatThrownBy(() -> transport.publishEvent(sampleEvent()))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("transport is not connected");
        }

        @Test
        @DisplayName("should reject broadcastSignal when not connected")
        void shouldRejectBroadcastSignalWhenNotConnected() {
            transport.disconnect();
            assertThatThrownBy(() -> transport.broadcastSignal(sampleSignal()))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("transport is not connected");
        }
    }

    @Nested
    @DisplayName("dispatchTask — POST to capable agents")
    class DispatchTask {

        @Test
        @DisplayName("should POST to ALL_CAPABLE agents with matching taskName")
        void shouldPostToAllCapableAgents() {
            String agentUrl = "http://localhost:" + agentPort + "/api/v1/tasks";
            AgentDescriptor agent1 = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("load-test"));
            AgentDescriptor agent2 = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("load-test"));
            registry.addAgent(agent1);
            registry.addAgent(agent2);

            var request = sampleRequest("load-test");
            transport.dispatchTask(request);

            assertThat(receivedBodies).hasSize(2);
        }

        @Test
        @DisplayName("should POST to FIRST_AVAILABLE agent only")
        void shouldPostToFirstAvailableAgentOnly() {
            String agentUrl = "http://localhost:" + agentPort + "/api/v1/tasks";
            AgentDescriptor agent1 = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("load-test"));
            AgentDescriptor agent2 = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("load-test"));
            registry.addAgent(agent1);
            registry.addAgent(agent2);

            // Créer un transport avec broadcastMode FIRST_AVAILABLE
            var firstProps = new HttpTransportProperties(
                    "FIRST_AVAILABLE", 30, 120, "/api/callback");
            var firstRegistry = new StubAgentRegistry();
            firstRegistry.addAgent(agent1);
            firstRegistry.addAgent(agent2);
            var firstTransport = new HttpExecutionTransport(firstProps, firstRegistry);
            firstTransport.connect();

            var request = sampleRequest("load-test");
            try {
                firstTransport.dispatchTask(request);
                assertThat(receivedBodies).hasSize(1);
            } finally {
                firstTransport.disconnect();
            }
        }

        @Test
        @DisplayName("should not POST when no capable agent found")
        void shouldNotPostWhenNoCapableAgentFound() {
            var request = sampleRequest("nonexistent-task");
            transport.dispatchTask(request);
            assertThat(receivedBodies).isEmpty();
        }

        @Test
        @DisplayName("should skip agents without httpCallbackUrl")
        void shouldSkipAgentsWithoutHttpCallbackUrl() {
            AgentDescriptor agentNoUrl = agentDescriptor(
                    AgentId.generate(), null, Set.of("load-test"));
            registry.addAgent(agentNoUrl);

            var request = sampleRequest("load-test");
            transport.dispatchTask(request);

            assertThat(receivedBodies).isEmpty();
        }

        @Test
        @DisplayName("should serialize request body as JSON")
        void shouldSerializeRequestBodyAsJson() throws Exception {
            String agentUrl = "http://localhost:" + agentPort + "/api/v1/tasks";
            AgentDescriptor agent = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("load-test"));
            registry.addAgent(agent);

            var request = sampleRequest("load-test");
            transport.dispatchTask(request);

            assertThat(receivedBodies).hasSize(1);
            String body = new String(receivedBodies.get(0));
            // Verify it's valid JSON and contains key fields
            assertThat(body).contains("\"taskName\"");
            assertThat(body).contains("load-test");
            assertThat(body).contains("\"executionId\"");
        }

        @Test
        @DisplayName("should reject null request in dispatchTask")
        void shouldRejectNullRequestInDispatchTask() {
            assertThatThrownBy(() -> transport.dispatchTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("request must not be null");
        }
    }

    @Nested
    @DisplayName("broadcastSignal — POST to all agents")
    class BroadcastSignal {

        @Test
        @DisplayName("should POST signal to all registered agents")
        void shouldPostSignalToAllRegisteredAgents() {
            String agentUrl = "http://localhost:" + agentPort + "/api/v1/signals";
            AgentDescriptor agent1 = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("task-a"));
            AgentDescriptor agent2 = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("task-b"));
            registry.addAgent(agent1);
            registry.addAgent(agent2);

            var signal = sampleSignal();
            transport.broadcastSignal(signal);

            assertThat(receivedBodies).hasSize(2);
        }

        @Test
        @DisplayName("should skip agents without httpCallbackUrl in signal broadcast")
        void shouldSkipAgentsWithoutUrlInSignalBroadcast() {
            AgentDescriptor agentNoUrl = agentDescriptor(
                    AgentId.generate(), null, Set.of("task-a"));
            registry.addAgent(agentNoUrl);

            var signal = sampleSignal();
            transport.broadcastSignal(signal);

            assertThat(receivedBodies).isEmpty();
        }

        @Test
        @DisplayName("should reject null signal in broadcastSignal")
        void shouldRejectNullSignalInBroadcastSignal() {
            assertThatThrownBy(() -> transport.broadcastSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("signal must not be null");
        }
    }

    @Nested
    @DisplayName("publishEvent — notify local subscribers")
    class PublishEvent {

        @Test
        @DisplayName("should deliver published event to subscriber")
        void shouldDeliverPublishedEventToSubscriber() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(received::add);

            var event = sampleEvent();
            transport.publishEvent(event);

            assertThat(received).containsExactly(event);
        }

        @Test
        @DisplayName("should deliver to all subscribers")
        void shouldDeliverToAllSubscribers() {
            var received1 = new CopyOnWriteArrayList<ExecutionEvent>();
            var received2 = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(received1::add);
            transport.subscribe(received2::add);

            var event = sampleEvent();
            transport.publishEvent(event);

            assertThat(received1).containsExactly(event);
            assertThat(received2).containsExactly(event);
        }

        @Test
        @DisplayName("should not deliver to cancelled subscription")
        void shouldNotDeliverToCancelledSubscription() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            var subscription = transport.subscribe(received::add);

            subscription.cancel();
            transport.publishEvent(sampleEvent());

            assertThat(received).isEmpty();
        }

        @Test
        @DisplayName("should reject null event in publishEvent")
        void shouldRejectNullEventInPublishEvent() {
            assertThatThrownBy(() -> transport.publishEvent(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("event must not be null");
        }

        @Test
        @DisplayName("should reject null handler in subscribe")
        void shouldRejectNullHandlerInSubscribe() {
            assertThatThrownBy(() -> transport.subscribe(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }
    }

    @Nested
    @DisplayName("publishAgentEvent — notify local subscribers")
    class PublishAgentEvent {

        @Test
        @DisplayName("should deliver published agent event to subscriber")
        void shouldDeliverPublishedAgentEventToSubscriber() {
            var received = new CopyOnWriteArrayList<AgentLifecycleEvent>();
            transport.subscribeAgentEvents(received::add);

            var event = new AgentLifecycleEvent(
                    eventId, agentId,
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    Map.of("host", "localhost"),
                    Instant.now());
            transport.publishAgentEvent(event);

            assertThat(received).containsExactly(event);
        }

        @Test
        @DisplayName("should reject null agent event")
        void shouldRejectNullAgentEvent() {
            assertThatThrownBy(() -> transport.publishAgentEvent(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("event must not be null");
        }

        @Test
        @DisplayName("should reject null handler in subscribeAgentEvents")
        void shouldRejectNullHandlerInSubscribeAgentEvents() {
            assertThatThrownBy(() -> transport.subscribeAgentEvents(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should not deliver to cancelled agent subscription")
        void shouldNotDeliverToCancelledAgentSubscription() {
            var received = new CopyOnWriteArrayList<AgentLifecycleEvent>();
            var subscription = transport.subscribeAgentEvents(received::add);
            subscription.cancel();

            var event = new AgentLifecycleEvent(
                    eventId, agentId,
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    Map.of("host", "localhost"),
                    Instant.now());
            transport.publishAgentEvent(event);

            assertThat(received).isEmpty();
        }
    }

    @Nested
    @DisplayName("Subscription cancellation")
    class SubscriptionCancellation {

        @Test
        @DisplayName("should be active by default")
        void shouldBeActiveByDefault() {
            var subscription = transport.subscribe(e -> {});
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("should be inactive after cancel")
        void shouldBeInactiveAfterCancel() {
            var subscription = transport.subscribe(e -> {});
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("cancel should be idempotent")
        void cancelShouldBeIdempotent() {
            var subscription = transport.subscribe(e -> {});
            subscription.cancel();
            subscription.cancel();
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("should stop receiving after cancel")
        void shouldStopReceivingAfterCancel() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            var subscription = transport.subscribe(received::add);

            transport.publishEvent(sampleEvent());
            assertThat(received).hasSize(1);

            subscription.cancel();
            transport.publishEvent(sampleEvent());
            assertThat(received).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Null checks for public methods")
    class NullChecks {

        @Test
        @DisplayName("should reject null handler in receiveTask")
        void shouldRejectNullHandlerInReceiveTask() {
            assertThatThrownBy(() -> transport.receiveTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null handler in receiveSignal")
        void shouldRejectNullHandlerInReceiveSignal() {
            assertThatThrownBy(() -> transport.receiveSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null handler in subscribe")
        void shouldRejectNullHandlerInSubscribe() {
            assertThatThrownBy(() -> transport.subscribe(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null handler in subscribeAgentEvents")
        void shouldRejectNullHandlerInSubscribeAgentEvents() {
            assertThatThrownBy(() -> transport.subscribeAgentEvents(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }
    }

    @Nested
    @DisplayName("Round-trip integration")
    class RoundTripIntegration {

        @Test
        @DisplayName("should deliver task to capable agent via HTTP POST")
        void shouldDeliverTaskToCapableAgentViaHttpPost() {
            String agentUrl = "http://localhost:" + agentPort + "/api/v1/tasks";
            AgentDescriptor agent = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("http-request"));
            registry.addAgent(agent);

            var request = sampleRequest("http-request");
            transport.dispatchTask(request);

            assertThat(receivedBodies).hasSize(1);
            String body = new String(receivedBodies.get(0));
            assertThat(body).contains("http-request");
            assertThat(body).doesNotContain("\"@type\"");
        }

        @Test
        @DisplayName("full round-trip: dispatchTask + publishEvent + subscribe")
        void fullRoundTrip() {
            var events = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(events::add);

            // Dispatch task to mock agent
            String agentUrl = "http://localhost:" + agentPort + "/api/v1/tasks";
            AgentDescriptor agent = agentDescriptor(
                    AgentId.generate(), agentUrl, Set.of("full-test"));
            registry.addAgent(agent);

            var request = sampleRequest("full-test");
            transport.dispatchTask(request);

            assertThat(receivedBodies).hasSize(1);

            // Simulate event coming back from agent (as controller would handle)
            var event = sampleEvent();
            transport.publishEvent(event);

            assertThat(events).containsExactly(event);
        }
    }

    // === Stub AgentRegistryPort ===

    static class StubAgentRegistry implements AgentRegistryPort {

        private final List<AgentDescriptor> agents = new ArrayList<>();

        void addAgent(AgentDescriptor agent) {
            agents.add(agent);
        }

        @Override
        public void onAgentRegistered(AgentDescriptor descriptor) {
            agents.add(descriptor);
        }

        @Override
        public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {
            // stub
        }

        @Override
        public void onAgentExpired(AgentId agentId) {
            agents.removeIf(a -> a.id().equals(agentId));
        }

        @Override
        public void onAgentDeregistered(AgentId agentId) {
            agents.removeIf(a -> a.id().equals(agentId));
        }

        @Override
        public List<AgentDescriptor> findByTaskName(String taskName) {
            return agents.stream()
                    .filter(a -> a.supportedTaskNames().contains(taskName))
                    .toList();
        }

        @Override
        public boolean hasAgentFor(String taskName) {
            return agents.stream().anyMatch(
                    a -> a.supportedTaskNames().contains(taskName));
        }

        @Override
        public Optional<AgentDescriptor> findById(AgentId agentId) {
            return agents.stream()
                    .filter(a -> a.id().equals(agentId))
                    .findFirst();
        }

        @Override
        public List<AgentDescriptor> findAll() {
            return List.copyOf(agents);
        }
    }
}
