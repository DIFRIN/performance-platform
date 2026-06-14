package com.performance.platform.transport.inmemory;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.*;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryExecutionTransport")
class InMemoryExecutionTransportTest {

    private InMemoryExecutionTransport transport;
    private ExecutionId executionId;
    private ScenarioId scenarioId;
    private TaskId taskId;
    private MessageId messageId;
    private EventId eventId;
    private AgentId agentId;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        transport.connect();
        executionId = ExecutionId.generate();
        scenarioId = ScenarioId.of("scenario-1");
        taskId = TaskId.of("task-1");
        messageId = MessageId.generate();
        eventId = EventId.generate();
        agentId = AgentId.generate();
    }

    // === Fixtures ===

    private StepDefinition sampleStep() {
        return new StepDefinition(
                taskId,
                "sample-task",
                Phase.INJECTION,
                Map.of("key", "value"),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), Set.of(RuntimeException.class))
        );
    }

    private PartialExecutionContext sampleContext() {
        return new PartialExecutionContext(
                executionId,
                scenarioId,
                Map.of("task-0", Map.of("agent-1", "result"))
        );
    }

    private TaskExecutionRequest sampleRequest() {
        return new TaskExecutionRequest(
                messageId,
                executionId,
                sampleStep(),
                sampleContext(),
                Instant.now(),
                new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), Set.of(RuntimeException.class))
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
        @DisplayName("should not be connected initially")
        void shouldNotBeConnectedInitially() {
            var fresh = new InMemoryExecutionTransport();
            assertThat(fresh.isConnected()).isFalse();
        }

        @Test
        @DisplayName("should connect")
        void shouldConnect() {
            transport.connect();
            assertThat(transport.isConnected()).isTrue();
        }

        @Test
        @DisplayName("should disconnect")
        void shouldDisconnect() {
            transport.connect();
            transport.disconnect();
            assertThat(transport.isConnected()).isFalse();
        }

        @Test
        @DisplayName("should return IN_MEMORY type")
        void shouldReturnInMemoryType() {
            assertThat(transport.getType()).isEqualTo(TransportType.IN_MEMORY);
        }
    }

    @Nested
    @DisplayName("Task dispatch → receive round-trip")
    class TaskDispatchReceive {

        @Test
        @DisplayName("should deliver dispatched task to registered handler")
        void shouldDeliverDispatchedTaskToHandler() {
            var received = new CopyOnWriteArrayList<TaskExecutionRequest>();
            transport.receiveTask(received::add);

            var request = sampleRequest();
            transport.dispatchTask(request);

            assertThat(received).containsExactly(request);
        }

        @Test
        @DisplayName("should deliver to all registered task handlers")
        void shouldDeliverToAllTaskHandlers() {
            var received1 = new CopyOnWriteArrayList<TaskExecutionRequest>();
            var received2 = new CopyOnWriteArrayList<TaskExecutionRequest>();
            transport.receiveTask(received1::add);
            transport.receiveTask(received2::add);

            var request = sampleRequest();
            transport.dispatchTask(request);

            assertThat(received1).containsExactly(request);
            assertThat(received2).containsExactly(request);
        }

        @Test
        @DisplayName("should do nothing when no task handlers registered")
        void shouldDoNothingWhenNoTaskHandlers() {
            var request = sampleRequest();
            transport.dispatchTask(request); // pas d'exception
        }

        @Test
        @DisplayName("should reject null request in dispatchTask")
        void shouldRejectNullRequestInDispatchTask() {
            assertThatThrownBy(() -> transport.dispatchTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("request must not be null");
        }

        @Test
        @DisplayName("should reject null handler in receiveTask")
        void shouldRejectNullHandlerInReceiveTask() {
            assertThatThrownBy(() -> transport.receiveTask(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should track handler count")
        void shouldTrackHandlerCount() {
            assertThat(transport.taskHandlerCount()).isZero();
            transport.receiveTask(r -> {});
            assertThat(transport.taskHandlerCount()).isEqualTo(1);
            transport.receiveTask(r -> {});
            assertThat(transport.taskHandlerCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Event publish → subscribe round-trip")
    class EventPublishSubscribe {

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

            var event = sampleEvent();
            transport.publishEvent(event);

            assertThat(received).isEmpty();
        }

        @Test
        @DisplayName("should deliver to remaining subscribers after one cancels")
        void shouldDeliverToRemainingSubscribersAfterOneCancels() {
            var received1 = new CopyOnWriteArrayList<ExecutionEvent>();
            var received2 = new CopyOnWriteArrayList<ExecutionEvent>();
            var sub1 = transport.subscribe(received1::add);
            transport.subscribe(received2::add);

            sub1.cancel();

            var event = sampleEvent();
            transport.publishEvent(event);

            assertThat(received1).isEmpty();
            assertThat(received2).containsExactly(event);
        }

        @Test
        @DisplayName("should do nothing when no subscribers")
        void shouldDoNothingWhenNoSubscribers() {
            var event = sampleEvent();
            transport.publishEvent(event); // pas d'exception
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

        @Test
        @DisplayName("should track active subscription count")
        void shouldTrackActiveSubscriptionCount() {
            assertThat(transport.activeSubscriptionCount()).isZero();
            var sub = transport.subscribe(e -> {});
            assertThat(transport.activeSubscriptionCount()).isEqualTo(1);
            sub.cancel();
            assertThat(transport.activeSubscriptionCount()).isZero();
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
        @DisplayName("should stop receiving events after cancel")
        void shouldStopReceivingEventsAfterCancel() {
            var received = new CopyOnWriteArrayList<ExecutionEvent>();
            var subscription = transport.subscribe(received::add);

            // Première publication : reçue
            transport.publishEvent(sampleEvent());
            assertThat(received).hasSize(1);

            // Annulation
            subscription.cancel();

            // Deuxième publication : non reçue
            transport.publishEvent(sampleEvent());
            assertThat(received).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Signal broadcast → receive round-trip")
    class SignalBroadcastReceive {

        @Test
        @DisplayName("should deliver broadcast signal to registered handler")
        void shouldDeliverBroadcastSignalToHandler() {
            var received = new CopyOnWriteArrayList<AgentSignal>();
            transport.receiveSignal(received::add);

            var signal = sampleSignal();
            transport.broadcastSignal(signal);

            assertThat(received).containsExactly(signal);
        }

        @Test
        @DisplayName("should deliver to all registered signal handlers")
        void shouldDeliverToAllSignalHandlers() {
            var received1 = new CopyOnWriteArrayList<AgentSignal>();
            var received2 = new CopyOnWriteArrayList<AgentSignal>();
            transport.receiveSignal(received1::add);
            transport.receiveSignal(received2::add);

            var signal = sampleSignal();
            transport.broadcastSignal(signal);

            assertThat(received1).containsExactly(signal);
            assertThat(received2).containsExactly(signal);
        }

        @Test
        @DisplayName("should do nothing when no signal handlers registered")
        void shouldDoNothingWhenNoSignalHandlers() {
            var signal = sampleSignal();
            transport.broadcastSignal(signal); // pas d'exception
        }

        @Test
        @DisplayName("should reject null signal in broadcastSignal")
        void shouldRejectNullSignalInBroadcastSignal() {
            assertThatThrownBy(() -> transport.broadcastSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("signal must not be null");
        }

        @Test
        @DisplayName("should reject null handler in receiveSignal")
        void shouldRejectNullHandlerInReceiveSignal() {
            assertThatThrownBy(() -> transport.receiveSignal(null))
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("handler must not be null");
        }

        @Test
        @DisplayName("should track signal handler count")
        void shouldTrackSignalHandlerCount() {
            assertThat(transport.signalHandlerCount()).isZero();
            transport.receiveSignal(s -> {});
            assertThat(transport.signalHandlerCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Round-trip integration scenarios")
    class RoundTripScenarios {

        @Test
        @DisplayName("full round-trip: dispatch→receiveTask + publishEvent→subscribe + broadcastSignal→receiveSignal")
        void fullRoundTrip() {
            var tasks = new CopyOnWriteArrayList<TaskExecutionRequest>();
            var events = new CopyOnWriteArrayList<ExecutionEvent>();
            var signals = new CopyOnWriteArrayList<AgentSignal>();

            transport.receiveTask(tasks::add);
            transport.subscribe(events::add);
            transport.receiveSignal(signals::add);

            var request = sampleRequest();
            var event = sampleEvent();
            var signal = sampleSignal();

            transport.dispatchTask(request);
            transport.publishEvent(event);
            transport.broadcastSignal(signal);

            assertThat(tasks).containsExactly(request);
            assertThat(events).containsExactly(event);
            assertThat(signals).containsExactly(signal);
        }
    }
}
