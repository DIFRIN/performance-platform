package com.performance.platform.domain.injection;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fenêtre de temps alignée sur l'horloge murale : arrivées planifiées, utilisateurs démarrés,
 * requêtes et échecs. {@code measured} = false pour le warm-up. Record immuable.
 */
public record LoadWindow(Instant start, Duration length, long plannedArrivals, long startedUsers,
                         long requests, long failures, boolean measured) {

    public LoadWindow {
        Objects.requireNonNull(start, "start required");
        Objects.requireNonNull(length, "length required");
        if (length.isNegative() || length.isZero()) {
            throw new IllegalArgumentException("length must be positive, got " + length);
        }
        if (plannedArrivals < 0 || startedUsers < 0 || requests < 0 || failures < 0) {
            throw new IllegalArgumentException("window counters must be non-negative");
        }
        if (failures > requests) {
            throw new IllegalArgumentException("failures (" + failures + ") exceed requests (" + requests + ")");
        }
    }

    public Instant end() {
        return start.plus(length);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", start.toString());
        m.put("lengthMs", length.toMillis());
        m.put("plannedArrivals", plannedArrivals);
        m.put("startedUsers", startedUsers);
        m.put("requests", requests);
        m.put("failures", failures);
        m.put("measured", measured);
        return m;
    }

    public static LoadWindow fromMap(Map<String, Object> m) {
        Objects.requireNonNull(m, "map required");
        return new LoadWindow(
            Instant.parse(String.valueOf(m.get("start"))),
            Duration.ofMillis(((Number) m.get("lengthMs")).longValue()),
            ((Number) m.get("plannedArrivals")).longValue(),
            ((Number) m.get("startedUsers")).longValue(),
            ((Number) m.get("requests")).longValue(),
            ((Number) m.get("failures")).longValue(),
            Boolean.parseBoolean(String.valueOf(m.get("measured"))));
    }
}
