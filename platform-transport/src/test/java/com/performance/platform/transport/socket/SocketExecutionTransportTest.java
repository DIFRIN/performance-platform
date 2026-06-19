package com.performance.platform.transport.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.*;
import com.performance.platform.transport.config.SocketTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@DisplayName("SocketExecutionTransport")
class SocketExecutionTransportTest {

    private int port;
    private SocketTransportProperties props;
    private SocketExecutionTransport orchestrator;
    private SocketExecutionTransport agent;
    private ObjectMapper objectMapper;

    private ExecutionId executionId;
    private ScenarioId scenarioId;
    private MessageId messageId;
    private EventId eventId;
    private AgentId agentId;

    @BeforeEach
    void setUp() throws IOException {
        // Trouver un port libre
        port = findFreePort();
        props = new SocketTransportProperties(
                "localhost", port, 10, true, 100);

        // Orchestrateur — bind en premier
        orchestrator = new SocketExecutionTransport(props);
        orchestrator.connect();

        // Agent — bind echoue (port deja pris), connecte en mode agent
        agent = new SocketExecutionTransport(props);
        agent.connect();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        executionId = ExecutionId.generate();
        scenarioId = ScenarioId.of("scenario-1");
        messageId = MessageId.generate();
        eventId = EventId.generate();
        agentId = AgentId.generate();
    }

    @AfterEach
    void tearDown() {
        if (agent != null && agent.isConnected()) {
            agent.disconnect();
        }
        if (orchestrator != null && orchestrator.isConnected()) {
            orchestrator.disconnect();
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    // === Fixtures ===

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
            assertThat(orchestrator.isConnected()).isTrue();
            assertThat(agent.isConnected()).isTrue();
        }

        @Test
        @DisplayName("should be disconnected after disconnect")
        void shouldBeDisconnectedAfterDisconnect() {
            agent.disconnect();
            assertThat(agent.isConnected()).isFalse();

            orchestrator.disconnect();
            assertThat(orchestrator.isConnected()).isFalse();
        }

        @Test
        @DisplayName("should return SOCKET type")
        void shouldReturnSocketType() {
            assertThat(orchestrator.getType()).isEqualTo(TransportType.SOCKET);
            assertThat(agent.getType()).isEqualTo(TransportType.SOCKET);
        }

        @Test
        @DisplayName("should reject dispatchTask when not connected")
        void shouldRejectDispatchTaskWhenNotConnected() {
            orchestrator.disconnect();
            assertThatThrownBy(() -> orchestrator.dispatchTask(sampleRequest("test")))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("transport is not connected");
        }

        @Test
        @DisplayName("should reject publishEvent when not connected")
        void shouldRejectPublishEventWhenNotConnected() {
            agent.disconnect();
            assertThatThrownBy(() -> agent.publishEvent(sampleEvent()))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("transport is not connected");
        }

        @Test
        @DisplayName("should reject broadcastSignal when not connected")
        void shouldRejectBroadcastSignalWhenNotConnected() {
            agent.disconnect();
            assertThatThrownBy(() -> agent.broadcastSignal(sampleSignal()))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("transport is not connected");
        }
    }

    @Nested
    @DisplayName("Round-trip: orchestrator → agent (task)")
    class TaskRoundTrip {

        @Test
        @DisplayName("should deliver dispatched task to agent handler")
        void shouldDeliverDispatchedTaskToAgentHandler() throws Exception {
            var latch = new CountDownLatch(1);
            var receivedRequests = new CopyOnWriteArrayList<TaskExecutionRequest>();
            agent.receiveTask(request -> {
                receivedRequests.add(request);
                latch.countDown();
            });

            var request = sampleRequest("load-test");
            orchestrator.dispatchTask(request);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedRequests).hasSize(1);
            TaskExecutionRequest received = receivedRequests.get(0);
            assertThat(received.step().taskName()).isEqualTo("load-test");
            assertThat(received.executionId()).isEqualTo(executionId);
            assertThat(received.id()).isEqualTo(messageId);
        }

        @Test
        @DisplayName("should deliver to multiple agent handlers")
        void shouldDeliverToMultipleAgentHandlers() throws Exception {
            var latch1 = new CountDownLatch(1);
            var latch2 = new CountDownLatch(1);
            agent.receiveTask(request -> latch1.countDown());
            agent.receiveTask(request -> latch2.countDown());

            orchestrator.dispatchTask(sampleRequest("multi-handler"));

            assertThat(latch1.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(latch2.await(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Nested
    @DisplayName("Round-trip: agent → orchestrator (event)")
    class EventRoundTrip {

        @Test
        @DisplayName("should deliver event from agent to orchestrator subscriber")
        void shouldDeliverEventFromAgentToOrchestratorSubscriber() throws Exception {
            var latch = new CountDownLatch(1);
            var receivedEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            orchestrator.subscribe(event -> {
                receivedEvents.add(event);
                latch.countDown();
            });

            var event = sampleEvent();
            agent.publishEvent(event);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedEvents).hasSize(1);
            ExecutionEvent received = receivedEvents.get(0);
            assertThat(received.executionId()).isEqualTo(executionId);
            assertThat(received.agentId()).isEqualTo(agentId);
            assertThat(received.eventType()).isEqualTo(ExecutionEvent.TASK_COMPLETED);
        }

        @Test
        @DisplayName("should deliver event to all orchestrator subscribers")
        void shouldDeliverEventToAllOrchestratorSubscribers() throws Exception {
            var latch1 = new CountDownLatch(1);
            var latch2 = new CountDownLatch(1);
            orchestrator.subscribe(event -> latch1.countDown());
            orchestrator.subscribe(event -> latch2.countDown());

            agent.publishEvent(sampleEvent());

            assertThat(latch1.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(latch2.await(3, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("should not deliver event after subscription cancelled")
        void shouldNotDeliverEventAfterSubscriptionCancelled() throws Exception {
            var receivedEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            var subscription = orchestrator.subscribe(receivedEvents::add);

            // Envoyer un premier event (doit etre recu)
            var event1 = sampleEvent();
            agent.publishEvent(event1);
            await().atMost(Duration.ofSeconds(2))
                    .until(() -> receivedEvents.size() == 1);
            assertThat(receivedEvents).hasSize(1);

            // Annuler et renvoyer
            subscription.cancel();
            agent.publishEvent(sampleEvent());
            await().pollDelay(Duration.ofMillis(100))
                    .atMost(Duration.ofMillis(500))
                    .untilAsserted(() -> assertThat(receivedEvents).hasSize(1));
        }
    }

    @Nested
    @DisplayName("Round-trip: orchestrator → agent (signal)")
    class SignalRoundTrip {

        @Test
        @DisplayName("should deliver broadcast signal to agent handler")
        void shouldDeliverBroadcastSignalToAgentHandler() throws Exception {
            var latch = new CountDownLatch(1);
            var receivedSignals = new CopyOnWriteArrayList<AgentSignal>();
            agent.receiveSignal(signal -> {
                receivedSignals.add(signal);
                latch.countDown();
            });

            var signal = sampleSignal();
            orchestrator.broadcastSignal(signal);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedSignals).hasSize(1);
            assertThat(receivedSignals.get(0).id()).isEqualTo(signal.id());
        }
    }

    @Nested
    @DisplayName("Round-trip: agent → orchestrator (agent lifecycle event)")
    class AgentEventRoundTrip {

        @Test
        @DisplayName("should deliver agent lifecycle event to orchestrator subscriber")
        void shouldDeliverAgentLifecycleEventToOrchestratorSubscriber() throws Exception {
            var latch = new CountDownLatch(1);
            var receivedEvents = new CopyOnWriteArrayList<AgentLifecycleEvent>();
            orchestrator.subscribeAgentEvents(event -> {
                receivedEvents.add(event);
                latch.countDown();
            });

            var lifecycleEvent = new AgentLifecycleEvent(
                    eventId,
                    agentId,
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    Map.of("host", "localhost"),
                    Instant.now());
            agent.publishAgentEvent(lifecycleEvent);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedEvents).hasSize(1);
            assertThat(receivedEvents.get(0).agentId()).isEqualTo(agentId);
            assertThat(receivedEvents.get(0).eventType())
                    .isEqualTo(AgentLifecycleEvent.AGENT_REGISTERED);
        }
    }

    @Nested
    @DisplayName("Full round-trip: orchestrator → agent → agent publishes → orchestrator receives")
    class FullRoundTrip {

        @Test
        @DisplayName("complete task dispatch and event feedback loop")
        void completeTaskDispatchAndEventFeedbackLoop() throws Exception {
            // Arrange: orchestrator subscriber
            var orchestratorEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            var eventLatch = new CountDownLatch(1);
            orchestrator.subscribe(event -> {
                orchestratorEvents.add(event);
                eventLatch.countDown();
            });

            // Arrange: agent task handler that publishes an event on receipt
            var agentTasks = new CopyOnWriteArrayList<TaskExecutionRequest>();
            var taskLatch = new CountDownLatch(1);
            agent.receiveTask(request -> {
                agentTasks.add(request);
                taskLatch.countDown();
                // Simuler l'execution de la tache → publier un evenement
                var responseEvent = new ExecutionEvent(
                        EventId.generate(),
                        request.executionId(),
                        request.id(),
                        agentId,
                        ExecutionEvent.TASK_COMPLETED,
                        Map.of("status", "SUCCESS"),
                        Instant.now());
                agent.publishEvent(responseEvent);
            });

            // Act: orchestrator dispatches task
            var request = sampleRequest("full-roundtrip");
            orchestrator.dispatchTask(request);

            // Assert: agent received task
            assertThat(taskLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(agentTasks).hasSize(1);
            assertThat(agentTasks.get(0).step().taskName()).isEqualTo("full-roundtrip");

            // Assert: orchestrator received the event back
            assertThat(eventLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(orchestratorEvents).hasSize(1);
            assertThat(orchestratorEvents.get(0).eventType())
                    .isEqualTo(ExecutionEvent.TASK_COMPLETED);
        }
    }

    @Nested
    @DisplayName("Multiple agents")
    class MultipleAgents {

        @Test
        @DisplayName("should broadcast task to all connected agents")
        void shouldBroadcastTaskToAllConnectedAgents() throws Exception {
            // Créer un deuxième agent
            var agent2 = new SocketExecutionTransport(props);
            agent2.connect();
            try {
                var latch1 = new CountDownLatch(1);
                var latch2 = new CountDownLatch(1);
                agent.receiveTask(request -> latch1.countDown());
                agent2.receiveTask(request -> latch2.countDown());

                orchestrator.dispatchTask(sampleRequest("multi-agent"));

                assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                agent2.disconnect();
            }
        }
    }

    @Nested
    @DisplayName("dispatchTask edge cases")
    class DispatchTaskEdgeCases {

        @Test
        @DisplayName("should reject null request")
        void shouldRejectNullRequest() {
            assertThatThrownBy(() -> orchestrator.dispatchTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("request must not be null");
        }

        @Test
        @DisplayName("should not throw when no agents connected (best-effort)")
        void shouldNotThrowWhenNoAgentsConnected() throws Exception {
            // Fermer l'agent
            agent.disconnect();
            await().atMost(Duration.ofSeconds(2))
                    .until(() -> !agent.isConnected());

            // Le dispatch ne doit pas lever d'exception
            orchestrator.dispatchTask(sampleRequest("no-agent"));
            // Best-effort : pas d'agent, pas de livraison
        }
    }

    @Nested
    @DisplayName("broadcastSignal edge cases")
    class BroadcastSignalEdgeCases {

        @Test
        @DisplayName("should reject null signal")
        void shouldRejectNullSignal() {
            assertThatThrownBy(() -> orchestrator.broadcastSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("signal must not be null");
        }
    }

    @Nested
    @DisplayName("Null checks for public methods")
    class NullChecks {

        @Test
        @DisplayName("should reject null handler in receiveTask")
        void shouldRejectNullHandlerInReceiveTask() {
            assertThatThrownBy(() -> agent.receiveTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null handler in receiveSignal")
        void shouldRejectNullHandlerInReceiveSignal() {
            assertThatThrownBy(() -> agent.receiveSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null handler in subscribe")
        void shouldRejectNullHandlerInSubscribe() {
            assertThatThrownBy(() -> orchestrator.subscribe(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null handler in subscribeAgentEvents")
        void shouldRejectNullHandlerInSubscribeAgentEvents() {
            assertThatThrownBy(() -> orchestrator.subscribeAgentEvents(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should reject null request in dispatchTask")
        void shouldRejectNullRequestInDispatchTask() {
            assertThatThrownBy(() -> orchestrator.dispatchTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("request must not be null");
        }

        @Test
        @DisplayName("should reject null event in publishEvent")
        void shouldRejectNullEventInPublishEvent() {
            assertThatThrownBy(() -> agent.publishEvent(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("event must not be null");
        }

        @Test
        @DisplayName("should reject null event in publishAgentEvent")
        void shouldRejectNullEventInPublishAgentEvent() {
            assertThatThrownBy(() -> agent.publishAgentEvent(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("event must not be null");
        }

        @Test
        @DisplayName("should reject null signal in broadcastSignal")
        void shouldRejectNullSignalInBroadcastSignal() {
            assertThatThrownBy(() -> orchestrator.broadcastSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("signal must not be null");
        }
    }

    @Nested
    @DisplayName("Subscription lifecycle")
    class SubscriptionLifecycle {

        @Test
        @DisplayName("should be active by default")
        void shouldBeActiveByDefault() {
            var subscription = orchestrator.subscribe(e -> {});
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("should be inactive after cancel")
        void shouldBeInactiveAfterCancel() {
            var subscription = orchestrator.subscribe(e -> {});
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("cancel should be idempotent")
        void cancelShouldBeIdempotent() {
            var subscription = orchestrator.subscribe(e -> {});
            subscription.cancel();
            subscription.cancel();
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Message serialization")
    class MessageSerialization {

        @Test
        @DisplayName("should serialize task message with @type field")
        void shouldSerializeTaskMessageWithTypeField() throws Exception {
            var request = sampleRequest("serialize-test");
            var json = objectMapper.valueToTree(request);
            String message = SocketExecutionTransport.serializeMessage(
                    SocketExecutionTransport.TYPE_TASK, json);

            assertThat(message).contains("\"@type\":\"TASK\"");
            assertThat(message).contains("\"request\":");
            assertThat(message).contains("\"serialize-test\"");
        }

        @Test
        @DisplayName("should serialize signal message with @type field")
        void shouldSerializeSignalMessageWithTypeField() throws Exception {
            var signal = sampleSignal();
            var json = objectMapper.valueToTree(signal);
            String message = SocketExecutionTransport.serializeMessage(
                    SocketExecutionTransport.TYPE_SIGNAL, json);

            assertThat(message).contains("\"@type\":\"SIGNAL\"");
            assertThat(message).contains("\"signal\":");
        }

        @Test
        @DisplayName("should serialize event message with @type field")
        void shouldSerializeEventMessageWithTypeField() throws Exception {
            var event = sampleEvent();
            var json = objectMapper.valueToTree(event);
            String message = SocketExecutionTransport.serializeMessage(
                    SocketExecutionTransport.TYPE_EVENT, json);

            assertThat(message).contains("\"@type\":\"EVENT\"");
            assertThat(message).contains("\"event\":");
        }

        @Test
        @DisplayName("should serialize agent event message with @type field")
        void shouldSerializeAgentEventMessageWithTypeField() throws Exception {
            var lifecycleEvent = new AgentLifecycleEvent(
                    eventId, agentId,
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    Map.of("host", "localhost"),
                    Instant.now());
            var json = objectMapper.valueToTree(lifecycleEvent);
            String message = SocketExecutionTransport.serializeMessage(
                    SocketExecutionTransport.TYPE_AGENT_EVENT, json);

            assertThat(message).contains("\"@type\":\"AGENT_EVENT\"");
            assertThat(message).contains("\"event\":");
        }
    }
}
