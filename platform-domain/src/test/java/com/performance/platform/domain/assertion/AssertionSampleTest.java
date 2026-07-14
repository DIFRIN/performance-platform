package com.performance.platform.domain.assertion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour AssertionSample — constructeur compact, defensive copy metadata, immuabilite.
 */
@DisplayName("AssertionSample")
class AssertionSampleTest {

    private static Instant sampleTime() {
        return Instant.ofEpochMilli(1_750_000_000_000L);
    }

    // ════════════════════════════════════════════════════════════════════
    // AssertionSample — construction
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AssertionSample — construction")
    class Construction {

        @Test
        @DisplayName("construction nominale avec tous les champs")
        void nominalConstruction() {
            var now = sampleTime();
            var meta = Map.<String, Object>of("partition", 0, "offset", 42L);
            var sample = new AssertionSample(now, 95.5, "ms", meta);

            assertEquals(now, sample.sampledAt());
            assertEquals(95.5, sample.observedValue());
            assertEquals("ms", sample.unit());
            assertEquals(meta, sample.metadata());
        }

        @Test
        @DisplayName("observedValue peut etre zero")
        void zeroObservedValue() {
            var sample = new AssertionSample(sampleTime(), 0.0, "rps", Map.of());
            assertEquals(0.0, sample.observedValue());
        }

        @Test
        @DisplayName("observedValue negative possible")
        void negativeObservedValue() {
            var sample = new AssertionSample(sampleTime(), -1.5, "degrees", Map.of());
            assertEquals(-1.5, sample.observedValue());
        }

        @Test
        @DisplayName("metadata null devient Map vide")
        void nullMetadataDefaultsToEmpty() {
            var sample = new AssertionSample(sampleTime(), 100.0, "rps", null);
            assertEquals(Map.of(), sample.metadata());
        }

        @Test
        @DisplayName("sampledAt null leve NullPointerException")
        void nullSampledAtThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionSample(null, 1.0, "ms", Map.of())
            );
        }

        @Test
        @DisplayName("unit null leve NullPointerException")
        void nullUnitThrows() {
            assertThrows(NullPointerException.class, () ->
                new AssertionSample(sampleTime(), 1.0, null, Map.of())
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AssertionSample — immuabilite metadata
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AssertionSample — immuabilite metadata")
    class MetadataImmutability {

        @Test
        @DisplayName("modifier la map metadata source apres construction ne modifie pas le record")
        void metadataDefensiveCopy() {
            var mutableMeta = new HashMap<>(Map.<String, Object>of("endpoint", "/api/users"));
            var sample = new AssertionSample(sampleTime(), 200.0, "ms", mutableMeta);

            mutableMeta.put("extra", "should-not-appear");

            assertEquals(Map.of("endpoint", "/api/users"), sample.metadata());
        }

        @Test
        @DisplayName("metadata() retourne une map non-modifiable")
        void metadataUnmodifiable() {
            var sample = new AssertionSample(sampleTime(), 50.0, "rps", Map.of("key", "val"));

            assertThrows(UnsupportedOperationException.class,
                () -> sample.metadata().put("new", "val"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AssertionSample — equals / toString
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AssertionSample — equals / toString")
    class EqualsToString {

        @Test
        @DisplayName("deux echantillons identiques sont egaux")
        void identicalSamplesEqual() {
            var now = sampleTime();
            var meta = Map.<String, Object>of("source", "kafka", "topic", "perf.test");
            var s1 = new AssertionSample(now, 150.0, "ms", meta);
            var s2 = new AssertionSample(now, 150.0, "ms", meta);

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("observedValue different → pas egaux")
        void differentObservedValueNotEqual() {
            var now = sampleTime();
            var s1 = new AssertionSample(now, 1.0, "ms", Map.of());
            var s2 = new AssertionSample(now, 2.0, "ms", Map.of());

            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("metadata different → pas egaux")
        void differentMetadataNotEqual() {
            var now = sampleTime();
            var s1 = new AssertionSample(now, 1.0, "ms", Map.of("a", 1));
            var s2 = new AssertionSample(now, 1.0, "ms", Map.of("a", 2));

            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("toString contient le nom du record")
        void toStringContainsRecordName() {
            var sample = new AssertionSample(sampleTime(), 42.0, "rps", Map.of("env", "staging"));
            assertTrue(sample.toString().contains("AssertionSample"));
        }
    }
}
