package com.performance.platform.injection.gatling.protocol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Declares the Gatling protocols supported by this platform module.
 * <p>
 * Each protocol entry maps to a Maven dependency in {@code pom.xml}.
 * This class is used by the protocol bootstrap layer (future ISSUE)
 * to conditionally register protocol-specific DSL components.
 * <p>
 * <strong>Protocol selection rationale (CC-03):</strong>
 * <ul>
 *   <li>{@code HTTP/HTTPS} — most common load-testing target (REST APIs, web apps)</li>
 *   <li>{@code WebSocket} — included in {@code gatling-http}, required for real-time
 *       bidirectional testing (chat, streaming, notifications)</li>
 *   <li>{@code Kafka} — message-driven architectures tested via
 *       {@code gatling-kafka} (consumer/producer simulation)</li>
 *   <li>{@code JMS} — enterprise messaging (ActiveMQ, Artemis, IBM MQ) via
 *       {@code gatling-jms}</li>
 * </ul>
 * <p>
 * <strong>gRPC explicitly excluded:</strong> the platform removed all gRPC
 * support (no {@code gatling-grpc} dependency, no {@code GrpcExecutionTransport},
 * no {@code TransportType.GRPC}). gRPC testing is out of scope for the
 * initial platform release.
 */
public final class ProtocolSupportInfo {

    private ProtocolSupportInfo() {
        // utility class — no instantiation
    }

    /**
     * Protocols actively supported by this platform.
     * Ordered by priority: HTTP first (most common), then WS, Kafka, JMS.
     * Does NOT include gRPC.
     */
    public static final List<String> SUPPORTED_PROTOCOLS = List.of(
            "HTTP",
            "HTTPS",
            "WebSocket",
            "Kafka",
            "JMS"
    );

    /**
     * Protocols explicitly excluded from the platform.
     */
    public static final Set<String> EXCLUDED_PROTOCOLS = Set.of(
            "gRPC"
    );

    /**
     * Returns the canonical set of supported protocol names.
     * Backed by a {@link LinkedHashSet} for deterministic iteration.
     */
    public static Set<String> supportedProtocols() {
        return new LinkedHashSet<>(SUPPORTED_PROTOCOLS);
    }

    /**
     * Checks whether a given protocol name is supported (case-sensitive).
     *
     * @param protocol the protocol name to test
     * @return {@code true} if the protocol is in {@link #SUPPORTED_PROTOCOLS}
     */
    public static boolean isSupported(String protocol) {
        return SUPPORTED_PROTOCOLS.contains(protocol);
    }

    /**
     * Checks whether a given protocol name is explicitly excluded.
     * Currently only gRPC is excluded.
     *
     * @param protocol the protocol name to test
     * @return {@code true} if the protocol is in {@link #EXCLUDED_PROTOCOLS}
     */
    public static boolean isExcluded(String protocol) {
        return EXCLUDED_PROTOCOLS.contains(protocol);
    }

    /**
     * Returns the count of supported protocols.
     */
    public static int count() {
        return SUPPORTED_PROTOCOLS.size();
    }
}
