package com.performance.platform.domain.injection;

import com.performance.platform.domain.id.TaskId;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Résultat agrégé d'une injection Gatling.
 * Contient les métriques de performance brutes et le chemin du rapport.
 * Record immuable — copies défensives sur {@code rawStats}.
 * 0 annotation framework.
 */
public record InjectionResult(
    TaskId taskId,
    String simulationClass,
    Duration duration,
    long totalRequests,
    long successfulRequests,
    long failedRequests,
    double errorRate,
    double throughput,
    long p50Ms,
    long p75Ms,
    long p90Ms,
    long p95Ms,
    long p99Ms,
    long maxMs,
    long minMs,
    double meanMs,
    Path gatlingReportDirectory,
    Map<String, Object> rawStats
) {
    public InjectionResult {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(simulationClass, "simulationClass required");
        Objects.requireNonNull(duration, "duration required");
        Objects.requireNonNull(gatlingReportDirectory, "gatlingReportDirectory required");
        if (errorRate < 0.0 || errorRate > 100.0) {
            throw new IllegalArgumentException("errorRate must be between 0.0 and 100.0, got " + errorRate);
        }
        if (totalRequests < 0) {
            throw new IllegalArgumentException("totalRequests must be non-negative, got " + totalRequests);
        }
        if (successfulRequests < 0) {
            throw new IllegalArgumentException("successfulRequests must be non-negative, got " + successfulRequests);
        }
        if (failedRequests < 0) {
            throw new IllegalArgumentException("failedRequests must be non-negative, got " + failedRequests);
        }
        if (throughput < 0.0) {
            throw new IllegalArgumentException("throughput must be non-negative, got " + throughput);
        }
        if (p50Ms < 0 || p75Ms < 0 || p90Ms < 0 || p95Ms < 0 || p99Ms < 0 || maxMs < 0 || minMs < 0) {
            throw new IllegalArgumentException("latency percentiles must be non-negative");
        }
        if (meanMs < 0.0) {
            throw new IllegalArgumentException("meanMs must be non-negative, got " + meanMs);
        }
        rawStats = rawStats == null ? Map.of() : Map.copyOf(rawStats);
    }

    /**
     * Calcule le taux d'erreur a partir des compteurs de requetes.
     */
    public static double computeErrorRate(long total, long failed) {
        if (total == 0) {
            return 0.0;
        }
        return (double) failed / total * 100.0;
    }
}
