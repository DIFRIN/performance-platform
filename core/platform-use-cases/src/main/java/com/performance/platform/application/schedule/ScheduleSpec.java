package com.performance.platform.application.schedule;

import java.time.Instant;
import java.util.Objects;

/**
 * Planification demandee pour un run, resolue depuis l'expression saisie
 * par l'utilisateur (IHM, CLI ou configuration).
 * <p>
 * 0 annotation framework.
 *
 * @param kind       nature de la planification
 * @param expression expression normalisee (vide pour {@code IMMEDIATE},
 *                   expression cron pour {@code CRON}, texte d'origine pour {@code ONCE})
 * @param runAt      instant de declenchement pour {@code ONCE}, sinon {@code null}
 */
public record ScheduleSpec(ScheduleKind kind, String expression, Instant runAt) {

    public ScheduleSpec {
        Objects.requireNonNull(kind, "kind required");
        expression = expression == null ? "" : expression.strip();
        if (kind == ScheduleKind.ONCE && runAt == null) {
            throw new IllegalArgumentException("runAt required for ONCE schedule");
        }
        if (kind == ScheduleKind.CRON && expression.isEmpty()) {
            throw new IllegalArgumentException("expression required for CRON schedule");
        }
    }

    public static ScheduleSpec immediate() {
        return new ScheduleSpec(ScheduleKind.IMMEDIATE, "", null);
    }

    public static ScheduleSpec once(String expression, Instant runAt) {
        return new ScheduleSpec(ScheduleKind.ONCE, expression, runAt);
    }

    public static ScheduleSpec cron(String expression) {
        return new ScheduleSpec(ScheduleKind.CRON, expression, null);
    }

    /** @return true si le run doit etre differe (ni immediat, ni invalide). */
    public boolean scheduled() {
        return kind != ScheduleKind.IMMEDIATE;
    }
}
