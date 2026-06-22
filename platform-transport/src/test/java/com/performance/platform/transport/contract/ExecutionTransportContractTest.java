package com.performance.platform.transport.contract;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.*;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.*;



import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CopyOnWriteArrayList;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Contrat abstrait pour toutes les implementations de {@link ExecutionTransport}.
 * <p>
 * Chaque implementation concrete (InMemory, HTTP, Socket, Kafka, RabbitMQ)
 * doit satisfaire ce contrat : connect/disconnect, dispatch/receive task,
 * publish/subscribe event, broadcast/receive signal, publish/subscribe agent event.
 * <p>
 * Ce contrat est teste ici avec les transports qui n'ont pas besoin
 * d'infrastructure externe (InMemory) et sert de reference pour les
 * transports externes (Kafka, RabbitMQ) qui ont leurs propres ITs.
 */
@DisplayName("ExecutionTransport Contract")
class ExecutionTransportContractTest {

    // ========================================================================
    // CORE CONTRACT: connect / disconnect / isConnected
    // ========================================================================

    @Nested
    @DisplayName("Connection lifecycle")
    class ConnectionLifecycle {

        private ExecutionTransport transport;

        @BeforeEach
        void setUp() {
            transport = createTransport();
        }

        @AfterEach
        void tearDown() {
            if (transport.isConnected()) {
                transport.disconnect();
            }
        }

        @Test
        @DisplayName("TC-CONTRACT-01: connect() sets isConnected()")
        void connectSetsConnected() {
            transport.connect();
            assertThat(transport.isConnected()).isTrue();
        }

        @Test
        @DisplayName("TC-CONTRACT-02: disconnect() clears isConnected()")
        void disconnectsetsNotConnected() {
            transport.connect();
            assertThat(transport.isConnected()).isTrue();
            transport.disconnect();
            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("TC-CONTRACT-03: isConnected() is false before connect()")
        void notConnectedBeforeConnect() {
            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("TC-CONTRACT-04: getType() returns non-null type")
        void getTypeReturnsNonNull() {
            assertThat(transport.getType()).isNotNull();
        }

        @Test
        @DisplayName("TC-CONTRACT-05: double disconnect is safe (idempotent)")
        void doubleDisconnectIsIdempotent() {
            transport.connect();
            transport.disconnect();
            assertThatCode(() -> transport.disconnect()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TC-CONTRACT-06: operations before connect throw TransportException")
        void operationBeforeConnect() {
            var request = buildTaskExecutionRequest("test-task");
            // Operations before connect should throw TransportException
            assertThatThrownBy(() -> transport.dispatchTask(request))
                    .isInstanceOf(TransportException.class);
        }
    }

    // ========================================================================
    // TASK: dispatch / receive
    // ========================================================================

    @Nested
    @DisplayName("Task dispatch and receive")
    class TaskDispatchReceive {

        private ExecutionTransport transport;

        @BeforeEach
        void setUp() {
            transport = createTransport();
            transport.connect();
        }

        @AfterEach
        void tearDown() {
            if (transport.isConnected()) {
                transport.disconnect();
            }
        }

        @Test
        @DisplayName("TC-CONTRACT-07: dispatchTask reaches receiveTask handler")
        void dispatchTaskReachesHandler() {
            AtomicReference<TaskExecutionRequest> received = new AtomicReference<>();
            transport.receiveTask(received::set);

            var request = buildTaskExecutionRequest("database");
            transport.dispatchTask(request);

            await().atMost(5, TimeUnit.SECONDS).until(() -> received.get() != null);
            assertThat(received.get().id()).isEqualTo(request.id());
            assertThat(received.get().executionId()).isEqualTo(request.executionId());
            assertThat(received.get().step().taskName()).isEqualTo("database");
        }

        @Test
        @DisplayName("TC-CONTRACT-08: receiveTask can have multiple handlers registered")
        void multipleTaskHandlers() {
            AtomicReference<TaskExecutionRequest> received1 = new AtomicReference<>();
            AtomicReference<TaskExecutionRequest> received2 = new AtomicReference<>();
            transport.receiveTask(received1::set);
            transport.receiveTask(received2::set);

            var request = buildTaskExecutionRequest("shell");
            transport.dispatchTask(request);

            await().atMost(5, TimeUnit.SECONDS).until(() -> received1.get() != null);
            assertThat(received1.get().id()).isEqualTo(request.id());
            // Second handler receives the same task (broadcast)
            await().atMost(5, TimeUnit.SECONDS).until(() -> received2.get() != null);
            assertThat(received2.get().id()).isEqualTo(request.id());
        }

        @Test
        @DisplayName("TC-CONTRACT-09: TaskExecutionRequest carries correct metadata")
        void taskRequestCarriesMetadata() {
            AtomicReference<TaskExecutionRequest> received = new AtomicReference<>();
            transport.receiveTask(received::set);

            var executionId = ExecutionId.generate();
            var messageId = MessageId.generate();
            var step = buildStep("verify-db", "database", Phase.PREPARATION);
            var ctx = PartialExecutionContext.empty(executionId, ScenarioId.of("test-scenario"));
            var retryPolicy = RetryPolicy.defaults();
            var dispatchedAt = Instant.now();

            var request = new TaskExecutionRequest(messageId, executionId, step, ctx,
                    dispatchedAt, retryPolicy);
            transport.dispatchTask(request);

            await().atMost(5, TimeUnit.SECONDS).until(() -> received.get() != null);
            assertThat(received.get().executionId()).isEqualTo(executionId);
            assertThat(received.get().step().taskName()).isEqualTo("database");
            assertThat(received.get().retryPolicy()).isNotNull();
            assertThat(received.get().dispatchedAt()).isNotNull();
        }

        @Test
        @DisplayName("TC-CONTRACT-10: Multiple task dispatches all reach handler")
        void multipleTaskDispatches() {
            var received = new CopyOnWriteArrayList<TaskExecutionRequest>();
            transport.receiveTask(received::add);

            var r1 = buildTaskExecutionRequest("shell");
            var r2 = buildTaskExecutionRequest("database");
            var r3 = buildTaskExecutionRequest("gatling");

            transport.dispatchTask(r1);
            transport.dispatchTask(r2);
            transport.dispatchTask(r3);

            await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() >= 3);
            assertThat(received).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ========================================================================
    // EVENTS: publish / subscribe
    // ========================================================================

    @Nested
    @DisplayName("Event publish and subscribe")
    class EventPublishSubscribe {

        private ExecutionTransport transport;

        @BeforeEach
        void setUp() {
            transport = createTransport();
            transport.connect();
        }

        @AfterEach
        void tearDown() {
            if (transport.isConnected()) {
                transport.disconnect();
            }
        }

        @Test
        @DisplayName("TC-CONTRACT-11: publishEvent reaches subscriber")
        void publishEventReachesSubscriber() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(received::add);

            var event = buildExecutionEvent(ExecutionEvent.TASK_COMPLETED,
                    Map.of("status", "SUCCESS"));
            transport.publishEvent(event);

            await().atMost(5, TimeUnit.SECONDS).until(() -> !received.isEmpty());
            assertThat(received.get(0).id()).isEqualTo(event.id());
            assertThat(received.get(0).eventType()).isEqualTo(ExecutionEvent.TASK_COMPLETED);
            assertThat(received.get(0).payload()).containsEntry("status", "SUCCESS");
        }

        @Test
        @DisplayName("TC-CONTRACT-12: unsubscribe stops receiving events")
        void unsubscribeStopsEvents() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            Subscription sub = transport.subscribe(received::add);
            sub.cancel();

            transport.publishEvent(buildExecutionEvent(ExecutionEvent.TASK_COMPLETED, Map.of()));
            // Wait briefly and ensure no event received
            await().pollDelay(300, TimeUnit.MILLISECONDS).until(() -> true);
            assertThat(received).isEmpty();
        }

        @Test
        @DisplayName("TC-CONTRACT-13: multiple subscribers all receive events")
        void multipleSubscribers() {
            var received1 = new CopyOnWriteArrayList<ExecutionEvent>();
            var received2 = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(received1::add);
            transport.subscribe(received2::add);

            transport.publishEvent(buildExecutionEvent(ExecutionEvent.TASK_COMPLETED, Map.of()));

            await().atMost(5, TimeUnit.SECONDS).until(() -> !received1.isEmpty());
            await().atMost(5, TimeUnit.SECONDS).until(() -> !received2.isEmpty());
            assertThat(received1).hasSize(1);
            assertThat(received2).hasSize(1);
        }

        @Test
        @DisplayName("TC-CONTRACT-14: All event types are publishable")
        void allEventTypesPublishable() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(received::add);

            for (String eventType : List.of(
                    ExecutionEvent.TASK_CLAIMED,
                    ExecutionEvent.TASK_WORK_IN_PROGRESS,
                    ExecutionEvent.TASK_COMPLETED,
                    ExecutionEvent.TASK_FAILED)) {

                transport.publishEvent(buildExecutionEvent(eventType, Map.of("type", eventType)));
            }

            await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() >= 4);

            assertThat(received.stream().map(ExecutionEvent::eventType).toList())
                    .contains(ExecutionEvent.TASK_CLAIMED, ExecutionEvent.TASK_COMPLETED,
                            ExecutionEvent.TASK_FAILED, ExecutionEvent.TASK_WORK_IN_PROGRESS);
        }
    }

    // ========================================================================
    // SIGNALS: broadcast / receive
    // ========================================================================

    @Nested
    @DisplayName("Signal broadcast and receive")
    class SignalBroadcastReceive {

        private ExecutionTransport transport;

        @BeforeEach
        void setUp() {
            transport = createTransport();
            transport.connect();
        }

        @AfterEach
        void tearDown() {
            if (transport.isConnected()) {
                transport.disconnect();
            }
        }

        @Test
        @DisplayName("TC-CONTRACT-15: broadcastSignal reaches receiveSignal handler")
        void broadcastSignalReachesHandler() {
            AtomicReference<AgentSignal> received = new AtomicReference<>();
            transport.receiveSignal(received::set);

            var signal = new ScenarioRestartSignal(
                    SignalId.generate(), ExecutionId.generate(),
                    "Manual restart requested", Instant.now());
            transport.broadcastSignal(signal);

            await().atMost(5, TimeUnit.SECONDS).until(() -> received.get() != null);
            assertThat(received.get()).isInstanceOf(ScenarioRestartSignal.class);
            assertThat(((ScenarioRestartSignal) received.get()).reason())
                    .isEqualTo("Manual restart requested");
        }

        @Test
        @DisplayName("TC-CONTRACT-16: signal handler can be updated (last writer wins)")
        void signalHandlerCanBeUpdated() {
            AtomicReference<AgentSignal> received1 = new AtomicReference<>();
            AtomicReference<AgentSignal> received2 = new AtomicReference<>();

            transport.receiveSignal(received1::set);
            transport.receiveSignal(received2::set); // Last handler wins for InMemory transport

            var signal = new ScenarioRestartSignal(
                    SignalId.generate(), ExecutionId.generate(),
                    "test reason", Instant.now());
            transport.broadcastSignal(signal);

            await().atMost(5, TimeUnit.SECONDS).until(() -> received2.get() != null);
        }
    }

    // ========================================================================
    // AGENT EVENTS: publish / subscribe agent lifecycle
    // ========================================================================

    @Nested
    @DisplayName("Agent lifecycle events (ADR-012)")
    class AgentLifecycleEvents {

        private ExecutionTransport transport;

        @BeforeEach
        void setUp() {
            transport = createTransport();
            transport.connect();
        }

        @AfterEach
        void tearDown() {
            if (transport.isConnected()) {
                transport.disconnect();
            }
        }

        @Test
        @DisplayName("TC-CONTRACT-17: publishAgentEvent reaches agent lifecycle subscriber")
        void publishAgentEventReachesSubscriber() {
            var received = new CopyOnWriteArrayList<AgentLifecycleEvent>();
            transport.subscribeAgentEvents(received::add);

            var event = new AgentLifecycleEvent(
                    EventId.generate(), AgentId.generate(),
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    Map.of("name", "agent-1", "host", "localhost"),
                    Instant.now());
            transport.publishAgentEvent(event);

            await().atMost(5, TimeUnit.SECONDS).until(() -> !received.isEmpty());
            assertThat(received.get(0).eventType()).isEqualTo(AgentLifecycleEvent.AGENT_REGISTERED);
            assertThat(received.get(0).payload()).containsEntry("name", "agent-1");
        }

        @Test
        @DisplayName("TC-CONTRACT-18: Execution event and agent event channels are independent")
        void separateEventChannels() {
            var execEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            var agentEvents = new CopyOnWriteArrayList<AgentLifecycleEvent>();

            transport.subscribe(execEvents::add);
            transport.subscribeAgentEvents(agentEvents::add);

            // Publish agent event — should NOT reach execution subscribers
            var agentEvent = new AgentLifecycleEvent(
                    EventId.generate(), AgentId.generate(),
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    Map.of(), Instant.now());
            transport.publishAgentEvent(agentEvent);

            await().atMost(5, TimeUnit.SECONDS).until(() -> !agentEvents.isEmpty());
            assertThat(execEvents).isEmpty();
            assertThat(agentEvents).hasSize(1);

            // Publish execution event — should NOT reach agent subscribers
            transport.publishEvent(buildExecutionEvent(ExecutionEvent.TASK_COMPLETED, Map.of()));

            await().atMost(5, TimeUnit.SECONDS).until(() -> !execEvents.isEmpty());
            assertThat(agentEvents).hasSize(1); // Still only the agent event
        }
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("TC-CONTRACT-19: Event with null payload handled gracefully")
        void nullPayloadEvent() {
            var transport = createTransport();
            transport.connect();
            try {
                var received = new CopyOnWriteArrayList<ExecutionEvent>();
                transport.subscribe(received::add);

                var event = new ExecutionEvent(EventId.generate(), ExecutionId.generate(),
                        MessageId.generate(), AgentId.generate(),
                        ExecutionEvent.TASK_COMPLETED, null, Instant.now());

                assertThatCode(() -> transport.publishEvent(event)).doesNotThrowAnyException();
            } finally {
                transport.disconnect();
            }
        }

        @Test
        @DisplayName("TC-CONTRACT-20: Connect after disconnect works")
        void reconnectAfterDisconnect() {
            var transport = createTransport();
            transport.connect();
            transport.disconnect();
            transport.connect();
            assertThat(transport.isConnected()).isTrue();
            transport.disconnect();
        }

        @Test
        @DisplayName("TC-CONTRACT-21: Large payload in event is handled")
        void largePayloadEvent() {
            var transport = createTransport();
            transport.connect();
            try {
                var received = new CopyOnWriteArrayList<ExecutionEvent>();
                transport.subscribe(received::add);

                // Build a moderately large payload
                var largePayload = new java.util.HashMap<String, Object>();
                for (int i = 0; i < 100; i++) {
                    largePayload.put("key-" + i, "value-" + "-".repeat(100));
                }

                var event = new ExecutionEvent(EventId.generate(), ExecutionId.generate(),
                        MessageId.generate(), AgentId.generate(),
                        ExecutionEvent.TASK_COMPLETED, largePayload, Instant.now());

                transport.publishEvent(event);
                await().atMost(10, TimeUnit.SECONDS).until(() -> !received.isEmpty());
                assertThat(received.get(0).payload()).hasSize(100);
            } finally {
                transport.disconnect();
            }
        }
    }

    // ========================================================================
    // Factory methods
    // ========================================================================

    /**
     * Override to test a specific transport implementation.
     */
    ExecutionTransport createTransport() {
        return new InMemoryExecutionTransport();
    }

    private TaskExecutionRequest buildTaskExecutionRequest(String taskName) {
        var step = buildStep("task-" + taskName, taskName, Phase.PREPARATION);
        return new TaskExecutionRequest(
                MessageId.generate(),
                ExecutionId.generate(),
                step,
                PartialExecutionContext.empty(ExecutionId.generate(), ScenarioId.of("test")),
                Instant.now(),
                RetryPolicy.defaults()
        );
    }

    private StepDefinition buildStep(String id, String taskName, Phase phase) {
        return new StepDefinition(
                TaskId.of(id), taskName, phase,
                Map.of("key", "value"),
                List.of(), List.of(),
                Duration.ofSeconds(30), null
        );
    }

    private ExecutionEvent buildExecutionEvent(String eventType, Map<String, Object> payload) {
        return new ExecutionEvent(
                EventId.generate(), ExecutionId.generate(),
                MessageId.generate(), AgentId.generate(),
                eventType, payload, Instant.now()
        );
    }
}
