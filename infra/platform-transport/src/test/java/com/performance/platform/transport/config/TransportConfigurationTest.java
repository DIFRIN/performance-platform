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
    void shouldRegisterHttpSkeletonWhenTypeHttp() {
        runner.withPropertyValues("transport.type=HTTP")
                .run(ctx -> {
                    assertThat(ctx.containsBean("httpExecutionTransport")).isTrue();
                    assertThat(ctx.containsBean("inMemoryExecutionTransport")).isFalse();
                });
    }

}
