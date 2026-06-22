package com.performance.platform.transport.kafka;

import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.AgentSignalHandler;
import com.performance.platform.transport.ExecutionEventHandler;
import com.performance.platform.transport.TaskRequestHandler;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("DynamicKafkaListenerRegistry")
@ExtendWith(MockitoExtension.class)
class DynamicKafkaListenerRegistryTest {

    @Mock private ConsumerFactory<String, byte[]> consumerFactory;

    private KafkaMessageCodec codec;
    private DynamicKafkaListenerRegistry registry;

    private static final String TASKS = "tasks";
    private static final String SIGNALS = "signals";
    private static final String EVENTS = "events";
    private static final String AGENT = "agent-001";
    private static final String ORCH = "orchestrator";

    @BeforeEach
    void setUp() {
        codec = new KafkaMessageCodec();
        registry = new DynamicKafkaListenerRegistry(consumerFactory, codec, TASKS, SIGNALS, EVENTS);
    }

    @Test
    @DisplayName("registerTaskListener creates container and calls start")
    void shouldCreateTaskContainerOnFirstRegister() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener(AGENT, req -> {});

            assertThat(mocked.constructed()).hasSize(1);
            verify(mocked.constructed().get(0), times(1)).start();
        }
    }

    @Test
    @DisplayName("second registerTaskListener does not create new container")
    void shouldNotCreateSecondContainerForSameAgent() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener(AGENT, req -> {});
            registry.registerTaskListener(AGENT, req -> {});

            assertThat(mocked.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("different agents get separate containers (ADR-009)")
    void shouldCreateSeparateContainersForDifferentAgents() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener("agent-A", req -> {});
            registry.registerTaskListener("agent-B", req -> {});

            assertThat(mocked.constructed()).hasSize(2);
        }
    }

    @Test
    @DisplayName("registerSignalListener creates container")
    void shouldCreateSignalContainer() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerSignalListener(AGENT, sig -> {});

            assertThat(mocked.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("task and signal have separate containers")
    void shouldCreateSeparateTaskAndSignalContainers() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener(AGENT, req -> {});
            registry.registerSignalListener(AGENT, sig -> {});

            assertThat(mocked.constructed()).hasSize(2);
        }
    }

    @Test
    @DisplayName("registerExecutionHandler returns cleanup runnable")
    void shouldReturnCleanupRunnable() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            Runnable cleanup = registry.registerExecutionHandler(ORCH, evt -> {});

            assertThat(cleanup).isNotNull();
            assertThat(mocked.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("cleanup stops container when last handler removed")
    void cleanupShouldStopContainer() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            Runnable cleanup = registry.registerExecutionHandler(ORCH, evt -> {});
            cleanup.run();

            verify(mocked.constructed().get(0), times(1)).stop();
        }
    }

    @Test
    @DisplayName("execution and lifecycle handlers share event container")
    void shouldShareEventContainer() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerExecutionHandler(ORCH, evt -> {});
            registry.registerAgentLifecycleHandler(ORCH, evt -> {});

            assertThat(mocked.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("unregisterAgent stops all agent containers")
    void shouldStopContainersOnUnregister() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener(AGENT, req -> {});
            registry.registerSignalListener(AGENT, sig -> {});

            registry.unregisterAgent(AGENT);

            verify(mocked.constructed().get(0), times(1)).stop();
            verify(mocked.constructed().get(1), times(1)).stop();
        }
    }

    @Test
    @DisplayName("unregisterAgent is no-op for unknown agent")
    void shouldNoopOnUnregisterUnknownAgent() {
        registry.unregisterAgent("unknown-agent");
    }

    @Test
    @DisplayName("stopAll stops all containers")
    void shouldStopAllContainers() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener("a1", req -> {});
            registry.registerSignalListener("a2", sig -> {});
            registry.registerExecutionHandler("g3", evt -> {});

            registry.stopAll();

            assertThat(mocked.constructed()).hasSize(3);
            mocked.constructed().forEach(c -> verify(c, times(1)).stop());
        }
    }

    @Test
    @DisplayName("double stopAll is no-op")
    void shouldNoopOnDoubleStopAll() throws Exception {
        try (MockedConstruction<KafkaMessageListenerContainer> mocked =
                     mockConstruction(KafkaMessageListenerContainer.class,
                             (mock, ctx) -> doNothing().when(mock).start())) {

            registry.registerTaskListener(AGENT, req -> {});
            registry.stopAll();
            registry.stopAll();

            verify(mocked.constructed().get(0), times(1)).stop();
        }
    }
}
