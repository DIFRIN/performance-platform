package com.performance.platform.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Proprietes de configuration pour l'engine d'execution, bindees depuis le
 * prefixe {@code execution.*} dans {@code application.yaml} ou les variables
 * d'environnement (ADR-006 : env prioritaire).
 *
 * @param taskAvailabilityTimeout delai max d'attente de disponibilite d'un agent (defaut 120s)
 * @param taskExecutionTimeout    delai max d'execution d'une tache (defaut 300s)
 * @param claimWindow             fenetre de claim en millisecondes (defaut 1s)
 */
@ConfigurationProperties(prefix = "execution")
public record ExecutionEngineProperties(
        Duration taskAvailabilityTimeout,
        Duration taskExecutionTimeout,
        Duration claimWindow
) {

    /** Valeurs par defaut pour tous les champs. */
    public ExecutionEngineProperties {
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
