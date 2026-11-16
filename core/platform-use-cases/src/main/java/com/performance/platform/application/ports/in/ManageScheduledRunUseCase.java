package com.performance.platform.application.ports.in;

import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.application.schedule.ScheduledRun;

/**
 * Gestion d'un run planifie deja enregistre.
 * <p>
 * Contrairement a {@link ScheduleScenarioUseCase} (creation), ce use case
 * modifie un run existant en attente :
 * <ul>
 *   <li>{@code reschedule} — remplace l'expression de planification</li>
 *   <li>{@code runNow} — declenche immediatement le scenario stocke</li>
 * </ul>
 * Seuls les runs {@code PENDING} peuvent etre manipules.
 */
public interface ManageScheduledRunUseCase {

    /**
     * Remplace l'expression d'un run en attente et recalcule la prochaine occurrence.
     *
     * @param id         identifiant du run planifie
     * @param expression nouvelle expression ({@code in 10m}, date ISO, {@code cron:...})
     * @return le run mis a jour (statut PENDING)
     * @throws com.performance.platform.application.exception.ScheduleNotFoundException
     *         si le run est inconnu
     * @throws com.performance.platform.application.exception.InvalidScheduleException
     *         si l'expression est invalide ou immediate
     */
    ScheduledRun reschedule(ScheduledRunId id, String expression);

    /**
     * Declenche immediatement le scenario du run planifie.
     * <p>
     * Pour un run one-shot, le run passe a {@code COMPLETED}. Pour un cron,
     * la prochaine occurrence est recalculee et le run reste {@code PENDING}.
     *
     * @param id identifiant du run planifie
     * @return l'identifiant de l'execution creee
     */
    String runNow(ScheduledRunId id);
}
