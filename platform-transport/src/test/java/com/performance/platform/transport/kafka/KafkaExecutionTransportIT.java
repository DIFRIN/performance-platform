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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@DisplayName("KafkaExecutionTransport — integration tests")
@Testcontainers
class KafkaExecutionTransportIT {

    @Container
    @SuppressWarnings("deprecation")
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    private KafkaExecutionTransport transport;
    private KafkaTransportProperties props;
    private KafkaTemplate<String, byte[]> kafkaTemplate;
    private ConsumerFactory<String, byte[]> consumerFactory;

    @BeforeAll
    static void startContainer() {
        // @Container starts automatically
    }

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        props = new KafkaTransportProperties(
                kafka.getBootstrapServers(),
                "agents-tasks-" + uid,
                "agents-events-" + uid,
                "agents-signals-" + uid,
                "all",
                "test-group-" + UUID.randomUUID()
        );
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"
        );
        var producerFactory = new DefaultKafkaProducerFactory<String, byte[]>(config);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true"
        );
        consumerFactory = new DefaultKafkaConsumerFactory<>(consumerConfig);

        var containerFactory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        containerFactory.setConsumerFactory(consumerFactory);
        containerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        transport = new KafkaExecutionTransport(props, kafkaTemplate, containerFactory);
        transport.connect();
    }

    @AfterEach
    void tearDown() {
        if (transport.isConnected()) {
            transport.disconnect();
        }
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    // === Fixtures ===

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

    // === Connection lifecycle ===

    @Test
    @DisplayName("should connect and report connected state")
    void shouldConnect() {
        assertThat(transport.isConnected()).isTrue();
    }

    @Test
    @DisplayName("should disconnect and report disconnected state")
    void shouldDisconnect() {
        transport.disconnect();
        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    @DisplayName("should no-op on double connect")
    void shouldNoopOnDoubleConnect() {
        transport.connect();
        assertThat(transport.isConnected()).isTrue();
    }

    @Test
    @DisplayName("should throw when dispatching while disconnected")
    void shouldThrowWhenDisconnected() {
        transport.disconnect();
        assertThatThrownBy(() -> transport.dispatchTask(sampleRequest()))
                .isInstanceOf(TransportException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    @DisplayName("should reconnect after disconnect")
    void shouldReconnect() {
        transport.disconnect();
        transport.connect();
        assertThat(transport.isConnected()).isTrue();
    }

    // === getType ===

    @Test
    @DisplayName("should return KAFKA transport type")
    void shouldReturnKafkaType() {
        assertThat(transport.getType()).isEqualTo(TransportType.KAFKA);
    }

    // === dispatchTask → receiveTask round-trip ===

    @Test
    @DisplayName("should deliver task request to registered handler")
    void shouldDeliverTaskRequest() {
        var received = new CopyOnWriteArrayList<TaskExecutionRequest>();
        transport.receiveTask(received::add);

        var request = sampleRequest();
        transport.dispatchTask(request);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received).hasSize(1));
        assertThat(received.get(0).id()).isEqualTo(request.id());
    }

    // === publishEvent → subscribe round-trip ===

    @Test
    @DisplayName("should deliver execution event to subscription")
    void shouldDeliverExecutionEvent() {
        var received = new CopyOnWriteArrayList<ExecutionEvent>();
        transport.subscribe(received::add);

        var event = sampleEvent();
        transport.publishEvent(event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received).hasSize(1));
        assertThat(received.get(0).id()).isEqualTo(event.id());
    }

    // === publishAgentEvent → subscribeAgentEvents round-trip ===

    @Test
    @DisplayName("should deliver agent lifecycle event to handler")
    void shouldDeliverAgentLifecycleEvent() {
        var received = new CopyOnWriteArrayList<AgentLifecycleEvent>();
        transport.subscribeAgentEvents(received::add);

        var event = sampleLifecycleEvent();
        transport.publishAgentEvent(event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received).hasSize(1));
        assertThat(received.get(0).id()).isEqualTo(event.id());
    }

    // === broadcastSignal → receiveSignal round-trip ===

    @Test
    @DisplayName("should deliver signal to registered handler")
    void shouldDeliverSignal() {
        var received = new CopyOnWriteArrayList<AgentSignal>();
        transport.receiveSignal(received::add);

        var signal = sampleSignal();
        transport.broadcastSignal(signal);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received).hasSize(1));
        assertThat(received.get(0).id()).isEqualTo(signal.id());
    }

    // === Subscription cancellation ===

    @Test
    @DisplayName("should not deliver events after subscription cancelled")
    void shouldNotDeliverAfterCancellation() {
        var received = new CopyOnWriteArrayList<ExecutionEvent>();
        var sub = transport.subscribe(received::add);

        // Send one event to verify delivery works
        transport.publishEvent(sampleEvent());
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received).hasSize(1));

        // Cancel subscription
        sub.cancel();
        assertThat(sub.isActive()).isFalse();

        // Send another event — should not be delivered
        transport.publishEvent(sampleEvent());
        // Short wait to verify no delivery
        await().pollDelay(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received).hasSize(1));
    }

    // === Multiple handlers ===

    @Test
    @DisplayName("should deliver tasks to multiple handlers")
    void shouldDeliverToMultipleHandlers() {
        var received1 = new CopyOnWriteArrayList<TaskExecutionRequest>();
        var received2 = new CopyOnWriteArrayList<TaskExecutionRequest>();
        transport.receiveTask(received1::add);
        transport.receiveTask(received2::add);

        transport.dispatchTask(sampleRequest());

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(received1).hasSize(1);
                    assertThat(received2).hasSize(1);
                });
    }

    // === Graceful disconnect with handlers ===

    @Test
    @DisplayName("should gracefully disconnect with registered handlers")
    void shouldDisconnectWithHandlers() {
        transport.receiveTask(req -> {});
        transport.receiveSignal(sig -> {});
        transport.subscribe(evt -> {});
        transport.subscribeAgentEvents(evt -> {});

        transport.disconnect();
        assertThat(transport.isConnected()).isFalse();
    }

    // === Null parameter checks ===

    @Test
    @DisplayName("should throw on null dispatchTask parameter")
    void shouldThrowOnNullDispatchTask() {
        assertThatThrownBy(() -> transport.dispatchTask(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should throw on null publishEvent parameter")
    void shouldThrowOnNullPublishEvent() {
        assertThatThrownBy(() -> transport.publishEvent(null))
                .isInstanceOf(TransportException.class);
    }

    @Test
    @DisplayName("should throw on null receiveTask handler")
    void shouldThrowOnNullReceiveTask() {
        assertThatThrownBy(() -> transport.receiveTask(null))
                .isInstanceOf(TransportException.class);
    }
}
