package com.performance.platform.application.config;

import com.performance.platform.domain.execution.TaskCompletionPolicy;

import java.time.Duration;

/**
 * Configuration de l'execution, regroupant les timeouts et la politique de completions.
 *
 * <p>Les contraintes R5 (taskAvailabilityTimeout >= 120s en K8s) et
 * R6 (workInProgressResetInterval <= taskExecutionTimeout / 3) sont
 * applicatives et verifiees par l'ExecutionEngine, pas ici.</p>
 *
 * @param taskAvailabilityTimeout delai max d'attente de disponibilite d'un agent
 * @param taskExecutionTimeout delai max d'execution d'une tache
 * @param workInProgressResetInterval intervalle de reset des taches en cours
 * @param completionPolicy politique de completions (FIRST_COMPLETE ou ALL_COMPLETE)
 */
public record ExecutionConfig(
    Duration taskAvailabilityTimeout,
    Duration taskExecutionTimeout,
    Duration workInProgressResetInterval,
    TaskCompletionPolicy completionPolicy
) {}
