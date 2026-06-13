package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour AssertionResult et Evidence — isPassed(), immuabilite, validation.
 */
@DisplayName("AssertionResult / Evidence")
class AssertionResultTest {

    private static TaskId assertionId() {
        return TaskId.of("assert-1");
    }

    private static Evidence validEvidence() {
        return new Evidence(95.0, 100.0, AssertionOperator.GTE, "ms", Map.of("metric", "p95"));
    }

    // ════════════════════════════════════════════════════════════════════
    // AssertionResult — isPassed()
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AssertionResult.isPassed()")
    class IsPassed {

        @Test
        @DisplayName("PASSED → isPassed() true")
        void passedReturnsTrue() {
            var result = new AssertionResult(assertionId(), AssertionStatus.PASSED,
                "OK", validEvidence(), Duration.ofMillis(5), Instant.now());
            assertTrue(result.isPassed());
        }

        @Test
        @DisplayName("FAILED → isPassed() false")
        void failedReturnsFalse() {
            var result = new AssertionResult(assertionId(), AssertionStatus.FAILED,
                "failed", validEvidence(), Duration.ofMillis(5), Instant.now());
            assertFalse(result.isPassed());
        }

        @Test
        @DisplayName("SKIPPED → isPassed() false")
        void skippedReturnsFalse() {
            var result = new AssertionResult(assertionId(), AssertionStatus.SKIPPED,
                "skipped", validEvidence(), Duration.ofMillis(5), Instant.now());
            assertFalse(result.isPassed());
        }

        @Test
        @DisplayName("ERROR → isPassed() false")
        void errorReturnsFalse() {
            var result = new AssertionResult(assertionId(), AssertionStatus.ERROR,
                "error during evaluation", validEvidence(),
                Duration.ofMillis(5), Instant.now());
            assertFalse(result.isPassed());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AssertionResult — construction
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AssertionResult — construction")
    class Construction {

        @Test
        @DisplayName("construction nominale avec evidence")
        void nominalWithEvidence() {
            var evidence = validEvidence();
            var now = Instant.now();
            var result = new AssertionResult(assertionId(), AssertionStatus.PASSED,
                "p95 below threshold", evidence, Duration.ofMillis(10), now);

            assertEquals(assertionId(), result.assertionId());
            assertEquals(AssertionStatus.PASSED, result.status());
            assertEquals("p95 below threshold", result.description());
            assertSame(evidence, result.evidence());
            assertEquals(Duration.ofMillis(10), result.evaluationDuration());
            assertEquals(now, result.evaluatedAt());
        }

        @Test
        @DisplayName("construction avec evidence null")
        void nullEvidence() {
            var result = new AssertionResult(assertionId(), AssertionStatus.ERROR,
                "evaluation error", null, Duration.ofMillis(1), Instant.now());

            assertNull(result.evidence());
            assertFalse(result.isPassed());
        }

        @Test
        @DisplayName("assertionId null leve NullPointerException")
        void nullAssertionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionResult(null, AssertionStatus.PASSED,
                    "desc", validEvidence(), Duration.ofMillis(1), Instant.now())
            );
        }

        @Test
        @DisplayName("status null leve NullPointerException")
        void nullStatusThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionResult(assertionId(), null,
                    "desc", validEvidence(), Duration.ofMillis(1), Instant.now())
            );
        }

        @Test
        @DisplayName("description null leve NullPointerException")
        void nullDescriptionThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionResult(assertionId(), AssertionStatus.PASSED,
                    null, validEvidence(), Duration.ofMillis(1), Instant.now())
            );
        }

        @Test
        @DisplayName("evaluationDuration null leve NullPointerException")
        void nullEvaluationDurationThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionResult(assertionId(), AssertionStatus.PASSED,
                    "desc", validEvidence(), null, Instant.now())
            );
        }

        @Test
        @DisplayName("evaluatedAt null leve NullPointerException")
        void nullEvaluatedAtThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionResult(assertionId(), AssertionStatus.PASSED,
                    "desc", validEvidence(), Duration.ofMillis(1), null)
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AssertionResult — equals / toString
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AssertionResult — equals / toString")
    class EqualsToString {

        @Test
        @DisplayName("deux resultats identiques sont egaux")
        void identicalResultsEqual() {
            var now = Instant.now();
            var r1 = new AssertionResult(assertionId(), AssertionStatus.PASSED,
                "OK", validEvidence(), Duration.ofMillis(5), now);
            var r2 = new AssertionResult(assertionId(), AssertionStatus.PASSED,
                "OK", validEvidence(), Duration.ofMillis(5), now);

            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("status different → pas egaux")
        void differentStatusNotEqual() {
            var now = Instant.now();
            var r1 = new AssertionResult(assertionId(), AssertionStatus.PASSED,
                "desc", validEvidence(), Duration.ofMillis(1), now);
            var r2 = new AssertionResult(assertionId(), AssertionStatus.FAILED,
                "desc", validEvidence(), Duration.ofMillis(1), now);

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("toString contient le nom du record et le statut")
        void toStringContainsRecordAndStatus() {
            var result = new AssertionResult(assertionId(), AssertionStatus.PASSED,
                "threshold respected", validEvidence(), Duration.ofMillis(3), Instant.now());

            assertTrue(result.toString().contains("AssertionResult"));
            assertTrue(result.toString().contains("PASSED"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Evidence — construction et immuabilite
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Evidence")
    class EvidenceTests {

        @Test
        @DisplayName("construction nominale avec tous les champs")
        void nominalConstruction() {
            var details = Map.<String, Object>of("source", "gatling", "simulation", "test.Simulation");
            var evidence = new Evidence(123L, 100L, AssertionOperator.GT, "rps", details);

            assertEquals(123L, evidence.actualValue());
            assertEquals(100L, evidence.expectedValue());
            assertEquals(AssertionOperator.GT, evidence.operator());
            assertEquals("rps", evidence.unit());
            assertEquals(details, evidence.details());
        }

        @Test
        @DisplayName("actualValue peut etre null")
        void nullActualValue() {
            var evidence = new Evidence(null, 100, AssertionOperator.EQ, null, Map.of());
            assertNull(evidence.actualValue());
        }

        @Test
        @DisplayName("expectedValue peut etre null")
        void nullExpectedValue() {
            var evidence = new Evidence(100, null, AssertionOperator.EQ, null, Map.of());
            assertNull(evidence.expectedValue());
        }

        @Test
        @DisplayName("unit peut etre null")
        void nullUnit() {
            var evidence = new Evidence(100, 100, AssertionOperator.EQ, null, Map.of());
            assertNull(evidence.unit());
        }

        @Test
        @DisplayName("details null devient Map vide")
        void nullDetailsDefaultsToEmpty() {
            var evidence = new Evidence(1, 2, AssertionOperator.LT, "ms", null);
            assertEquals(Map.of(), evidence.details());
        }

        @Test
        @DisplayName("operator null leve NullPointerException")
        void nullOperatorThrows() {
            assertThrows(NullPointerException.class, () ->
                new Evidence(1, 2, null, "ms", Map.of())
            );
        }

        @Test
        @DisplayName("modifier la map details source apres construction ne modifie pas le record")
        void detailsDefensiveCopy() {
            var mutableDetails = new HashMap<>(Map.<String, Object>of("key", "val"));
            var evidence = new Evidence(1, 2, AssertionOperator.EQ, null, mutableDetails);

            mutableDetails.put("key2", "val2");

            assertEquals(Map.of("key", "val"), evidence.details());
        }

        @Test
        @DisplayName("details() retourne une map non-modifiable")
        void detailsUnmodifiable() {
            var evidence = new Evidence(1, 2, AssertionOperator.EQ, null, Map.of("k", "v"));

            assertThrows(UnsupportedOperationException.class,
                () -> evidence.details().put("new", "val"));
        }

        @Test
        @DisplayName("differentes valeurs actualValue → pas egaux")
        void differentActualValueNotEqual() {
            var e1 = new Evidence(1, 2, AssertionOperator.LT, null, Map.of());
            var e2 = new Evidence(9, 2, AssertionOperator.LT, null, Map.of());
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("deux evidences identiques sont egales")
        void identicalEvidenceEqual() {
            var e1 = new Evidence(100, 200, AssertionOperator.LTE, "ms", Map.of("key", "val"));
            var e2 = new Evidence(100, 200, AssertionOperator.LTE, "ms", Map.of("key", "val"));
            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("toString contient le nom du record et l'operateur")
        void toStringContainsRecordAndOperator() {
            var evidence = new Evidence(50, 100, AssertionOperator.LT, "ms", Map.of());
            assertTrue(evidence.toString().contains("Evidence"));
            assertTrue(evidence.toString().contains("LT"));
        }
    }
}
