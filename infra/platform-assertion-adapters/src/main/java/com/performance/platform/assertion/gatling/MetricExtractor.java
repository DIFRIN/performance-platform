package com.performance.platform.assertion.gatling;

import com.performance.platform.domain.injection.InjectionResult;

/**
 * Extrait une metrique numerique d'un {@link InjectionResult}.
 * Chaque metrique est resolue par son nom (p50, p75, p90, p95, p99, max, min,
 * mean, errorRate, throughput, totalRequests, failedRequests).
 * <p>
 * Immutable utility — pas d'etat mutable, thread-safe par conception.
 */
final class MetricExtractor {

    private MetricExtractor() {
        // classe utilitaire, non instanciable
    }

    /**
     * Extrait la valeur de la metrique nommee depuis le resultat d'injection.
     *
     * @param result     le resultat d'injection (non-null)
     * @param metricName le nom de la metrique (non-null, case-sensitive)
     * @return la valeur double de la metrique
     * @throws IllegalArgumentException si le nom de metrique est inconnu
     */
    static double extract(InjectionResult result, String metricName) {
        return switch (metricName) {
            case "p50"            -> (double) result.p50Ms();
            case "p75"            -> (double) result.p75Ms();
            case "p90"            -> (double) result.p90Ms();
            case "p95"            -> (double) result.p95Ms();
            case "p99"            -> (double) result.p99Ms();
            case "max"            -> (double) result.maxMs();
            case "min"            -> (double) result.minMs();
            case "mean"           -> result.meanMs();
            case "errorRate"      -> result.errorRate();
            case "throughput"     -> result.throughput();
            case "totalRequests"  -> (double) result.totalRequests();
            case "failedRequests" -> (double) result.failedRequests();
            default -> throw new IllegalArgumentException(
                    "Unknown metric: '" + metricName
                    + "'. Supported: p50, p75, p90, p95, p99, max, min, mean, "
                    + "errorRate, throughput, totalRequests, failedRequests");
        };
    }

    /**
     * Verifie si le nom de metrique est supporte.
     *
     * @param metricName le nom a verifier
     * @return true si la metrique est connue
     */
    static boolean isSupported(String metricName) {
        return switch (metricName) {
            case "p50", "p75", "p90", "p95", "p99", "max", "min",
                 "mean", "errorRate", "throughput", "totalRequests",
                 "failedRequests" -> true;
            default -> false;
        };
    }
}
