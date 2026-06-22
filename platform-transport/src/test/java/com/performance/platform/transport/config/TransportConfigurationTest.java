package com.performance.platform.transport.config;

import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportType;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de binding des {@code @ConfigurationProperties} et de la
 * {@code TransportConfiguration}.
 */
class TransportConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TransportConfiguration.class, KafkaTransportBeans.class);

    // === Kafka Properties Binding ===

    @Test
    void shouldBindFullKafkaProperties() {
        runner.withPropertyValues(
                "transport.kafka.bootstrap-servers=broker1:9092,broker2:9092",
                "transport.kafka.tasks-topic=agents-tasks",
                "transport.kafka.events-topic=agents-events",
                "transport.kafka.signals-topic=agents-signals",
                "transport.kafka.producer-acks=1",
                "transport.kafka.consumer-group=agent-001"
        ).run(ctx -> {
            var props = ctx.getBean(KafkaTransportProperties.class);
            assertThat(props.bootstrapServers()).isEqualTo("broker1:9092,broker2:9092");
            assertThat(props.tasksTopic()).isEqualTo("agents-tasks");
            assertThat(props.eventsTopic()).isEqualTo("agents-events");
            assertThat(props.signalsTopic()).isEqualTo("agents-signals");
            assertThat(props.producerAcks()).isEqualTo("1");
            assertThat(props.consumerGroup()).isEqualTo("agent-001");
        });
    }

    @Test
    void shouldBindPartialKafkaPropertiesWithNullDefaults() {
        runner.withPropertyValues(
                "transport.kafka.bootstrap-servers=localhost:9092",
                "transport.kafka.tasks-topic=tasks-topic"
        ).run(ctx -> {
            var props = ctx.getBean(KafkaTransportProperties.class);
            assertThat(props.bootstrapServers()).isEqualTo("localhost:9092");
            assertThat(props.tasksTopic()).isEqualTo("tasks-topic");
            assertThat(props.eventsTopic()).isNull();
            assertThat(props.signalsTopic()).isNull();
            assertThat(props.producerAcks()).isNull();
            assertThat(props.consumerGroup()).isNull();
        });
    }

    // === RabbitMQ Properties Binding ===

    @Test
    void shouldBindFullRabbitMQProperties() {
        runner.withPropertyValues(
                "transport.rabbitmq.host=rabbitmq.example.com",
                "transport.rabbitmq.port=5672",
                "transport.rabbitmq.virtual-host=/production",
                "transport.rabbitmq.tasks-exchange=tasks.exchange",
                "transport.rabbitmq.events-exchange=events.exchange",
                "transport.rabbitmq.signals-exchange=signals.exchange",
                "transport.rabbitmq.username=admin",
                "transport.rabbitmq.password=secret"
        ).run(ctx -> {
            var props = ctx.getBean(RabbitMQTransportProperties.class);
            assertThat(props.host()).isEqualTo("rabbitmq.example.com");
            assertThat(props.port()).isEqualTo(5672);
            assertThat(props.virtualHost()).isEqualTo("/production");
            assertThat(props.tasksExchange()).isEqualTo("tasks.exchange");
            assertThat(props.eventsExchange()).isEqualTo("events.exchange");
            assertThat(props.signalsExchange()).isEqualTo("signals.exchange");
            assertThat(props.username()).isEqualTo("admin");
            assertThat(props.password()).isEqualTo("secret");
        });
    }

    @Test
    void shouldBindPartialRabbitMQPropertiesWithDefaultPort() {
        runner.withPropertyValues(
                "transport.rabbitmq.host=localhost"
        ).run(ctx -> {
            var props = ctx.getBean(RabbitMQTransportProperties.class);
            assertThat(props.host()).isEqualTo("localhost");
            assertThat(props.port()).isEqualTo(0); // int default
        });
    }

    // === HTTP Properties Binding ===

    @Test
    void shouldBindFullHttpProperties() {
        runner.withPropertyValues(
                "transport.http.broadcast-mode=ALL_CAPABLE",
                "transport.http.request-timeout-seconds=60",
                "transport.http.task-availability-timeout-seconds=300",
                "transport.http.callback-base-path=/api/callbacks"
        ).run(ctx -> {
            var props = ctx.getBean(HttpTransportProperties.class);
            assertThat(props.broadcastMode()).isEqualTo("ALL_CAPABLE");
            assertThat(props.requestTimeoutSeconds()).isEqualTo(60);
            assertThat(props.taskAvailabilityTimeoutSeconds()).isEqualTo(300);
            assertThat(props.callbackBasePath()).isEqualTo("/api/callbacks");
        });
    }

    // === Socket Properties Binding ===

    @Test
    void shouldBindFullSocketProperties() {
        runner.withPropertyValues(
                "transport.socket.orchestrator-host=10.0.0.1",
                "transport.socket.orchestrator-port=9090",
                "transport.socket.backlog=100",
                "transport.socket.keep-alive=true",
                "transport.socket.reconnect-interval-ms=3000"
        ).run(ctx -> {
            var props = ctx.getBean(SocketTransportProperties.class);
            assertThat(props.orchestratorHost()).isEqualTo("10.0.0.1");
            assertThat(props.orchestratorPort()).isEqualTo(9090);
            assertThat(props.backlog()).isEqualTo(100);
            assertThat(props.keepAlive()).isTrue();
            assertThat(props.reconnectIntervalMs()).isEqualTo(3000);
        });
    }

    @Test
    void shouldBindSocketKeepAliveFalse() {
        runner.withPropertyValues(
                "transport.socket.orchestrator-host=localhost",
                "transport.socket.keep-alive=false"
        ).run(ctx -> {
            var props = ctx.getBean(SocketTransportProperties.class);
            assertThat(props.keepAlive()).isFalse();
        });
    }

    // === Transport Bean Selection ===

    @Test
    void shouldCreateInMemoryTransportByDefault() {
        runner.run(ctx -> {
            assertThat(ctx.containsBean("inMemoryExecutionTransport")).isTrue();
            assertThat(ctx.containsBean("kafkaExecutionTransport")).isFalse();
            assertThat(ctx.containsBean("rabbitMQExecutionTransport")).isFalse();
            assertThat(ctx.containsBean("httpExecutionTransport")).isFalse();
            assertThat(ctx.containsBean("socketExecutionTransport")).isFalse();

            var transport = ctx.getBean(ExecutionTransport.class);
            assertThat(transport).isInstanceOf(InMemoryExecutionTransport.class);
            assertThat(transport.getType()).isEqualTo(TransportType.IN_MEMORY);
        });
    }

    @Test
    void shouldNotRegisterNonInMemoryTransportBeansByDefault() {
        runner.run(ctx -> {
            assertThat(ctx.containsBean("kafkaExecutionTransport")).isFalse();
            assertThat(ctx.containsBean("rabbitMQExecutionTransport")).isFalse();
            assertThat(ctx.containsBean("httpExecutionTransport")).isFalse();
            assertThat(ctx.containsBean("socketExecutionTransport")).isFalse();
        });
    }

    @Test
    void shouldRegisterInMemoryTransportWhenExplicitlySet() {
        runner.withPropertyValues("transport.type=IN_MEMORY")
                .run(ctx -> {
                    var transport = ctx.getBean(ExecutionTransport.class);
                    assertThat(transport).isInstanceOf(InMemoryExecutionTransport.class);
                });
    }

    @Test
    void shouldRegisterKafkaTransportWhenTypeKafka() {
        runner.withPropertyValues(
                "transport.type=KAFKA",
                "transport.kafka.bootstrap-servers=localhost:9092",
                "transport.kafka.tasks-topic=tasks",
                "transport.kafka.events-topic=events",
                "transport.kafka.signals-topic=signals",
                "transport.kafka.producer-acks=all",
                "transport.kafka.consumer-group=test"
        ).run(ctx -> {
            assertThat(ctx.containsBean("kafkaExecutionTransport")).isTrue();
            assertThat(ctx.containsBean("inMemoryExecutionTransport")).isFalse();
        });
    }

    @Test
    void shouldRegisterRabbitMQSkeletonWhenTypeRabbitMQ() {
        runner.withPropertyValues("transport.type=RABBITMQ")
                .run(ctx -> {
                    assertThat(ctx.containsBean("rabbitMQExecutionTransport")).isTrue();
                    assertThat(ctx.containsBean("inMemoryExecutionTransport")).isFalse();
                });
    }

    @Test
    void shouldRegisterHttpSkeletonWhenTypeHttp() {
        runner.withPropertyValues("transport.type=HTTP")
                .run(ctx -> {
                    assertThat(ctx.containsBean("httpExecutionTransport")).isTrue();
                    assertThat(ctx.containsBean("inMemoryExecutionTransport")).isFalse();
                });
    }

    @Test
    void shouldRegisterSocketSkeletonWhenTypeSocket() {
        runner.withPropertyValues("transport.type=SOCKET")
                .run(ctx -> {
                    assertThat(ctx.containsBean("socketExecutionTransport")).isTrue();
                    assertThat(ctx.containsBean("inMemoryExecutionTransport")).isFalse();
                });
    }
}
