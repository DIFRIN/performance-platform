package com.performance.platform.domain.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant unique d'un message de transport.
 * Value object immuable, 0-annotation framework.
 */
public record MessageId(String value) {

    public MessageId {
        Objects.requireNonNull(value, "value required");
    }

    public static MessageId generate() {
        return new MessageId(UUID.randomUUID().toString());
    }

    public static MessageId of(String value) {
        return new MessageId(value);
    }
}
