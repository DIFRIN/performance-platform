package com.performance.platform.injection.gatling.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Proprietes {@code injection.worker.*} (ADR-066). Les variables d'environnement
 * {@code INJECTION_WORKER_MAX_CONCURRENT}, {@code INJECTION_WORKER_JVM_ARGS} et
 * {@code INJECTION_WORKER_STOP_GRACE} priment sur les properties (ADR-006, binding Spring).
 */
@ConfigurationProperties("injection.worker")
public record InjectionWorkerProperties(int maxConcurrent, List<String> jvmArgs, Duration stopGrace) {

    public static final int DEFAULT_MAX_CONCURRENT = 1;
    public static final Duration DEFAULT_STOP_GRACE = Duration.ofSeconds(10);

    public InjectionWorkerProperties {
        maxConcurrent = maxConcurrent <= 0 ? DEFAULT_MAX_CONCURRENT : maxConcurrent;
        jvmArgs = jvmArgs != null ? List.copyOf(jvmArgs) : List.of();
        stopGrace = stopGrace != null ? stopGrace : DEFAULT_STOP_GRACE;
    }

    public static InjectionWorkerProperties defaults() {
        return new InjectionWorkerProperties(DEFAULT_MAX_CONCURRENT, List.of(), DEFAULT_STOP_GRACE);
    }
}
