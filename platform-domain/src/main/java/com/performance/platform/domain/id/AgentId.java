package com.performance.platform.domain.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant unique d'un agent d'exécution.
 * Value object immuable, 0-annotation framework.
 */
public record AgentId(String value) {

    public AgentId {
        Objects.requireNonNull(value, "value required");
    }

    public static AgentId generate() {
        return new AgentId(UUID.randomUUID().toString());
    }

    public static AgentId of(String value) {
        return new AgentId(value);
    }
}
