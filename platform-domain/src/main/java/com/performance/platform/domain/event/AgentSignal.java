package com.performance.platform.domain.event;

import com.performance.platform.domain.id.SignalId;

import java.time.Instant;

/**
 * Interface scellee representant un signal envoye par l'orchestrateur
 * aux agents. Seules les implementations listees dans {@code permits} sont autorisees.
 * <p>
 * Actuellement, seul {@link ScenarioRestartSignal} est permis.
 * Interface 0-framework — les sous-types sont des records immuables.
 */
public sealed interface AgentSignal permits ScenarioRestartSignal {

    /**
     * Identifiant unique du signal.
     */
    SignalId id();

    /**
     * Instant d'emission du signal.
     */
    Instant issuedAt();
}
