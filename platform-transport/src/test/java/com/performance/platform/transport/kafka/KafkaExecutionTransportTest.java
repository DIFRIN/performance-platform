package com.performance.platform.transport.kafka;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.*;
import com.performance.platform.transport.config.KafkaTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
@DisplayName("KafkaExecutionTransport — unit tests with mocked KafkaTemplate")
@ExtendWith(MockitoExtension.class)
class KafkaExecutionTransportTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    private KafkaTransportProperties props;
    private KafkaExecutionTransport transport;

    @BeforeEach
    void setUp() {
        props = new KafkaTransportProperties(
                "localhost:9092",
                "agents-tasks",
                "agents-events",
                "agents-signals",
                "all",
                "test-group"
        );
        transport = new KafkaExecutionTransport(props, kafkaTemplate);
    }


    private static StepDefinition sampleStep() {
        return new StepDefinition(
                TaskId.of("task-1"),
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
                3, Duration.ofMillis(100), 2.0,
                Duration.ofSeconds(5), Set.of(RuntimeException.class));
    }

    private static PartialExecutionContext sampleContext() {
        return new PartialExecutionContext(
                ExecutionId.generate(), ScenarioId.of("sc-1"),
                Map.of("task-0", Map.of("agent-1", "result")));
    }

    private static TaskExecutionRequest sampleRequest() {
        return new TaskExecutionRequest(
                MessageId.generate(), ExecutionId.generate(),
                sampleStep(), sampleContext(),
                Instant.now(), defaultRetryPolicy());
    }

    private static ExecutionEvent sampleEvent() {
        return new ExecutionEvent(
                EventId.generate(), ExecutionId.generate(), MessageId.generate(),
                AgentId.generate(), ExecutionEvent.TASK_COMPLETED,
                Map.of("status", "SUCCESS"), Instant.now());
    }

    private static AgentLifecycleEvent sampleLifecycleEvent() {
        return new AgentLifecycleEvent(
                EventId.generate(), AgentId.generate(),
                AgentLifecycleEvent.AGENT_REGISTERED,
                Map.of("agentType", "test"), Instant.now());
    }

    private static AgentSignal sampleSignal() {
        return new ScenarioRestartSignal(
                SignalId.generate(), ExecutionId.generate(),
                "test reason", Instant.now());
    }

    @SuppressWarnings("unchecked")
    private void stubSuccessfulSend() {
        SendResult<String, byte[]> result = mock(SendResult.class);
        var future = CompletableFuture.completedFuture(result);
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(future);
    }


    @Test
    @DisplayName("should reject null KafkaTemplate in constructor")
    void shouldRejectNullKafkaTemplate() {
        assertThatThrownBy(() -> new KafkaExecutionTransport(props, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kafkaTemplate");
    }

    @Test
    @DisplayName("should start disconnected")
    void shouldStartDisconnected() {
        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    @DisplayName("should report connected after connect")
    void shouldReportConnectedAfterConnect() {
        transport.connect();
        assertThat(transport.isConnected()).isTrue();
    }

    @Test
    @DisplayName("should report disconnected after disconnect")
    void shouldReportDisconnectedAfterDisconnect() {
        transport.connect();
        transport.disconnect();
        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    @DisplayName("should flush KafkaTemplate on disconnect")
    void shouldFlushKafkaTemplateOnDisconnect() {
        transport.connect();
        transport.disconnect();
        verify(kafkaTemplate, times(1)).flush();
    }

    @Test
    @DisplayName("should no-op on double disconnect")
    void shouldNoopOnDoubleDisconnect() {
        transport.connect();
        transport.disconnect();
        transport.disconnect();
        verify(kafkaTemplate, times(1)).flush();
    }


    @Test
    @DisplayName("should return KAFKA transport type")
    void shouldReturnKafkaType() {
        assertThat(transport.getType()).isEqualTo(TransportType.KAFKA);
    }

    @Test
    @DisplayName("should send task request to tasks topic")
    void shouldSendTaskRequestToTasksTopic() {
        transport.connect();
        stubSuccessfulSend();
        var request = sampleRequest();
        transport.dispatchTask(request);
        verify(kafkaTemplate).send(eq("agents-tasks"), eq(request.id().value()), any(byte[].class));
    }

    @Test
    @DisplayName("should throw TransportException when dispatching task while disconnected")
    void shouldThrowWhenDispatchingTaskDisconnected() {
        assertThatThrownBy(() -> transport.dispatchTask(sampleRequest()))
                .isInstanceOf(TransportException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("should throw on null dispatchTask parameter")
    void shouldThrowOnNullDispatchTask() {
        transport.connect();
        assertThatThrownBy(() -> transport.dispatchTask(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should send execution event to events topic")
    void shouldSendEventToEventsTopic() {
        transport.connect();
        stubSuccessfulSend();
        var event = sampleEvent();
        transport.publishEvent(event);
        verify(kafkaTemplate).send(eq("agents-events"), eq(event.id().value()), any(byte[].class));
    }

    @Test
    @DisplayName("should throw on null publishEvent parameter")
    void shouldThrowOnNullPublishEvent() {
        transport.connect();
        assertThatThrownBy(() -> transport.publishEvent(null))
                .isInstanceOf(TransportException.class);
    }


    @Test
    @DisplayName("should send agent lifecycle event to events topic")
    void shouldSendAgentLifecycleEventToEventsTopic() {
        transport.connect();
        stubSuccessfulSend();
        var event = sampleLifecycleEvent();
        transport.publishAgentEvent(event);
        verify(kafkaTemplate).send(eq("agents-events"), eq(event.id().value()), any(byte[].class));
    }

    @Test
    @DisplayName("should throw on null publishAgentEvent parameter")
    void shouldThrowOnNullPublishAgentEvent() {
        transport.connect();
        assertThatThrownBy(() -> transport.publishAgentEvent(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should send signal to signals topic")
    void shouldSendSignalToSignalsTopic() {
        transport.connect();
        stubSuccessfulSend();
        var signal = sampleSignal();
        transport.broadcastSignal(signal);
        verify(kafkaTemplate).send(eq("agents-signals"), eq(signal.id().value()), any(byte[].class));
    }

    @Test
    @DisplayName("should throw on null broadcastSignal parameter")
    void shouldThrowOnNullBroadcastSignal() {
        transport.connect();
        assertThatThrownBy(() -> transport.broadcastSignal(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should throw on null receiveTask handler")
    void shouldThrowOnNullReceiveTask() {
        assertThatThrownBy(() -> transport.receiveTask(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should throw on null receiveSignal handler")
    void shouldThrowOnNullReceiveSignal() {
        assertThatThrownBy(() -> transport.receiveSignal(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should throw on null subscribe handler")
    void shouldThrowOnNullSubscribe() {
        assertThatThrownBy(() -> transport.subscribe(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should throw on null subscribeAgentEvents handler")
    void shouldThrowOnNullSubscribeAgentEvents() {
        assertThatThrownBy(() -> transport.subscribeAgentEvents(null))
                .isInstanceOf(TransportException.class);
    }
}
