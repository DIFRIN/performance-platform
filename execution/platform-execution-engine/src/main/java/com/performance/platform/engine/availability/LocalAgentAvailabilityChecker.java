package com.performance.platform.engine.availability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Disponibilite des agents en mode LOCAL (ADR-059).
 * <p>
 * En LOCAL, l'agent embarque supporte toutes les taches : la verification de
 * disponibilite est toujours satisfaite. Le meme chemin d'execution que le mode
 * DISTRIBUTED est conserve (ADR-055, ADR-059) ; seule cette strategie differe,
 * choisie par la racine de composition.
 */
@Component
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalAgentAvailabilityChecker implements AgentAvailabilityChecker {

    @Override
    public boolean hasAgentFor(String taskName) {
        return true;
    }

    @Override
    public void awaitAgentFor(String taskName, Duration timeout) {
        // L'agent embarque est toujours disponible.
    }
}
