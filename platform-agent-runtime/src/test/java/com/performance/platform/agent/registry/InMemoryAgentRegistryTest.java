package com.performance.platform.agent.registry;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryAgentRegistry")
class InMemoryAgentRegistryTest {

    private InMemoryAgentRegistry registry;
    private AgentId agentId1;
    private AgentId agentId2;
    private AgentDescriptor agent1;
    private AgentDescriptor agent2;

    @BeforeEach
    void setUp() {
        registry = new InMemoryAgentRegistry();
        agentId1 = AgentId.generate();
        agentId2 = AgentId.generate();
        agent1 = createDescriptor(agentId1, "agent-1", Set.of("http-get", "kafka-produce"));
        agent2 = createDescriptor(agentId2, "agent-2", Set.of("database-query", "kafka-produce"));
    }

    private static AgentDescriptor createDescriptor(AgentId id, String name, Set<String> tasks) {
        return new AgentDescriptor(
                id, name, "localhost", 9090, "http://localhost:9090/callback",
                tasks, new AgentCapabilities(10, "1.0.0"), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
        );
    }

    // === Interface compliance ===

    @Test
    @DisplayName("AgentRegistry should extend AgentRegistryPort")
    void shouldExtendAgentRegistryPort() {
        assertThat(registry).isInstanceOf(AgentRegistryPort.class);
        assertThat(registry).isInstanceOf(AgentRegistry.class);
    }

    // === Registration ===

    @Nested
    @DisplayName("onAgentRegistered")
    class Registration {

        @Test
        @DisplayName("should store agent and retrieve by id")
        void shouldStoreAndRetrieveById() {
            registry.onAgentRegistered(agent1);
            var found = registry.findById(agentId1);
            assertThat(found).isPresent();
            assertThat(found.get().name()).isEqualTo("agent-1");
        }

        @Test
        @DisplayName("should include agent in findAll")
        void shouldIncludeInFindAll() {
            registry.onAgentRegistered(agent1);
            registry.onAgentRegistered(agent2);
            assertThat(registry.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("should reject null descriptor")
        void shouldRejectNullDescriptor() {
            assertThatThrownBy(() -> registry.onAgentRegistered(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === Heartbeat ===

    @Nested
    @DisplayName("onAgentHeartbeat")
    class Heartbeat {

        @Test
        @DisplayName("should update agent state and lastHeartbeatAt")
        void shouldUpdateStateAndHeartbeat() {
            registry.onAgentRegistered(agent1);
            var newTime = Instant.now().plusSeconds(10);
            var heartbeat = new AgentHeartbeat(agentId1, AgentState.EXECUTING, 3, newTime);

            registry.onAgentHeartbeat(agentId1, heartbeat);

            var updated = registry.findById(agentId1).orElseThrow();
            assertThat(updated.state()).isEqualTo(AgentState.EXECUTING);
            assertThat(updated.lastHeartbeatAt()).isEqualTo(newTime);
            // Original fields preserved
            assertThat(updated.name()).isEqualTo("agent-1");
            assertThat(updated.supportedTaskNames()).containsExactlyInAnyOrder("http-get", "kafka-produce");
        }

        @Test
        @DisplayName("should be no-op for unknown agent")
        void shouldBeNoOpForUnknownAgent() {
            var heartbeat = new AgentHeartbeat(agentId1, AgentState.IDLE, 0, Instant.now());
            registry.onAgentHeartbeat(agentId1, heartbeat); // no exception
            assertThat(registry.findById(agentId1)).isEmpty();
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            var hb = new AgentHeartbeat(agentId1, AgentState.IDLE, 0, Instant.now());
            assertThatThrownBy(() -> registry.onAgentHeartbeat(null, hb))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null heartbeat")
        void shouldRejectNullHeartbeat() {
            assertThatThrownBy(() -> registry.onAgentHeartbeat(agentId1, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === Deregistration ===

    @Nested
    @DisplayName("onAgentDeregistered")
    class Deregistration {

        @Test
        @DisplayName("should remove agent from registry")
        void shouldRemoveAgent() {
            registry.onAgentRegistered(agent1);
            registry.onAgentDeregistered(agentId1);
            assertThat(registry.findById(agentId1)).isEmpty();
        }

        @Test
        @DisplayName("should be no-op for unknown agent")
        void shouldBeNoOpForUnknownAgent() {
            registry.onAgentDeregistered(agentId1); // no exception
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> registry.onAgentDeregistered(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === Expiration ===

    @Nested
    @DisplayName("onAgentExpired")
    class Expiration {

        @Test
        @DisplayName("should remove expired agent")
        void shouldRemoveExpiredAgent() {
            registry.onAgentRegistered(agent1);
            registry.onAgentExpired(agentId1);
            assertThat(registry.findById(agentId1)).isEmpty();
        }

        @Test
        @DisplayName("should be no-op for unknown agent")
        void shouldBeNoOpForUnknownAgent() {
            registry.onAgentExpired(agentId1); // no exception
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> registry.onAgentExpired(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === findByTaskName ===

    @Nested
    @DisplayName("findByTaskName")
    class FindByTaskName {

        @Test
        @DisplayName("should return all agents with matching task")
        void shouldReturnAllMatchingAgents() {
            registry.onAgentRegistered(agent1);
            registry.onAgentRegistered(agent2);

            // kafka-produce is supported by both agents
            var agents = registry.findByTaskName("kafka-produce");
            assertThat(agents).hasSize(2);
            assertThat(agents).extracting(AgentDescriptor::name)
                    .containsExactlyInAnyOrder("agent-1", "agent-2");
        }

        @Test
        @DisplayName("should return single agent for unique task")
        void shouldReturnSingleAgent() {
            registry.onAgentRegistered(agent1);
            registry.onAgentRegistered(agent2);

            var agents = registry.findByTaskName("http-get");
            assertThat(agents).hasSize(1);
            assertThat(agents.get(0).name()).isEqualTo("agent-1");
        }

        @Test
        @DisplayName("should return empty list for unknown task")
        void shouldReturnEmptyListForUnknownTask() {
            registry.onAgentRegistered(agent1);
            var agents = registry.findByTaskName("grpc-call");
            assertThat(agents).isEmpty();
        }

        @Test
        @DisplayName("should reject null taskName")
        void shouldRejectNullTaskName() {
            assertThatThrownBy(() -> registry.findByTaskName(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === hasAgentFor ===

    @Nested
    @DisplayName("hasAgentFor")
    class HasAgentFor {

        @Test
        @DisplayName("should return true when agent supports task")
        void shouldReturnTrue() {
            registry.onAgentRegistered(agent1);
            assertThat(registry.hasAgentFor("http-get")).isTrue();
        }

        @Test
        @DisplayName("should return false when no agent supports task")
        void shouldReturnFalse() {
            registry.onAgentRegistered(agent1);
            assertThat(registry.hasAgentFor("grpc-call")).isFalse();
        }

        @Test
        @DisplayName("should return false for empty registry")
        void shouldReturnFalseForEmptyRegistry() {
            assertThat(registry.hasAgentFor("http-get")).isFalse();
        }

        @Test
        @DisplayName("should reject null taskName")
        void shouldRejectNullTaskNameInHasAgentFor() {
            assertThatThrownBy(() -> registry.hasAgentFor(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === findById ===

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return empty for unknown id")
        void shouldReturnEmptyForUnknownId() {
            assertThat(registry.findById(AgentId.generate())).isEmpty();
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentIdInFindById() {
            assertThatThrownBy(() -> registry.findById(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // === findAll ===

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return empty list initially")
        void shouldReturnEmptyListInitially() {
            assertThat(registry.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should return all registered agents")
        void shouldReturnAllRegisteredAgents() {
            registry.onAgentRegistered(agent1);
            registry.onAgentRegistered(agent2);
            assertThat(registry.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("should return unmodifiable copy")
        void shouldReturnUnmodifiableCopy() {
            registry.onAgentRegistered(agent1);
            var all = registry.findAll();
            assertThatThrownBy(() -> all.add(agent2))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // === agentCount ===

    @Test
    @DisplayName("should track agent count")
    void shouldTrackAgentCount() {
        assertThat(registry.agentCount()).isZero();
        registry.onAgentRegistered(agent1);
        assertThat(registry.agentCount()).isEqualTo(1);
        registry.onAgentExpired(agentId1);
        assertThat(registry.agentCount()).isZero();
    }

    // === findExpired ===

    @Nested
    @DisplayName("findExpired")
    class FindExpired {

        @Test
        @DisplayName("should detect agent with expired TTL")
        void shouldDetectExpiredAgent() {
            var pastHeartbeat = Instant.now().minusSeconds(60);
            var shortTtl = createDescriptor(
                    AgentId.generate(), "short-ttl",
                    Set.of("test"),
                    Duration.ofSeconds(30), // TTL 30s
                    pastHeartbeat // last heartbeat 60s ago → expired
            );
            registry.onAgentRegistered(shortTtl);

            var expired = registry.findExpired(Instant.now());
            assertThat(expired).hasSize(1);
        }

        @Test
        @DisplayName("should not detect agent with valid TTL")
        void shouldNotDetectValidAgent() {
            registry.onAgentRegistered(agent1); // TTL 30s, heartbeat now → valid

            var expired = registry.findExpired(Instant.now());
            assertThat(expired).isEmpty();
        }

        @Test
        @DisplayName("should detect multiple expired agents")
        void shouldDetectMultipleExpired() {
            var pastHeartbeat = Instant.now().minusSeconds(60);
            var expired1 = createDescriptor(AgentId.generate(), "exp-1", Set.of("t1"), Duration.ofSeconds(30), pastHeartbeat);
            var expired2 = createDescriptor(AgentId.generate(), "exp-2", Set.of("t2"), Duration.ofSeconds(10), pastHeartbeat);
            registry.onAgentRegistered(expired1);
            registry.onAgentRegistered(expired2);
            registry.onAgentRegistered(agent1); // still valid

            var expired = registry.findExpired(Instant.now());
            assertThat(expired).hasSize(2);
        }
    }

    // === AgentTtlMonitor ===

    @Nested
    @DisplayName("AgentTtlMonitor")
    class TtlMonitor {

        @Test
        @DisplayName("should detect and expire agents with short TTL")
        void shouldDetectAndExpireAgents() throws InterruptedException {
            // Agent with 1s TTL, heartbeat 2s ago → already expired
            var pastHeartbeat = Instant.now().minusSeconds(2);
            var shortTtlAgent = createDescriptor(
                    AgentId.generate(), "short-ttl",
                    Set.of("test"),
                    Duration.ofSeconds(1),
                    pastHeartbeat
            );
            registry.onAgentRegistered(shortTtlAgent);

            var expiredLatch = new CountDownLatch(1);
            var monitor = new AgentTtlMonitor(registry, 1, agentId -> expiredLatch.countDown());

            monitor.start();
            var received = expiredLatch.await(5, TimeUnit.SECONDS);
            monitor.stop();

            assertThat(received).isTrue(); // latch released before timeout
            assertThat(monitor.expiredCount()).isGreaterThanOrEqualTo(1);
            assertThat(registry.findById(shortTtlAgent.id())).isEmpty();
        }

        @Test
        @DisplayName("should invoke onExpired callback for each expired agent")
        void shouldInvokeOnExpiredCallback() throws InterruptedException {
            var pastHeartbeat = Instant.now().minusSeconds(2);
            var shortTtlAgent = createDescriptor(
                    AgentId.generate(), "callback-test",
                    Set.of("test"),
                    Duration.ofSeconds(1),
                    pastHeartbeat
            );
            registry.onAgentRegistered(shortTtlAgent);

            var expiredAgents = new java.util.concurrent.CopyOnWriteArrayList<AgentId>();
            var expiredLatch = new CountDownLatch(1);
            var monitor = new AgentTtlMonitor(registry, 1, agentId -> {
                expiredAgents.add(agentId);
                expiredLatch.countDown();
            });

            monitor.start();
            assertThat(expiredLatch.await(5, TimeUnit.SECONDS)).isTrue();
            monitor.stop();

            assertThat(expiredAgents).contains(shortTtlAgent.id());
        }

        @Test
        @DisplayName("should report running state correctly")
        void shouldReportRunningState() {
            var monitor = new AgentTtlMonitor(registry, 1);
            assertThat(monitor.isRunning()).isFalse();
            monitor.start();
            assertThat(monitor.isRunning()).isTrue();
            monitor.stop();
            assertThat(monitor.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should start with zero expired count")
        void shouldStartWithZeroExpiredCount() {
            var monitor = new AgentTtlMonitor(registry, 1);
            assertThat(monitor.expiredCount()).isZero();
        }

        @Test
        @DisplayName("should reject null registry")
        void shouldRejectNullRegistry() {
            assertThatThrownBy(() -> new AgentTtlMonitor(null, 1))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject zero check interval")
        void shouldRejectZeroCheckInterval() {
            assertThatThrownBy(() -> new AgentTtlMonitor(registry, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checkIntervalSeconds");
        }
    }

    // === Helper ===

    private static AgentDescriptor createDescriptor(AgentId id, String name, Set<String> tasks,
                                                     Duration ttl, Instant lastHeartbeat) {
        return new AgentDescriptor(
                id, name, "localhost", 9090, "http://localhost:9090",
                tasks, new AgentCapabilities(10, "1.0.0"), AgentState.IDLE,
                Instant.now(), lastHeartbeat, ttl
        );
    }
}
