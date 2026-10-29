package com.performance.platform.domain.agent;

import com.performance.platform.domain.id.AgentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour AgentDescriptor — canExecute(), copie defensive sur supportedTaskNames,
 * validations du constructeur compact.
 */
@DisplayName("AgentDescriptor")
class AgentDescriptorTest {

    private static AgentId agentId() {
        return AgentId.of("agent-007");
    }

    private static AgentCapabilities capabilities() {
        return new AgentCapabilities(4, "1.0.0");
    }

    private static Set<String> supportedTasks() {
        return Set.of("http-get", "grpc-call", "database-query");
    }

    private static AgentDescriptor validDescriptor() {
        return new AgentDescriptor(
            agentId(),
            "test-agent",
            "10.0.0.1",
            8080,
            "http://10.0.0.1:8080/callback",
            supportedTasks(),
            capabilities(),
            AgentState.IDLE,
            Instant.parse("2026-06-13T10:00:00Z"),
            Instant.parse("2026-06-13T10:05:00Z"),
            Duration.ofSeconds(30)
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // Construction nominale
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction nominale")
    class NominalConstruction {

        @Test
        @DisplayName("tous les champs valides")
        void allFieldsValid() {
            var desc = validDescriptor();
            assertNotNull(desc);
            assertEquals("test-agent", desc.name());
            assertEquals("10.0.0.1", desc.host());
            assertEquals(8080, desc.port());
            assertEquals("http://10.0.0.1:8080/callback", desc.httpCallbackUrl());
            assertEquals(3, desc.supportedTaskNames().size());
            assertEquals(capabilities(), desc.capabilities());
            assertEquals(AgentState.IDLE, desc.state());
            assertEquals(Duration.ofSeconds(30), desc.registrationTtl());
        }

        @Test
        @DisplayName("httpCallbackUrl nullable")
        void httpCallbackUrlNullable() {
            var desc = new AgentDescriptor(
                agentId(), "agent", "10.0.0.2", 9090, null,
                supportedTasks(), capabilities(), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
            );
            assertNull(desc.httpCallbackUrl());
        }

        @Test
        @DisplayName("port 0 valide")
        void portZero() {
            var desc = new AgentDescriptor(
                agentId(), "agent", "10.0.0.2", 0, null,
                supportedTasks(), capabilities(), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
            );
            assertEquals(0, desc.port());
        }

        @Test
        @DisplayName("port 65535 valide")
        void portMax() {
            var desc = new AgentDescriptor(
                agentId(), "agent", "10.0.0.2", 65535, null,
                supportedTasks(), capabilities(), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
            );
            assertEquals(65535, desc.port());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // canExecute()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("canExecute()")
    class CanExecute {

        @Test
        @DisplayName("retourne true pour une tache supportee")
        void returnsTrueForSupportedTask() {
            var desc = validDescriptor();
            assertTrue(desc.canExecute("http-get"));
            assertTrue(desc.canExecute("grpc-call"));
            assertTrue(desc.canExecute("database-query"));
        }

        @Test
        @DisplayName("retourne false pour une tache non supportee")
        void returnsFalseForUnsupportedTask() {
            var desc = validDescriptor();
            assertFalse(desc.canExecute("kafka-produce"));
            assertFalse(desc.canExecute("shell-script"));
        }

        @Test
        @DisplayName("retourne false avec supportedTaskNames vide")
        void returnsFalseWhenEmpty() {
            var desc = new AgentDescriptor(
                agentId(), "agent", "10.0.0.2", 9090, null,
                Set.of(), capabilities(), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
            );
            assertFalse(desc.canExecute("anything"));
        }

        @Test
        @DisplayName("retourne false avec supportedTaskNames null → Set.of()")
        void returnsFalseWhenNull() {
            var desc = new AgentDescriptor(
                agentId(), "agent", "10.0.0.2", 9090, null,
                null, capabilities(), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
            );
            assertFalse(desc.canExecute("anything"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Copie defensive supportedTaskNames
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Copie defensive supportedTaskNames")
    class DefensiveCopy {

        @Test
        @DisplayName("la modification du Set d'origine n'affecte pas le record")
        void originalSetMutationDoesNotAffectRecord() {
            var mutableSet = new HashSet<String>();
            mutableSet.add("http-get");
            mutableSet.add("database-query");

            var desc = new AgentDescriptor(
                agentId(), "agent", "10.0.0.2", 9090, null,
                mutableSet, capabilities(), AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofSeconds(30)
            );

            // muter le set d'origine
            mutableSet.add("malicious-task");
            mutableSet.remove("http-get");

            // le record ne doit pas etre affecte
            assertEquals(2, desc.supportedTaskNames().size());
            assertTrue(desc.canExecute("http-get"));
            assertTrue(desc.canExecute("database-query"));
            assertFalse(desc.canExecute("malicious-task"));
        }

        @Test
        @DisplayName("le Set retourne est non modifiable")
        void returnedSetIsUnmodifiable() {
            var desc = validDescriptor();
            assertThrows(UnsupportedOperationException.class, () ->
                desc.supportedTaskNames().add("new-task"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Validations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validations constructeur compact")
    class Validation {

        @Test
        @DisplayName("id null → NPE")
        void nullId() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    null, "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("name null → NPE")
        void nullName() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), null, "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("host null → NPE")
        void nullHost() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", null, 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("port negatif → IAE")
        void negativePort() {
            assertThrows(IllegalArgumentException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", -1, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("port > 65535 → IAE")
        void portTooHigh() {
            assertThrows(IllegalArgumentException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 65536, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("capabilities null → NPE")
        void nullCapabilities() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), null, AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("state null → NPE")
        void nullState() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), null,
                    Instant.now(), Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("registeredAt null → NPE")
        void nullRegisteredAt() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    null, Instant.now(), Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("lastHeartbeatAt null → NPE")
        void nullLastHeartbeatAt() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), null, Duration.ofSeconds(30)
                ));
        }

        @Test
        @DisplayName("registrationTtl null → NPE")
        void nullRegistrationTtl() {
            assertThrows(NullPointerException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), null
                ));
        }

        @Test
        @DisplayName("registrationTtl negatif → IAE")
        void negativeTtl() {
            assertThrows(IllegalArgumentException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ofSeconds(-1)
                ));
        }

        @Test
        @DisplayName("registrationTtl zero → IAE")
        void zeroTtl() {
            assertThrows(IllegalArgumentException.class, () ->
                new AgentDescriptor(
                    agentId(), "agent", "10.0.0.2", 9090, null,
                    supportedTasks(), capabilities(), AgentState.IDLE,
                    Instant.now(), Instant.now(), Duration.ZERO
                ));
        }
    }
}
