package com.performance.platform.domain.injection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeneratorHealth")
class GeneratorHealthTest {

    @Test
    @DisplayName("construction nominale")
    void nominal() {
        var h = new GeneratorHealth(0.5, Duration.ofMillis(120), 1024L);
        assertEquals(0.5, h.cpuLoad(), 0.0001);
        assertEquals(Duration.ofMillis(120), h.gcPause());
        assertEquals(1024L, h.peakHeapBytes());
    }

    @Test
    @DisplayName("cpuLoad négatif rejeté")
    void negativeCpu() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new GeneratorHealth(-0.1, Duration.ZERO, 0L));
        assertTrue(ex.getMessage().contains("cpuLoad"));
    }

    @Test
    @DisplayName("gcPause négatif rejeté")
    void negativeGcPause() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new GeneratorHealth(0.1, Duration.ofMillis(-1), 0L));
        assertTrue(ex.getMessage().contains("gcPause"));
    }

    @Test
    @DisplayName("gcPause null rejeté")
    void nullGcPause() {
        assertThrows(NullPointerException.class, () -> new GeneratorHealth(0.1, null, 0L));
    }

    @Test
    @DisplayName("peakHeapBytes négatif rejeté")
    void negativeHeap() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new GeneratorHealth(0.1, Duration.ZERO, -1L));
        assertTrue(ex.getMessage().contains("peakHeapBytes"));
    }

    @Test
    @DisplayName("unknown() : valeurs neutres")
    void unknown() {
        var h = GeneratorHealth.unknown();
        assertEquals(0.0, h.cpuLoad(), 0.0001);
        assertEquals(Duration.ZERO, h.gcPause());
        assertEquals(0L, h.peakHeapBytes());
    }

    @Test
    @DisplayName("roundtrip toMap/fromMap")
    void roundtrip() {
        var h = new GeneratorHealth(0.75, Duration.ofMillis(250), 2_000_000L);
        assertEquals(h, GeneratorHealth.fromMap(h.toMap()));
    }

    @Test
    @DisplayName("fromMap accepte des Number variés")
    void fromMapNumbers() {
        var h = GeneratorHealth.fromMap(Map.of("cpuLoad", 1, "gcPauseMs", 5, "peakHeapBytes", 7));
        assertEquals(new GeneratorHealth(1.0, Duration.ofMillis(5), 7L), h);
    }
}
