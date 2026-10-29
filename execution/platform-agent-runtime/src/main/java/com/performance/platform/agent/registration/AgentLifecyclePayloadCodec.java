package com.performance.platform.agent.registration;

import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Codec unique pour le payload {@code Map<String, Object>} des
 * {@link com.performance.platform.transport.AgentLifecycleEvent}.
 * <p>
 * Utilise a la fois par l'emetteur ({@link TransportAgentRegistration}) et
 * par le recepteur ({@link AgentLifecycleIngestion}) : le schema n'est plus
 * maintenu manuellement a deux endroits.
 * <p>
 * Le decodage est tolerant sur les champs optionnels inconnus d'un ancien
 * agent, mais strict sur les types des champs presents.
 */
public final class AgentLifecyclePayloadCodec {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private AgentLifecyclePayloadCodec() {
    }

    // ------------------------------------------------------------------
    // Encodage
    // ------------------------------------------------------------------

    public static Map<String, Object> encode(AgentDescriptor d) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agentId", d.id().value());
        payload.put("name", d.name());
        payload.put("host", d.host());
        payload.put("port", d.port());
        // Map.copyOf (dans AgentLifecycleEvent) rejette les valeurs null.
        if (d.httpCallbackUrl() != null) {
            payload.put("httpCallbackUrl", d.httpCallbackUrl());
        }
        payload.put("supportedTaskNames", d.supportedTaskNames());
        payload.put("state", d.state().name());
        payload.put("registeredAt", d.registeredAt().toString());
        payload.put("lastHeartbeatAt", d.lastHeartbeatAt().toString());
        payload.put("registrationTtlSeconds", d.registrationTtl().toSeconds());
        payload.put("capabilities", encode(d.capabilities()));
        return payload;
    }

    public static Map<String, Object> encode(AgentHeartbeat h) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agentId", h.agentId().value());
        payload.put("state", h.state().name());
        payload.put("activeTasks", h.activeTasks());
        payload.put("sentAt", h.sentAt().toString());
        return payload;
    }

    private static Map<String, Object> encode(AgentCapabilities caps) {
        var map = new LinkedHashMap<String, Object>();
        map.put("maxConcurrentTasks", caps.maxConcurrentTasks());
        map.put("version", caps.version());
        return map;
    }

    // ------------------------------------------------------------------
    // Decodage
    // ------------------------------------------------------------------

    public static AgentDescriptor decodeDescriptor(AgentId eventAgentId, Map<String, Object> payload) {
        var p = payload == null ? Map.<String, Object>of() : payload;
        AgentId agentId = java.util.Objects.requireNonNull(eventAgentId, "eventAgentId required");
        String name = string(p, "name", agentId.value());
        String host = string(p, "host", "unknown");
        int port = intValue(p, "port", 0);
        if (port < 0 || port > 65535) {
            port = 0;
        }
        String callback = string(p, "httpCallbackUrl", null);
        Set<String> supportedTasks = stringSet(p.get("supportedTaskNames"));
        AgentCapabilities capabilities = decodeCapabilities(p.get("capabilities"));
        AgentState state = state(p.get("state"));
        Instant registeredAt = instant(p, "registeredAt", Instant.now());
        Instant lastHeartbeatAt = instant(p, "lastHeartbeatAt", registeredAt);
        long ttlSeconds = longValue(p, "registrationTtlSeconds", DEFAULT_TTL.toSeconds());
        Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : DEFAULT_TTL;
        return new AgentDescriptor(agentId, name, host, port, callback, supportedTasks,
                capabilities, state, registeredAt, lastHeartbeatAt, ttl);
    }

    public static AgentHeartbeat decodeHeartbeat(AgentId agentId, Map<String, Object> payload) {
        var p = payload == null ? Map.<String, Object>of() : payload;
        AgentState state = state(p.get("state"));
        int activeTasks = intValue(p, "activeTasks", 0);
        Instant sentAt = instant(p, "sentAt", Instant.now());
        return new AgentHeartbeat(agentId, state, activeTasks, sentAt);
    }

    private static AgentCapabilities decodeCapabilities(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new AgentCapabilities(0, "unknown");
        }
        int maxConcurrent = intValue(map, "maxConcurrentTasks", 0);
        String version = string(map, "version", "unknown");
        return new AgentCapabilities(maxConcurrent, version);
    }

    private static AgentState state(Object raw) {
        if (raw == null) {
            return AgentState.OFFLINE;
        }
        try {
            return AgentState.valueOf(raw.toString().strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AgentState.OFFLINE;
        }
    }

    private static Set<String> stringSet(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private static Instant instant(Map<?, ?> p, String key, Instant fallback) {
        Object raw = p.get(key);
        if (raw == null || raw.toString().isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw.toString());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String string(Map<?, ?> p, String key, String fallback) {
        Object raw = p.get(key);
        return raw == null ? fallback : raw.toString();
    }

    private static int intValue(Map<?, ?> p, String key, int fallback) {
        Object raw = p.get(key);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static long longValue(Map<?, ?> p, String key, long fallback) {
        Object raw = p.get(key);
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(raw.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }
}
