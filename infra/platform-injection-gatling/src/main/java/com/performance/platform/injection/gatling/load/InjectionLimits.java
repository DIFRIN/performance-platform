package com.performance.platform.injection.gatling.load;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Garde-fou de debit par agent : {@code injection.max-arrival-rate} (modele ouvert) et
 * {@code injection.max-concurrent-users} (modele ferme). {@code null} = pas de limite.
 * Les env vars {@code INJECTION_MAX_ARRIVAL_RATE} et {@code INJECTION_MAX_CONCURRENT_USERS}
 * priment (ADR-006, binding Spring).
 */
@ConfigurationProperties("injection")
public record InjectionLimits(Double maxArrivalRate, Long maxConcurrentUsers) {

    public InjectionLimits {
        if (maxArrivalRate != null && maxArrivalRate <= 0) {
            throw new IllegalArgumentException("max-arrival-rate must be > 0");
        }
        if (maxConcurrentUsers != null && maxConcurrentUsers <= 0) {
            throw new IllegalArgumentException("max-concurrent-users must be > 0");
        }
    }

    public static InjectionLimits none() {
        return new InjectionLimits(null, null);
    }
}
