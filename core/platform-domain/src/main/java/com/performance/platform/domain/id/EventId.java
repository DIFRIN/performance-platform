package com.performance.platform.domain.id;

import java.util.UUID;

/**
 * Identifiant unique d'un événement domaine.
 * Value object immuable, 0-annotation framework.
 */
public record EventId(String value) {

    public static EventId generate() {
        return new EventId(UUID.randomUUID().toString());
    }
}
