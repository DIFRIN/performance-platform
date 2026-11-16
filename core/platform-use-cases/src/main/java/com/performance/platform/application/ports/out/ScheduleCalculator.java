package com.performance.platform.application.ports.out;

import com.performance.platform.application.schedule.ScheduleSpec;

import java.time.Instant;

/**
 * Resout l'expression de planification saisie par l'utilisateur et calcule
 * les occurrences successives. L'implementation vit dans l'infrastructure
 * (le domaine reste sans dependance framework ni parseur cron).
 */
public interface ScheduleCalculator {

    /**
     * Traduit l'expression utilisateur en specification.
     * Les syntaxes supportees sont : vide / {@code now}, {@code in 10m},
     * {@code in 1h30m}, une date ISO-8601, ou une expression cron
     * (prefixee {@code cron:} ou 5/6 champs).
     *
     * @param expression expression brute (peut etre {@code null} ou vide)
     * @return la specification resolue, jamais {@code null}
     * @throws com.performance.platform.application.exception.InvalidScheduleException
     *         si l'expression est invalide
     */
    ScheduleSpec parse(String expression);

    /**
     * Calcule la prochaine occurrence a partir de {@code from}.
     *
     * @param spec specification resolue
     * @param from instant de reference
     * @return prochaine occurrence (jamais {@code null})
     * @throws com.performance.platform.application.exception.InvalidScheduleException
     *         si aucune occurrence ne peut etre calculee
     */
    Instant nextRun(ScheduleSpec spec, Instant from);
}
