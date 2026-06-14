package com.performance.platform.engine.availability;

import java.time.Duration;

/**
 * Service de verification de disponibilite des agents.
 * <p>
 * Interroge {@code AgentRegistryPort} pour savoir si un agent competent est
 * present pour un nom de tache donne. Ne selectionne jamais d'agent —
 * uniquement une verification de presence (conforme ADR-008).
 * <p>
 * Le blocage dans {@link #awaitAgentFor} est compatible Virtual Threads.
 */
public interface AgentAvailabilityChecker {

    /**
     * Attend qu'au moins un agent competent pour {@code taskName} soit
     * present dans le registre, avec un timeout maximum.
     * <p>
     * Interroge le registre periodiquement. Retourne des qu'un agent
     * est disponible. Si aucun agent n'est disponible avant le timeout,
     * leve une {@code NoAvailableAgentException}.
     *
     * @param taskName  le nom de la tache pour laquelle un agent est requis
     * @param timeout   la duree maximum d'attente
     * @throws com.performance.platform.application.exception.NoAvailableAgentException
     *         si le timeout est atteint sans agent disponible
     */
    void awaitAgentFor(String taskName, Duration timeout);

    /**
     * Verifie sans blocage si au moins un agent competent est present.
     *
     * @param taskName le nom de la tache a verifier
     * @return true si au moins un agent est disponible
     */
    boolean hasAgentFor(String taskName);
}
