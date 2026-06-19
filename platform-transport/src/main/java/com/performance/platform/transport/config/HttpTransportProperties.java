package com.performance.platform.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration du transport HTTP.
 * Préfixe : {@code transport.http}
 *
 * <p>Chaque champ correspond à une propriété dans {@code application.yaml} :
 * <pre>
 * transport:
 *   http:
 *     broadcast-mode: "ALL_CAPABLE"
 *     request-timeout-seconds: 30
 *     task-availability-timeout-seconds: 120
 *     callback-base-path: "/api/callback"
 * </pre>
 */
@ConfigurationProperties(prefix = "transport.http")
public record HttpTransportProperties(
        String broadcastMode,
        int requestTimeoutSeconds,
        int taskAvailabilityTimeoutSeconds,
        String callbackBasePath
) {}
