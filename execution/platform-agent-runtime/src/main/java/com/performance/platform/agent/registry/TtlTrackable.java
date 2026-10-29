package com.performance.platform.agent.registry;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.id.AgentId;

import java.time.Instant;
import java.util.List;

/**
 * Interface package-private exposant les capacités de suivi TTL d'un registre.
 * <p>
 * Permet à {@link AgentTtlMonitor} de dépendre d'une abstraction plutôt que
 * de {@link InMemoryAgentRegistry} directement — respecte le Dependency
 * Inversion Principle et permet de futures implémentations (Redis, JDBC).
 */
interface TtlTrackable {

    /**
     * Retourne les agents dont le TTL a expiré à l'instant donné.
     */
    List<AgentDescriptor> findExpired(Instant now);

    /**
     * Supprime atomiquement un agent SI son TTL est toujours expiré à
     * {@code checkedAt}. Élimine la race condition TOCTOU entre
     * {@code findExpired} et la suppression.
     */
    void onAgentExpiredIfStillExpired(AgentId agentId, Instant checkedAt);
}
