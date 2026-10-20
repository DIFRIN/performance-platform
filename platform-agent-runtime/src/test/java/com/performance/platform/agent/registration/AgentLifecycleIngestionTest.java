package com.performance.platform.agent.registration;

import com.performance.platform.agent.registry.InMemoryAgentRegistry;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.AgentLost;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentLifecycleIngestion")
class AgentLifecycleIngestionTest {

    private InMemoryExecutionTransport transport;
    private InMemoryAgentRegistry registry;
    private AgentLifecycleIngestion ingestion;
    private final List<Object> publishedEvents = new ArrayList<>();
    private AgentDescriptor descriptor;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        transport.connect();
        registry = new InMemoryAgentRegistry();
        ingestion = new AgentLifecycleIngestion(transport, registry, publishedEvents::add);
        ingestion.afterPropertiesSet();

        descriptor = new AgentDescriptor(
                AgentId.of("agent-1"),
                "agent-one",
                "localhost",
                9090,
                null,
                Set.of("gatling", "database-assertion"),
                new AgentCapabilities(4, "1.0.0"),
                AgentState.IDLE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:10Z"),
                Duration.ofMinutes(5)
        );
    }

    @AfterEach
    void tearDown() {
        ingestion.destroy();
        transport.disconnect();
    }

    private void publish(String type, AgentId agentId, Map<String, Object> payload) {
        transport.publishAgentEvent(new AgentLifecycleEvent(
                EventId.generate(), agentId, type, payload, Instant.now()));
    }

    @Nested
    @DisplayName("registration")
    class RegistrationTests {

        @Test
        @DisplayName("should populate the orchestrator registry from a lifecycle event")
        void shouldPopulateRegistry() {
            publish(AgentLifecycleEvent.AGENT_REGISTERED, descriptor.id(),
                    AgentLifecyclePayloadCodec.encode(descriptor));

            assertThat(registry.findById(descriptor.id())).contains(descriptor);
            assertThat(registry.hasAgentFor("gatling")).isTrue();
            assertThat(registry.hasAgentFor("database-assertion")).isTrue();
        }

        @Test
        @DisplayName("should round-trip AgentDescriptor through the payload codec")
        void shouldRoundTripDescriptor() {
            var decoded = AgentLifecyclePayloadCodec.decodeDescriptor(
                    descriptor.id(), AgentLifecyclePayloadCodec.encode(descriptor));

            assertThat(decoded).isEqualTo(descriptor);
        }
    }

    @Nested
    @DisplayName("heartbeat")
    class HeartbeatTests {

        @Test
        @DisplayName("should refresh state and heartbeat timestamp")
        void shouldRefreshHeartbeat() {
            publish(AgentLifecycleEvent.AGENT_REGISTERED, descriptor.id(),
                    AgentLifecyclePayloadCodec.encode(descriptor));

            var heartbeat = new AgentHeartbeat(descriptor.id(), AgentState.EXECUTING, 3,
                    Instant.parse("2026-01-01T00:01:00Z"));
            publish(AgentLifecycleEvent.AGENT_HEARTBEAT, descriptor.id(),
                    AgentLifecyclePayloadCodec.encode(heartbeat));

            var updated = registry.findById(descriptor.id()).orElseThrow();
            assertThat(updated.state()).isEqualTo(AgentState.EXECUTING);
            assertThat(updated.lastHeartbeatAt()).isEqualTo(heartbeat.sentAt());
        }

        @Test
        @DisplayName("should ignore heartbeat for an unknown agent")
        void shouldIgnoreUnknownAgent() {
            var heartbeat = new AgentHeartbeat(AgentId.of("unknown"), AgentState.IDLE, 0, Instant.now());

            publish(AgentLifecycleEvent.AGENT_HEARTBEAT, heartbeat.agentId(),
                    AgentLifecyclePayloadCodec.encode(heartbeat));

            assertThat(registry.findById(heartbeat.agentId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("deregistration and loss")
    class LossTests {

        @Test
        @DisplayName("should remove a deregistered agent")
        void shouldRemoveDeregisteredAgent() {
            publish(AgentLifecycleEvent.AGENT_REGISTERED, descriptor.id(),
                    AgentLifecyclePayloadCodec.encode(descriptor));

            publish(AgentLifecycleEvent.AGENT_DEREGISTERED, descriptor.id(), Map.of());

            assertThat(registry.findById(descriptor.id())).isEmpty();
        }

        @Test
        @DisplayName("should remove a lost agent and publish AgentLost")
        void shouldRemoveLostAgentAndPublishEvent() {
            publish(AgentLifecycleEvent.AGENT_REGISTERED, descriptor.id(),
                    AgentLifecyclePayloadCodec.encode(descriptor));

            publish(AgentLifecycleEvent.AGENT_LOST, descriptor.id(), Map.of());

            assertThat(registry.findById(descriptor.id())).isEmpty();
            assertThat(publishedEvents)
                    .filteredOn(AgentLost.class::isInstance)
                    .singleElement()
                    .satisfies(e -> assertThat(((AgentLost) e).agentId())
                            .isEqualTo(descriptor.id()));
        }
    }

    @Nested
    @DisplayName("subscription lifecycle")
    class SubscriptionTests {

        @Test
        @DisplayName("should stop receiving events after destroy")
        void shouldStopReceivingAfterDestroy() {
            ingestion.destroy();

            publish(AgentLifecycleEvent.AGENT_REGISTERED, descriptor.id(),
                    AgentLifecyclePayloadCodec.encode(descriptor));

            assertThat(registry.findById(descriptor.id())).isEmpty();
        }
    }
}
