package com.performance.platform.domain.injection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Distribution creuse des latences : {@code borneSuperieureMs -> effectif}, requêtes réussies (ok)
 * et en échec (ko) séparées. Résolution : 1 ms jusqu'à 1000 ms, puis 2 chiffres significatifs
 * (arrondi supérieur). Record immuable, 0 annotation framework.
 */
public record LatencyHistogram(Map<Long, Long> okBuckets, Map<Long, Long> koBuckets) {

    public LatencyHistogram {
        okBuckets = copy(okBuckets);
        koBuckets = copy(koBuckets);
    }

    private static Map<Long, Long> copy(Map<Long, Long> source) {
        if (source == null) {
            return Map.of();
        }
        TreeMap<Long, Long> sorted = new TreeMap<>();
        source.forEach((bucket, count) -> {
            Objects.requireNonNull(bucket, "bucket required");
            Objects.requireNonNull(count, "count required");
            if (bucket < 0) {
                throw new IllegalArgumentException("bucket must be non-negative, got " + bucket);
            }
            if (count < 0) {
                throw new IllegalArgumentException("count must be non-negative, got " + count);
            }
            if (count > 0) {
                sorted.put(bucket, count);
            }
        });
        return java.util.Collections.unmodifiableMap(sorted);
    }

    public static LatencyHistogram empty() {
        return new LatencyHistogram(Map.of(), Map.of());
    }

    /** Borne supérieure du bucket : identité jusqu'à 1000 ms, puis arrondi supérieur à 2 chiffres significatifs. */
    public static long bucketOf(long latencyMs) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be non-negative, got " + latencyMs);
        }
        if (latencyMs <= 1000) {
            return latencyMs;
        }
        long unit = 1;
        while (latencyMs / unit >= 100) {
            unit *= 10;
        }
        return ((latencyMs + unit - 1) / unit) * unit;
    }

    /** Ajoute un échantillon (retourne une nouvelle instance). */
    public LatencyHistogram with(long latencyMs, boolean ok) {
        long bucket = bucketOf(latencyMs);
        Map<Long, Long> ko = new TreeMap<>(koBuckets);
        Map<Long, Long> okMap = new TreeMap<>(okBuckets);
        (ok ? okMap : ko).merge(bucket, 1L, Long::sum);
        return new LatencyHistogram(okMap, ko);
    }

    /** Somme bucket par bucket — associatif et commutatif. */
    public LatencyHistogram merge(LatencyHistogram other) {
        Objects.requireNonNull(other, "other required");
        return new LatencyHistogram(sum(okBuckets, other.okBuckets), sum(koBuckets, other.koBuckets));
    }

    private static Map<Long, Long> sum(Map<Long, Long> a, Map<Long, Long> b) {
        Map<Long, Long> result = new TreeMap<>(a);
        b.forEach((k, v) -> result.merge(k, v, Long::sum));
        return result;
    }

    /** Nombre total d'échantillons (ok + ko). */
    public long count() {
        long total = 0;
        for (long c : okBuckets.values()) {
            total += c;
        }
        for (long c : koBuckets.values()) {
            total += c;
        }
        return total;
    }

    /** Plus petite borne dont le cumul atteint le rang {@code ceil(q * count)}, sur ok + ko. */
    public long quantile(double q) {
        if (!(q > 0.0 && q <= 1.0)) {
            throw new IllegalArgumentException("q must be in ]0,1], got " + q);
        }
        long total = count();
        if (total == 0) {
            throw new IllegalStateException("empty histogram");
        }
        long rank = Math.max(1, (long) Math.ceil(q * total));
        TreeMap<Long, Long> all = new TreeMap<>(sum(okBuckets, koBuckets));
        long cumulative = 0;
        for (Map.Entry<Long, Long> e : all.entrySet()) {
            cumulative += e.getValue();
            if (cumulative >= rank) {
                return e.getKey();
            }
        }
        return all.lastKey();
    }

    /** Sérialisation pour {@code TaskResult.outputs} (clés String). */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", stringKeys(okBuckets));
        map.put("ko", stringKeys(koBuckets));
        return map;
    }

    private static Map<String, Object> stringKeys(Map<Long, Long> buckets) {
        Map<String, Object> m = new LinkedHashMap<>();
        buckets.forEach((k, v) -> m.put(Long.toString(k), v));
        return m;
    }

    public static LatencyHistogram fromMap(Map<String, Object> map) {
        if (map == null) {
            return empty();
        }
        return new LatencyHistogram(longKeys(map.get("ok")), longKeys(map.get("ko")));
    }

    private static Map<Long, Long> longKeys(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<Long, Long> result = new TreeMap<>();
        m.forEach((k, v) -> result.put(Long.parseLong(String.valueOf(k)), ((Number) v).longValue()));
        return result;
    }
}
