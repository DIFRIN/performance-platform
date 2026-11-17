package com.performance.platform.domain.injection;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Santé du générateur de charge (worker) : CPU, pause GC cumulée, pic de heap. */
public record GeneratorHealth(double cpuLoad, Duration gcPause, long peakHeapBytes) {

    public GeneratorHealth {
        Objects.requireNonNull(gcPause, "gcPause required");
        if (cpuLoad < 0.0) {
            throw new IllegalArgumentException("cpuLoad must be non-negative, got " + cpuLoad);
        }
        if (gcPause.isNegative()) {
            throw new IllegalArgumentException("gcPause must be non-negative, got " + gcPause);
        }
        if (peakHeapBytes < 0) {
            throw new IllegalArgumentException("peakHeapBytes must be non-negative, got " + peakHeapBytes);
        }
    }

    /** Valeur par défaut : santé inconnue/non mesurée. */
    public static GeneratorHealth unknown() {
        return new GeneratorHealth(0.0, Duration.ZERO, 0L);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cpuLoad", cpuLoad);
        m.put("gcPauseMs", gcPause.toMillis());
        m.put("peakHeapBytes", peakHeapBytes);
        return m;
    }

    public static GeneratorHealth fromMap(Map<String, Object> m) {
        Objects.requireNonNull(m, "map required");
        return new GeneratorHealth(
            ((Number) m.get("cpuLoad")).doubleValue(),
            Duration.ofMillis(((Number) m.get("gcPauseMs")).longValue()),
            ((Number) m.get("peakHeapBytes")).longValue());
    }
}
