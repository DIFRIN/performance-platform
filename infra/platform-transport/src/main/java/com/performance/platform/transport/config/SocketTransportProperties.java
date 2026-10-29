package com.performance.platform.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration du transport Socket.
 * Préfixe : {@code transport.socket}
 *
 * <p>Chaque champ correspond à une propriété dans {@code application.yaml} :
 * <pre>
 * transport:
 *   socket:
 *     orchestrator-host: "localhost"
 *     orchestrator-port: 9090
 *     backlog: 50
 *     keep-alive: true
 *     reconnect-interval-ms: 5000
 * </pre>
 */
@ConfigurationProperties(prefix = "transport.socket")
public record SocketTransportProperties(
        String orchestratorHost,
        int orchestratorPort,
        int backlog,
        boolean keepAlive,
        int reconnectIntervalMs
) {}
