package com.performance.platform.agent.registry;

import com.performance.platform.domain.id.AgentId;

import java.util.function.Consumer;

/**
 * Fabrique publique pour {@link AgentTtlMonitor}.
 * <p>
 * {@link AgentTtlMonitor} depend de {@link TtlTrackable}, une interface
 * package-private. La fabrique permet aux configurations Spring situees
 * dans un sous-package ({@code registry.config}) de creer le moniteur sans
 * elargir l'API publique du registre.
 */
public final class AgentTtlMonitorFactory {

    private AgentTtlMonitorFactory() {
    }

    /**
     * Cree un moniteur TTL pour le registre donne.
     *
     * @param registry             le registre (doit implementer {@link TtlTrackable})
     * @param checkIntervalSeconds intervalle de verification (>= 1)
     * @param onExpired            callback appele pour chaque agent expire (nullable)
     * @return le moniteur, non demarre
     * @throws IllegalArgumentException si le registre ne supporte pas le suivi TTL
     */
    public static AgentTtlMonitor create(AgentRegistry registry,
                                         long checkIntervalSeconds,
                                         Consumer<AgentId> onExpired) {
        if (!(registry instanceof TtlTrackable trackable)) {
            throw new IllegalArgumentException(
                    "AgentRegistry implementation does not support TTL tracking: "
                            + (registry == null ? "null" : registry.getClass().getName()));
        }
        return new AgentTtlMonitor(trackable, checkIntervalSeconds, onExpired);
    }
}
