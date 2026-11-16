package com.performance.platform.application.ports.out;

import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.application.schedule.ScheduledRun;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistance des runs planifies. */
public interface ScheduledRunRepository {

    void save(ScheduledRun run);

    Optional<ScheduledRun> findById(ScheduledRunId id);

    /**
     * Retourne les runs PENDING dont l'echeance est atteinte, du plus ancien
     * au plus recent.
     *
     * @param now   instant courant
     * @param limit nombre maximal de runs retournes
     */
    List<ScheduledRun> findDue(Instant now, int limit);

    /** Retourne les runs les plus recents (pending et historique). */
    List<ScheduledRun> findRecent(int limit);
}
