package com.performance.platform.application.ports.in;

import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.application.schedule.ScheduleSpec;
import com.performance.platform.application.schedule.ScheduledRun;

/**
 * Enregistre un scenario pour un declenchement differe (one-shot ou cron).
 * <p>
 * Contrairement a {@link SubmitScenarioUseCase}, aucune execution n'est
 * demarree immediatement : le run planifie est persiste et declenche par
 * le dispatcher a l'echeance calculee.
 */
public interface ScheduleScenarioUseCase {

    ScheduledRun schedule(ScenarioDefinition scenario, ScheduleSpec spec);
}
