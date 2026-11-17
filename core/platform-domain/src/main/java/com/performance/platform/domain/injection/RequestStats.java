package com.performance.platform.domain.injection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Résultats d'une requête nommée du mix : compteurs et histogramme de latence. */
public record RequestStats(long requests, long failures, LatencyHistogram histogram) {

    public RequestStats {
        if (requests < 0) {
            throw new IllegalArgumentException("requests must be non-negative, got " + requests);
        }
        if (failures < 0) {
            throw new IllegalArgumentException("failures must be non-negative, got " + failures);
        }
        if (failures > requests) {
            throw new IllegalArgumentException("failures must not exceed requests");
        }
        histogram = histogram == null ? LatencyHistogram.empty() : histogram;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requests", requests);
        m.put("failures", failures);
        m.put("histogram", histogram.toMap());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static RequestStats fromMap(Map<String, Object> m) {
        Objects.requireNonNull(m, "map required");
        Object h = m.get("histogram");
        return new RequestStats(
            ((Number) m.get("requests")).longValue(),
            ((Number) m.get("failures")).longValue(),
            h == null ? LatencyHistogram.empty() : LatencyHistogram.fromMap((Map<String, Object>) h));
    }
}
