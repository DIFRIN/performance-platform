package com.performance.platform.domain.id;

import java.util.Objects;

/**
 * Identifiant unique d'une tâche dans un scénario.
 * Value object immuable, 0-annotation framework.
 */
public record TaskId(String value) {

    public TaskId {
        Objects.requireNonNull(value, "value required");
    }

    public static TaskId of(String value) {
        return new TaskId(value);
    }
}
