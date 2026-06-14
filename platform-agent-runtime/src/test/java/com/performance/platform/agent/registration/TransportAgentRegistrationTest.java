package com.performance.platform.agent.registration;

import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransportAgentRegistration")
class TransportAgentRegistrationTest {

    private InMemoryExecutionTransport transport;
    private TransportAgentRegistration registration;
    private AgentId agentId;
    private AgentDescriptor descriptor;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        registration = new TransportAgentRegistration(transport);
        agentId = AgentId.generate();
        descriptor = new AgentDescriptor(
                agentId,
                "test-agent",
                "localhost",
                9090,
                "http://localhost:9090/callback",
                Set.of("http-get", "kafka-produce"),
                new AgentCapabilities(10, "1.0.0"),
                AgentState.IDLE,
                Instant.now(),
                Instant.now(),
                Duration.ofSeconds(30)
        );
    }

    // === Fixtures ===

    private CopyOnWriteArrayList<ExecutionEvent> captureEvents() {
        var events = new CopyOnWriteArrayList<ExecutionEvent>();
        transport.subscribe(events::add);
        return events;
    }

    // === Tests ===

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should publish AGENT_REGISTERED event")
        void shouldPublishAgentRegisteredEvent() {
            var events = captureEvents();
            registration.register(descriptor);

            assertThat(events).hasSize(1);
            var event = events.get(0);
            assertThat(event.eventType()).isEqualTo(ExecutionEvent.AGENT_REGISTERED);
            assertThat(event.agentId()).isEqualTo(agentId);
        }

        @Test
        @DisplayName("should include descriptor fields in payload")
        void shouldIncludeDescriptorFieldsInPayload() {
            var events = captureEvents();
            registration.register(descriptor);

            var payload = events.get(0).payload();
            assertThat(payload).containsEntry("agentId", agentId.value());
            assertThat(payload).containsEntry("name", "test-agent");
            assertThat(payload).containsEntry("host", "localhost");
            assertThat(payload).containsEntry("port", 9090);
            assertThat(payload).containsEntry("httpCallbackUrl", "http://localhost:9090/callback");
            assertThat(payload).containsEntry("state", "IDLE");
            assertThat(payload).containsKey("supportedTaskNames");
            assertThat(payload).containsKey("capabilities");
            assertThat(payload).containsKey("registeredAt");
            assertThat(payload).containsKey("registrationTtlSeconds");
        }

        @Test
        @DisplayName("should include capabilities in payload")
        void shouldIncludeCapabilitiesInPayload() {
            var events = captureEvents();
            registration.register(descriptor);

            @SuppressWarnings("unchecked")
            var caps = (java.util.Map<String, Object>) events.get(0).payload().get("capabilities");
            assertThat(caps).containsEntry("maxConcurrentTasks", 10);
            assertThat(caps).containsEntry("version", "1.0.0");
        }

        @Test
        @DisplayName("should reject null descriptor")
        void shouldRejectNullDescriptor() {
            assertThatThrownBy(() -> registration.register(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should wrap TransportException in RegistrationException")
        void shouldWrapTransportException() {
            var broken = new ExecutionTransport() {
                public void publishEvent(ExecutionEvent e) { throw new TransportException("down"); }
                public void dispatchTask(com.performance.platform.transport.message.TaskExecutionRequest r) {}
                public void broadcastSignal(com.performance.platform.domain.event.AgentSignal s) {}
                public com.performance.platform.transport.Subscription subscribe(com.performance.platform.transport.ExecutionEventHandler h) {
                    return new com.performance.platform.transport.Subscription() {
                        private boolean active = true;
                        public void cancel() { active = false; }
                        public boolean isActive() { return active; }
                    };
                }
                public void receiveTask(com.performance.platform.transport.TaskRequestHandler h) {}
                public void receiveSignal(com.performance.platform.transport.AgentSignalHandler h) {}
                public void connect() {}
                public void disconnect() {}
                public boolean isConnected() { return false; }
                public com.performance.platform.transport.TransportType getType() { return com.performance.platform.transport.TransportType.IN_MEMORY; }
            };
            var brokenReg = new TransportAgentRegistration(broken);

            assertThatThrownBy(() -> brokenReg.register(descriptor))
                    .isInstanceOf(RegistrationException.class)
                    .hasMessageContaining("Failed to register agent")
                    .hasCauseInstanceOf(TransportException.class);
        }
    }

    @Nested
    @DisplayName("deregister")
    class Deregister {

        @Test
        @DisplayName("should publish AGENT_DEREGISTERED event")
        void shouldPublishAgentDeregisteredEvent() {
            var events = captureEvents();
            registration.deregister(agentId);

            assertThat(events).hasSize(1);
            var event = events.get(0);
            assertThat(event.eventType()).isEqualTo(ExecutionEvent.AGENT_DEREGISTERED);
            assertThat(event.agentId()).isEqualTo(agentId);
        }

        @Test
        @DisplayName("should include agentId in payload")
        void shouldIncludeAgentIdInPayload() {
            var events = captureEvents();
            registration.deregister(agentId);

            assertThat(events.get(0).payload()).containsEntry("agentId", agentId.value());
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> registration.deregister(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("sendHeartbeat")
    class SendHeartbeat {

        @Test
        @DisplayName("should publish AGENT_HEARTBEAT event")
        void shouldPublishAgentHeartbeatEvent() {
            var events = captureEvents();
            var heartbeat = new AgentHeartbeat(agentId, AgentState.IDLE, 0, Instant.now());
            registration.sendHeartbeat(agentId, heartbeat);

            assertThat(events).hasSize(1);
            var event = events.get(0);
            assertThat(event.eventType()).isEqualTo(ExecutionEvent.AGENT_HEARTBEAT);
            assertThat(event.agentId()).isEqualTo(agentId);
        }

        @Test
        @DisplayName("should include heartbeat fields in payload")
        void shouldIncludeHeartbeatFieldsInPayload() {
            var events = captureEvents();
            var now = Instant.now();
            var heartbeat = new AgentHeartbeat(agentId, AgentState.EXECUTING, 3, now);
            registration.sendHeartbeat(agentId, heartbeat);

            var payload = events.get(0).payload();
            assertThat(payload).containsEntry("agentId", agentId.value());
            assertThat(payload).containsEntry("state", "EXECUTING");
            assertThat(payload).containsEntry("activeTasks", 3);
            assertThat(payload).containsEntry("sentAt", now.toString());
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            var heartbeat = new AgentHeartbeat(agentId, AgentState.IDLE, 0, Instant.now());
            assertThatThrownBy(() -> registration.sendHeartbeat(null, heartbeat))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null heartbeat")
        void shouldRejectNullHeartbeat() {
            assertThatThrownBy(() -> registration.sendHeartbeat(agentId, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null transport")
        void shouldRejectNullTransport() {
            assertThatThrownBy(() -> new TransportAgentRegistration(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("transport");
        }
    }

    @Nested
    @DisplayName("HeartbeatScheduler")
    class HeartbeatSchedulerTests {

        @Test
        @DisplayName("should send heartbeats periodically")
        void shouldSendHeartbeatsPeriodically() throws InterruptedException {
            var latch = new CountDownLatch(2);
            var events = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(e -> {
                events.add(e);
                latch.countDown();
            });
            var scheduler = new HeartbeatScheduler(registration, agentId, 1, 0);

            scheduler.start();
            var received = latch.await(5, TimeUnit.SECONDS);
            scheduler.stop();

            assertThat(received).isTrue(); // latch released before timeout
            assertThat(scheduler.heartbeatCount()).isGreaterThanOrEqualTo(2);
            assertThat(events).isNotEmpty();
            assertThat(events.stream().allMatch(e -> e.eventType().equals(ExecutionEvent.AGENT_HEARTBEAT))).isTrue();
        }

        @Test
        @DisplayName("should respect ttl >= 3 × interval")
        void shouldRespectTtlGreaterOrEqualTo3TimesInterval() {
            var scheduler = new HeartbeatScheduler(registration, agentId, 5, 0);
            assertThat(scheduler.registrationTtlSeconds()).isEqualTo(15);
        }

        @Test
        @DisplayName("should report running state correctly")
        void shouldReportRunningStateCorrectly() throws InterruptedException {
            var latch = new CountDownLatch(1);
            transport.subscribe(e -> latch.countDown());
            var scheduler = new HeartbeatScheduler(registration, agentId, 1, 0);

            assertThat(scheduler.isRunning()).isFalse();
            scheduler.start();
            assertThat(scheduler.isRunning()).isTrue();
            // wait for at least one heartbeat to confirm scheduling is active
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.stop();
            assertThat(scheduler.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should start with zero heartbeats")
        void shouldStartWithZeroHeartbeats() {
            var scheduler = new HeartbeatScheduler(registration, agentId, 1, 0);
            assertThat(scheduler.heartbeatCount()).isZero();
        }

        @Test
        @DisplayName("should reject intervalSeconds < 1")
        void shouldRejectIntervalTooSmall() {
            assertThatThrownBy(() -> new HeartbeatScheduler(registration, agentId, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("intervalSeconds");
        }

        @Test
        @DisplayName("should reject null registrationPort")
        void shouldRejectNullPort() {
            assertThatThrownBy(() -> new HeartbeatScheduler(null, agentId, 1, 0))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> new HeartbeatScheduler(registration, null, 1, 0))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
