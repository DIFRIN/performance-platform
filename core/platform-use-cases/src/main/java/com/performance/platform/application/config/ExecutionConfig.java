package com.performance.platform.application.config;

import java.time.Duration;

/**
 * Configuration de l'execution, regroupant les timeouts et la fenetre de claim.
 *
 * <p>ADR-055 : le fan-out est declare par etape ({@code fanOut}), il n'y a plus
 * de politique globale de completion ni de selection de resultat. ADR-058 :
 * {@code work-in-progress-reset-interval} est supprime.</p>
 *
 * @param taskAvailabilityTimeout delai max d'attente de disponibilite d'un agent
 * @param taskExecutionTimeout delai max d'execution d'une tache
 * @param claimWindow fenetre pendant laquelle les claims sont collectes
 *                    ({@code fanOut: all}) ou attendus ({@code fanOut: one})
 */
public record ExecutionConfig(
    Duration taskAvailabilityTimeout,
    Duration taskExecutionTimeout,
    Duration claimWindow
) {

    public ExecutionConfig {
        if (taskAvailabilityTimeout == null) {
            taskAvailabilityTimeout = Duration.ofSeconds(120);
        }
        if (taskExecutionTimeout == null) {
            taskExecutionTimeout = Duration.ofSeconds(300);
        }
        if (claimWindow == null) {
            claimWindow = Duration.ofSeconds(1);
        }
    }
}
