package com.performance.platform.domain.injection;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoadWindowTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void roundTrip() {
        LoadWindow w = new LoadWindow(T0, Duration.ofSeconds(5), 50, 48, 120, 3, true);
        assertEquals(w, LoadWindow.fromMap(w.toMap()));
        assertEquals(T0.plusSeconds(5), w.end());
    }

    @Test
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new LoadWindow(T0, Duration.ZERO, 0, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new LoadWindow(T0, Duration.ofSeconds(1), -1, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new LoadWindow(T0, Duration.ofSeconds(1), 0, 0, 1, 2, true));
    }

    @Test
    void generatorHealthRoundTrip() {
        GeneratorHealth h = new GeneratorHealth(0.42, Duration.ofMillis(120), 1024L);
        assertEquals(h, GeneratorHealth.fromMap(h.toMap()));
        assertThrows(IllegalArgumentException.class, () -> new GeneratorHealth(-1, Duration.ZERO, 0));
    }

    @Test
    void injectionResultLegacyConstructorStillWorks() {
        InjectionResult r = new InjectionResult(com.performance.platform.domain.id.TaskId.of("t1"), "Sim",
            Duration.ofSeconds(1), 1, 1, 0, 0.0, 1.0, 1, 1, 1, 1, 1, 1, 1, 1.0, java.nio.file.Path.of("."), null);
        assertFalse(r.complete());
        assertEquals(0, r.histogram().count());
        assertTrue(r.windows().isEmpty());
    }
}
