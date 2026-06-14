package com.performance.platform.engine.availability;

import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.id.AgentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultAgentAvailabilityChecker")
class DefaultAgentAvailabilityCheckerTest {

    // -------------------------------------------------------------------
    // Stub implementations de AgentRegistryPort
    // -------------------------------------------------------------------

    /**
     * Stub qui retourne une reponse fixe pour {@code hasAgentFor}.
     */
    static class FixedResponseStub implements AgentRegistryPort {
        private final boolean value;

        FixedResponseStub(boolean value) {
            this.value = value;
        }

        @Override
        public boolean hasAgentFor(String taskName) {
            return value;
        }

        // Methodes non utilisees — retour trivial
        @Override public void onAgentRegistered(AgentDescriptor descriptor) {}
        @Override public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {}
        @Override public void onAgentExpired(AgentId agentId) {}
        @Override public void onAgentDeregistered(AgentId agentId) {}
        @Override public List<AgentDescriptor> findByTaskName(String taskName) { return Collections.emptyList(); }
        @Override public Optional<AgentDescriptor> findById(AgentId agentId) { return Optional.empty(); }
        @Override public List<AgentDescriptor> findAll() { return Collections.emptyList(); }
    }

    /**
     * Stub qui devient disponible apres un certain nombre d'appels a {@code hasAgentFor}.
     */
    static class DelayedStub implements AgentRegistryPort {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final int requiredPolls;

        DelayedStub(int requiredPolls) {
            this.requiredPolls = requiredPolls;
        }

        @Override
        public boolean hasAgentFor(String taskName) {
            return counter.incrementAndGet() > requiredPolls;
        }

        // Methodes non utilisees — retour trivial
        @Override public void onAgentRegistered(AgentDescriptor descriptor) {}
        @Override public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {}
        @Override public void onAgentExpired(AgentId agentId) {}
        @Override public void onAgentDeregistered(AgentId agentId) {}
        @Override public List<AgentDescriptor> findByTaskName(String taskName) { return Collections.emptyList(); }
        @Override public Optional<AgentDescriptor> findById(AgentId agentId) { return Optional.empty(); }
        @Override public List<AgentDescriptor> findAll() { return Collections.emptyList(); }
    }

    // -------------------------------------------------------------------
    // hasAgentFor
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("hasAgentFor")
    class HasAgentFor {

        @Test
        @DisplayName("returns true when registry reports agent present")
        void returnsTrueWhenAgentAvailable() {
            var registry = new FixedResponseStub(true);
            var checker = new DefaultAgentAvailabilityChecker(registry);

            assertTrue(checker.hasAgentFor("db-query"));
        }

        @Test
        @DisplayName("returns false when registry reports no agent")
        void returnsFalseWhenAgentUnavailable() {
            var registry = new FixedResponseStub(false);
            var checker = new DefaultAgentAvailabilityChecker(registry);

            assertFalse(checker.hasAgentFor("http-request"));
        }
    }

    // -------------------------------------------------------------------
    // awaitAgentFor
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("awaitAgentFor")
    class AwaitAgentFor {

        @Test
        @DisplayName("returns immediately when agent is already available")
        void returnsImmediatelyWhenAgentAvailable() {
            var registry = new FixedResponseStub(true);
            var checker = new DefaultAgentAvailabilityChecker(registry);

            long start = System.currentTimeMillis();
            assertDoesNotThrow(() ->
                    checker.awaitAgentFor("db-query", Duration.ofSeconds(5)));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 100,
                    "should return immediately, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("throws NoAvailableAgentException when timeout expires with no agent")
        void throwsExceptionOnTimeout() {
            var registry = new FixedResponseStub(false);
            var checker = new DefaultAgentAvailabilityChecker(registry);

            Duration shortTimeout = Duration.ofMillis(100);

            long start = System.currentTimeMillis();
            NoAvailableAgentException ex = assertThrows(
                    NoAvailableAgentException.class,
                    () -> checker.awaitAgentFor("batch-processing", shortTimeout));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(ex.getMessage().contains("batch-processing"),
                    "exception message should contain task name");
            assertTrue(elapsed >= shortTimeout.toMillis() - 10,
                    "should have waited at least timeout, elapsed=" + elapsed + "ms");
        }

        @Test
        @DisplayName("succeeds when agent becomes available during polling")
        void succeedsWhenAgentBecomesAvailable() {
            // Devient disponible apres le 1er poll (2eme appel a hasAgentFor)
            var registry = new DelayedStub(1);
            var checker = new DefaultAgentAvailabilityChecker(registry);

            Duration timeout = Duration.ofSeconds(5);

            long start = System.currentTimeMillis();
            assertDoesNotThrow(() ->
                    checker.awaitAgentFor("kafka-produce", timeout));
            long elapsed = System.currentTimeMillis() - start;

            // Devrait reussir apres ~500ms (1 cycle de polling)
            assertTrue(elapsed < 2000,
                    "should succeed within 2s, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("timeout is honored even when poll interval extends beyond")
        void timeoutHonoredWithTightDeadline() {
            var registry = new FixedResponseStub(false);
            var checker = new DefaultAgentAvailabilityChecker(registry);

            // Timeout tres court — 1ms
            Duration tinyTimeout = Duration.ofMillis(1);

            long start = System.currentTimeMillis();
            assertThrows(NoAvailableAgentException.class,
                    () -> checker.awaitAgentFor("rest-call", tinyTimeout));
            long elapsed = System.currentTimeMillis() - start;

            // Le timeout devrait etre approxime, pas 500ms
            assertTrue(elapsed < 600,
                    "should not wait full poll interval, elapsed=" + elapsed + "ms");
        }

        @Test
        @DisplayName("different task names are handled independently")
        void differentTaskNamesHandledIndependently() {
            // Stub qui connait "cache-warm" mais pas "db-cleanup"
            var registry = new AgentRegistryPort() {
                @Override
                public boolean hasAgentFor(String taskName) {
                    return "cache-warm".equals(taskName);
                }
                @Override public void onAgentRegistered(AgentDescriptor descriptor) {}
                @Override public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {}
                @Override public void onAgentExpired(AgentId agentId) {}
                @Override public void onAgentDeregistered(AgentId agentId) {}
                @Override public List<AgentDescriptor> findByTaskName(String taskName) { return Collections.emptyList(); }
                @Override public Optional<AgentDescriptor> findById(AgentId agentId) { return Optional.empty(); }
                @Override public List<AgentDescriptor> findAll() { return Collections.emptyList(); }
            };
            var checker = new DefaultAgentAvailabilityChecker(registry);

            // Tache connue
            assertDoesNotThrow(() ->
                    checker.awaitAgentFor("cache-warm", Duration.ofMillis(100)));

            // Tache inconnue
            assertThrows(NoAvailableAgentException.class,
                    () -> checker.awaitAgentFor("db-cleanup", Duration.ofMillis(100)));
        }
    }
}
