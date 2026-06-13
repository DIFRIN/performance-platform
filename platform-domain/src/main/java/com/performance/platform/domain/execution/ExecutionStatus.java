package com.performance.platform.domain.execution;

/**
 * Statut global d'une exécution de scénario.
 */
public enum ExecutionStatus {
    STARTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
