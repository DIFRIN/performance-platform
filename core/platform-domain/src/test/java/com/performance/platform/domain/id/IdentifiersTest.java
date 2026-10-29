package com.performance.platform.domain.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour tous les identifiants value objects.
 * Couvre : non-null, égalité par valeur, factories, unicité UUID.
 */
@DisplayName("Identifiants value objects")
class IdentifiersTest {

    // ─── ExecutionId ────────────────────────────────────────────────

    @Nested
    @DisplayName("ExecutionId")
    class ExecutionIdTests {

        @Test
        @DisplayName("generate() produit des valeurs uniques")
        void generateProducesUniqueValues() {
            var id1 = ExecutionId.generate();
            var id2 = ExecutionId.generate();
            var id3 = ExecutionId.generate();

            assertFalse(id1.value().isBlank());
            assertFalse(id2.value().isBlank());
            assertFalse(id3.value().isBlank());
            assertNotEquals(id1, id2);
            assertNotEquals(id2, id3);
            assertNotEquals(id1, id3);
        }

        @Test
        @DisplayName("of() crée l'identifiant avec la valeur donnée")
        void ofCreatesWithGivenValue() {
            var id = ExecutionId.of("exec-123");

            assertEquals("exec-123", id.value());
        }

        @Test
        @DisplayName("constructeur null lève NullPointerException")
        void nullValueThrows() {
            assertThrows(NullPointerException.class, () -> new ExecutionId(null));
        }

        @Test
        @DisplayName("égalité par valeur (record)")
        void valueEquality() {
            assertEquals(ExecutionId.of("abc"), ExecutionId.of("abc"));
            assertNotEquals(ExecutionId.of("abc"), ExecutionId.of("def"));
        }
    }

    // ─── ScenarioId ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ScenarioId")
    class ScenarioIdTests {

        @Test
        @DisplayName("of() crée l'identifiant")
        void ofCreatesIdentifier() {
            var id = ScenarioId.of("scenario-1");
            assertEquals("scenario-1", id.value());
        }

        @Test
        @DisplayName("constructeur null lève NullPointerException")
        void nullValueThrows() {
            assertThrows(NullPointerException.class, () -> new ScenarioId(null));
        }

        @Test
        @DisplayName("égalité par valeur")
        void valueEquality() {
            assertEquals(ScenarioId.of("x"), ScenarioId.of("x"));
            assertNotEquals(ScenarioId.of("x"), ScenarioId.of("y"));
        }
    }

    // ─── TaskId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("TaskId")
    class TaskIdTests {

        @Test
        @DisplayName("of() crée l'identifiant")
        void ofCreatesIdentifier() {
            var id = TaskId.of("task-42");
            assertEquals("task-42", id.value());
        }

        @Test
        @DisplayName("constructeur null lève NullPointerException")
        void nullValueThrows() {
            assertThrows(NullPointerException.class, () -> new TaskId(null));
        }

        @Test
        @DisplayName("égalité par valeur")
        void valueEquality() {
            assertEquals(TaskId.of("a"), TaskId.of("a"));
            assertNotEquals(TaskId.of("a"), TaskId.of("b"));
        }
    }

    // ─── AgentId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentId")
    class AgentIdTests {

        @Test
        @DisplayName("generate() produit des valeurs uniques")
        void generateProducesUniqueValues() {
            var id1 = AgentId.generate();
            var id2 = AgentId.generate();

            assertNotEquals(id1, id2);
            assertFalse(id1.value().isBlank());
        }

        @Test
        @DisplayName("of() crée avec la valeur donnée")
        void ofCreatesWithGivenValue() {
            assertEquals("agent-7", AgentId.of("agent-7").value());
        }

        @Test
        @DisplayName("constructeur null lève NullPointerException")
        void nullValueThrows() {
            assertThrows(NullPointerException.class, () -> new AgentId(null));
        }

        @Test
        @DisplayName("égalité par valeur")
        void valueEquality() {
            assertEquals(AgentId.of("x"), AgentId.of("x"));
        }
    }

    // ─── MessageId ──────────────────────────────────────────────────

    @Nested
    @DisplayName("MessageId")
    class MessageIdTests {

        @Test
        @DisplayName("generate() produit des valeurs uniques")
        void generateProducesUniqueValues() {
            var id1 = MessageId.generate();
            var id2 = MessageId.generate();

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("of() crée avec la valeur donnée")
        void ofCreatesWithGivenValue() {
            assertEquals("msg-99", MessageId.of("msg-99").value());
        }

        @Test
        @DisplayName("constructeur null lève NullPointerException")
        void nullValueThrows() {
            assertThrows(NullPointerException.class, () -> new MessageId(null));
        }

        @Test
        @DisplayName("égalité par valeur")
        void valueEquality() {
            assertEquals(MessageId.of("m1"), MessageId.of("m1"));
        }
    }

    // ─── EventId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventId")
    class EventIdTests {

        @Test
        @DisplayName("generate() produit des valeurs uniques")
        void generateProducesUniqueValues() {
            var id1 = EventId.generate();
            var id2 = EventId.generate();

            assertNotEquals(id1, id2);
            assertFalse(id1.value().isBlank());
        }

        @Test
        @DisplayName("égalité par valeur")
        void valueEquality() {
            var id1 = new EventId("abc");
            var id2 = new EventId("abc");
            var id3 = new EventId("def");
            assertEquals(id1, id2);
            assertNotEquals(id1, id3);
        }
    }

    // ─── SignalId ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SignalId")
    class SignalIdTests {

        @Test
        @DisplayName("generate() produit des valeurs uniques")
        void generateProducesUniqueValues() {
            var id1 = SignalId.generate();
            var id2 = SignalId.generate();

            assertNotEquals(id1, id2);
        }
    }

    // ─── ReportId ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ReportId")
    class ReportIdTests {

        @Test
        @DisplayName("generate() produit des valeurs uniques")
        void generateProducesUniqueValues() {
            var id1 = ReportId.generate();
            var id2 = ReportId.generate();

            assertNotEquals(id1, id2);
            assertFalse(id1.value().isBlank());
        }
    }

    // ─── Cross-id tests ─────────────────────────────────────────────

    @Test
    @DisplayName("tous les ids avec generate() produisent des UUID valides")
    void allGenerateProduceValidUuidFormat() {
        var ids = new String[]{
                ExecutionId.generate().value(),
                AgentId.generate().value(),
                MessageId.generate().value(),
                EventId.generate().value(),
                SignalId.generate().value(),
                ReportId.generate().value()
        };

        var uuidPattern = java.util.regex.Pattern.compile(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        for (var id : ids) {
            assertTrue(uuidPattern.matcher(id).matches(),
                    "Expected UUID format but got: " + id);
        }
    }
}
