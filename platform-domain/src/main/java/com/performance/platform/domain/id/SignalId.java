package com.performance.platform.domain.id;

import java.util.UUID;

/**
 * Identifiant unique d'un signal entre orchestrateur et agents.
 * Value object immuable, 0-annotation framework.
 */
public record SignalId(String value) {

    public static SignalId generate() {
        return new SignalId(UUID.randomUUID().toString());
    }
}
