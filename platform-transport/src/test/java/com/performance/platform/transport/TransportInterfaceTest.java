package com.performance.platform.transport;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransportInterface — signatures et compilation (no-op impl)")
class TransportInterfaceTest {

    // === Fixtures reutilisables ===

    private static final ExecutionId EXECUTION_ID = ExecutionId.generate();
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-1");
    private static final TaskId TASK_ID = TaskId.of("task-1");
    private static final MessageId MESSAGE_ID = MessageId.generate();
    private static final EventId EVENT_ID = EventId.generate();
    private static final AgentId AGENT_ID = AgentId.generate();

    private static StepDefinition sampleStep() {
        return new StepDefinition(
                TASK_ID,
                "sample-task",
                Phase.INJECTION,
                Map.of("key", "value"),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                defaultRetryPolicy()
        );
    }

    private static RetryPolicy defaultRetryPolicy() {
        return new RetryPolicy(
                3,
                Duration.ofMillis(100),
                2.0,
                Duration.ofSeconds(5),
                Set.of(RuntimeException.class)
        );
    }

    private static PartialExecutionContext sampleContext() {
        return new PartialExecutionContext(
                EXECUTION_ID,
                SCENARIO_ID,
                Map.of("task-0", Map.of("agent-1", "result"))
        );
    }

    private static TaskExecutionRequest sampleRequest() {
        return new TaskExecutionRequest(
                MESSAGE_ID,
                EXECUTION_ID,
                sampleStep(),
                sampleContext(),
                Instant.now(),
                defaultRetryPolicy()
        );
    }

    private static ExecutionEvent sampleEvent() {
        return new ExecutionEvent(
                EVENT_ID,
                EXECUTION_ID,
                MESSAGE_ID,
                AGENT_ID,
                ExecutionEvent.TASK_COMPLETED,
                Map.of("status", "SUCCESS"),
                Instant.now()
        );
    }

    private static AgentSignal sampleSignal() {
        return new ScenarioRestartSignal(
                SignalId.generate(),
                EXECUTION_ID,
                "Test restart",
                Instant.now()
        );
    }

    // === No-op transport implementation for compilation verification ===

    static class NoOpSubscription implements Subscription {
        private final AtomicBoolean active = new AtomicBoolean(true);

        @Override
        public void cancel() {
            active.set(false);
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }

    static class NoOpTransport implements ExecutionTransport {
        private boolean connected = false;
        private final List<ExecutionEvent> publishedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void dispatchTask(TaskExecutionRequest request) {
            // no-op: broadcast task
        }

        @Override
        public void broadcastSignal(AgentSignal signal) {
            // no-op: broadcast signal
        }

        @Override
        public void publishEvent(ExecutionEvent event) {
            publishedEvents.add(event);
        }

        @Override
        public Subscription subscribe(ExecutionEventHandler handler) {
            return new NoOpSubscription();
        }

        @Override
        public void receiveTask(TaskRequestHandler handler) {
            handler.onRequest(sampleRequest());
        }

        @Override
        public void receiveSignal(AgentSignalHandler handler) {
            handler.onSignal(sampleSignal());
        }

        @Override
        public void connect() throws TransportException {
            connected = true;
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public TransportType getType() {
            return TransportType.IN_MEMORY;
        }

        @Override
        public void publishAgentEvent(AgentLifecycleEvent event) {
            // no-op
        }

        @Override
        public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
            return new NoOpSubscription();
        }
    }

    // === Tests ===

    @Nested
    @DisplayName("ExecutionTransport no-op implementation")
    class NoOpTransportTests {

        @Test
        @DisplayName("should compile and connect")
        void shouldConnect() {
            var transport = new NoOpTransport();
            transport.connect();
            assertThat(transport.isConnected()).isTrue();
        }

        @Test
        @DisplayName("should disconnect")
        void shouldDisconnect() {
            var transport = new NoOpTransport();
            transport.connect();
            transport.disconnect();
            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("should return transport type")
        void shouldReturnTransportType() {
            var transport = new NoOpTransport();
            assertThat(transport.getType()).isEqualTo(TransportType.IN_MEMORY);
        }

        @Test
        @DisplayName("should dispatch task without throwing")
        void shouldDispatchTask() {
            var transport = new NoOpTransport();
            transport.dispatchTask(sampleRequest());
            // no exception = success
        }

        @Test
        @DisplayName("should broadcast signal without throwing")
        void shouldBroadcastSignal() {
            var transport = new NoOpTransport();
            transport.broadcastSignal(sampleSignal());
            // no exception = success
        }

        @Test
        @DisplayName("should publish event")
        void shouldPublishEvent() {
            var transport = new NoOpTransport();
            var event = sampleEvent();
            transport.publishEvent(event);
            assertThat(transport.publishedEvents).containsExactly(event);
        }

        @Test
        @DisplayName("should subscribe and return active subscription")
        void shouldSubscribe() {
            var transport = new NoOpTransport();
            var subscription = transport.subscribe(e -> {});
            assertThat(subscription).isNotNull();
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("should receive task via handler")
        void shouldReceiveTask() {
            var transport = new NoOpTransport();
            var received = new CopyOnWriteArrayList<TaskExecutionRequest>();
            transport.receiveTask(received::add);
            assertThat(received).hasSize(1);
        }

        @Test
        @DisplayName("should receive signal via handler")
        void shouldReceiveSignal() {
            var transport = new NoOpTransport();
            var received = new CopyOnWriteArrayList<AgentSignal>();
            transport.receiveSignal(received::add);
            assertThat(received).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Subscription")
    class SubscriptionTests {

        @Test
        @DisplayName("should be active by default")
        void shouldBeActiveByDefault() {
            var subscription = new NoOpSubscription();
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("should be inactive after cancel")
        void shouldBeInactiveAfterCancel() {
            var subscription = new NoOpSubscription();
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("cancel should be idempotent")
        void cancelShouldBeIdempotent() {
            var subscription = new NoOpSubscription();
            subscription.cancel();
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("TaskExecutionRequest")
    class TaskExecutionRequestTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreate() {
            var request = sampleRequest();
            assertThat(request.id()).isEqualTo(MESSAGE_ID);
            assertThat(request.executionId()).isEqualTo(EXECUTION_ID);
            assertThat(request.step()).isNotNull();
            assertThat(request.context()).isNotNull();
            assertThat(request.dispatchedAt()).isNotNull();
            assertThat(request.retryPolicy()).isNotNull();
        }

        @Test
        @DisplayName("should reject null id")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    null, EXECUTION_ID, sampleStep(), sampleContext(),
                    Instant.now(), defaultRetryPolicy()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null executionId")
        void shouldRejectNullExecutionId() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, null, sampleStep(), sampleContext(),
                    Instant.now(), defaultRetryPolicy()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null step")
        void shouldRejectNullStep() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, null, sampleContext(),
                    Instant.now(), defaultRetryPolicy()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null context")
        void shouldRejectNullContext() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, sampleStep(), null,
                    Instant.now(), defaultRetryPolicy()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null dispatchedAt")
        void shouldRejectNullDispatchedAt() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, sampleStep(), sampleContext(),
                    null, defaultRetryPolicy()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null retryPolicy")
        void shouldRejectNullRetryPolicy() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, sampleStep(), sampleContext(),
                    Instant.now(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ExecutionEvent")
    class ExecutionEventTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreate() {
            var event = sampleEvent();
            assertThat(event.id()).isEqualTo(EVENT_ID);
            assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
            assertThat(event.correlationId()).isEqualTo(MESSAGE_ID);
            assertThat(event.agentId()).isEqualTo(AGENT_ID);
            assertThat(event.eventType()).isEqualTo(ExecutionEvent.TASK_COMPLETED);
            assertThat(event.payload()).containsEntry("status", "SUCCESS");
            assertThat(event.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should allow nullable correlationId")
        void shouldAllowNullableCorrelationId() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, null, AGENT_ID,
                    ExecutionEvent.AGENT_REGISTERED,
                    Map.of(), Instant.now());
            assertThat(event.correlationId()).isNull();
        }

        @Test
        @DisplayName("should allow nullable agentId")
        void shouldAllowNullableAgentId() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, null,
                    ExecutionEvent.TASK_FAILED,
                    Map.of(), Instant.now());
            assertThat(event.agentId()).isNull();
        }

        @Test
        @DisplayName("should defensively copy payload")
        void shouldDefensivelyCopyPayload() {
            var mutablePayload = new HashMap<String, Object>();
            mutablePayload.put("key", "value");
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED,
                    mutablePayload, Instant.now());

            mutablePayload.put("extra", "should not appear");

            assertThat(event.payload()).hasSize(1);
            assertThat(event.payload()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should treat null payload as empty map")
        void shouldTreatNullPayloadAsEmptyMap() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, null, null,
                    ExecutionEvent.TASK_FAILED,
                    null, Instant.now());
            assertThat(event.payload()).isEmpty();
        }

        @Test
        @DisplayName("should define all event type constants")
        void shouldDefineAllEventTypeConstants() {
            assertThat(ExecutionEvent.AGENT_REGISTERED).isEqualTo("AgentRegistered");
            assertThat(ExecutionEvent.AGENT_HEARTBEAT).isEqualTo("AgentHeartbeat");
            assertThat(ExecutionEvent.AGENT_DEREGISTERED).isEqualTo("AgentDeregistered");
            assertThat(ExecutionEvent.TASK_CLAIMED).isEqualTo("TaskClaimedByAgent");
            assertThat(ExecutionEvent.TASK_WORK_IN_PROGRESS).isEqualTo("TaskWorkInProgress");
            assertThat(ExecutionEvent.TASK_COMPLETED).isEqualTo("TaskCompleted");
            assertThat(ExecutionEvent.TASK_FAILED).isEqualTo("TaskFailed");
        }

        @Test
        @DisplayName("should reject null id")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    null, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, Map.of(), Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null executionId")
        void shouldRejectNullExecutionId() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    EVENT_ID, null, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, Map.of(), Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null eventType")
        void shouldRejectNullEventType() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    null, Map.of(), Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null occurredAt")
        void shouldRejectNullOccurredAt() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, Map.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("TransportType")
    class TransportTypeTests {

        @Test
        @DisplayName("should contain expected values")
        void shouldContainExpectedValues() {
            assertThat(TransportType.values()).containsExactly(
                    TransportType.KAFKA,
                    TransportType.RABBITMQ,
                    TransportType.HTTP,
                    TransportType.SOCKET,
                    TransportType.IN_MEMORY,
                    TransportType.CUSTOM
            );
        }

        @Test
        @DisplayName("valueOf should work for all types")
        void valueOfShouldWork() {
            assertThat(TransportType.valueOf("KAFKA")).isEqualTo(TransportType.KAFKA);
            assertThat(TransportType.valueOf("RABBITMQ")).isEqualTo(TransportType.RABBITMQ);
            assertThat(TransportType.valueOf("HTTP")).isEqualTo(TransportType.HTTP);
            assertThat(TransportType.valueOf("SOCKET")).isEqualTo(TransportType.SOCKET);
            assertThat(TransportType.valueOf("IN_MEMORY")).isEqualTo(TransportType.IN_MEMORY);
        }
    }

    @Nested
    @DisplayName("TransportException")
    class TransportExceptionTests {

        @Test
        @DisplayName("should create with message")
        void shouldCreateWithMessage() {
            var ex = new TransportException("connection failed");
            assertThat(ex).isInstanceOf(RuntimeException.class);
            assertThat(ex.getMessage()).isEqualTo("connection failed");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("should create with message and cause")
        void shouldCreateWithMessageAndCause() {
            var cause = new RuntimeException("root cause");
            var ex = new TransportException("connection failed", cause);
            assertThat(ex.getMessage()).isEqualTo("connection failed");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("Functional interface compilation")
    class FunctionalInterfaceTests {

        @Test
        @DisplayName("TaskRequestHandler can be a lambda")
        void taskRequestHandlerLambda() {
            TaskRequestHandler handler = request -> {};
            handler.onRequest(sampleRequest());
            // compiles and runs
        }

        @Test
        @DisplayName("AgentSignalHandler can be a lambda")
        void agentSignalHandlerLambda() {
            AgentSignalHandler handler = signal -> {};
            handler.onSignal(sampleSignal());
            // compiles and runs
        }

        @Test
        @DisplayName("ExecutionEventHandler can be a lambda")
        void executionEventHandlerLambda() {
            ExecutionEventHandler handler = event -> {};
            handler.onEvent(sampleEvent());
            // compiles and runs
        }
    }
}
