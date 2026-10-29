package com.performance.platform.domain.agent;

import com.performance.platform.domain.id.AgentId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Metadonnees completes d'un agent enregistre.
 * Inclut son identite, ses capacites, son etat, et ses timestamps de vie.
 * Record immuable — copies defensives sur {@code supportedTaskNames}.
 * 0 annotation framework.
 *
 * @param httpCallbackUrl   nullable — absent pour les transports non-HTTP
 * @param supportedTaskNames ensemble immuable des noms de taches supportees
 */
public record AgentDescriptor(
    AgentId id,
    String name,
    String host,
    int port,
    String httpCallbackUrl,
    Set<String> supportedTaskNames,
    AgentCapabilities capabilities,
    AgentState state,
    Instant registeredAt,
    Instant lastHeartbeatAt,
    Duration registrationTtl
) {
    public AgentDescriptor {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(host, "host required");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535, got " + port);
        }
        // httpCallbackUrl nullable — valid for non-HTTP transports
        supportedTaskNames = supportedTaskNames == null ? Set.of() : Set.copyOf(supportedTaskNames);
        Objects.requireNonNull(capabilities, "capabilities required");
        Objects.requireNonNull(state, "state required");
        Objects.requireNonNull(registeredAt, "registeredAt required");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt required");
        Objects.requireNonNull(registrationTtl, "registrationTtl required");
        if (registrationTtl.isNegative() || registrationTtl.isZero()) {
            throw new IllegalArgumentException("registrationTtl must be positive, got " + registrationTtl);
        }
    }

    /**
     * Verifie si l'agent peut executer la tache demandee.
     *
     * @param taskName le nom de la tache a verifier
     * @return true si l'agent declare supporter cette tache
     */
    public boolean canExecute(String taskName) {
        return supportedTaskNames.contains(taskName);
    }
}
