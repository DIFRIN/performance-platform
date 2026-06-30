package com.performance.platform.engine.config;

import com.performance.platform.domain.execution.TaskCompletionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Proprietes de configuration pour l'engine d'execution, bindees
 * depuis le prefixe {@code execution.*} dans {@code application.yaml}
 * ou les variables d'environnement (ADR-006 : env prioritaire).
 *
 * <p>Ces proprietes sont utilisees exclusivement par le
 * {@code RemoteExecutionEngine} en mode DISTRIBUTED.</p>
 *
 * @param taskAvailabilityTimeout delai max d'attente de disponibilite d'un agent (defaut 120s)
 * @param taskExecutionTimeout delai max d'execution d'une tache (defaut 300s)
 * @param workInProgressResetInterval intervalle de reset des taches en cours (defaut 100s)
 * @param completionPolicy politique de completions (defaut FIRST_COMPLETE)
 */
@ConfigurationProperties(prefix = "execution")
public record ExecutionEngineProperties(
        Duration taskAvailabilityTimeout,
        Duration taskExecutionTimeout,
        Duration workInProgressResetInterval,
        TaskCompletionPolicy completionPolicy
) {

    /** Valeurs par defaut pour tous les champs. */
    public ExecutionEngineProperties {
        if (taskAvailabilityTimeout == null) {
            taskAvailabilityTimeout = Duration.ofSeconds(120);
        }
        if (taskExecutionTimeout == null) {
            taskExecutionTimeout = Duration.ofSeconds(300);
        }
        if (workInProgressResetInterval == null) {
            workInProgressResetInterval = Duration.ofSeconds(100);
        }
        if (completionPolicy == null) {
            completionPolicy = TaskCompletionPolicy.FIRST_COMPLETE;
        }
    }
}
