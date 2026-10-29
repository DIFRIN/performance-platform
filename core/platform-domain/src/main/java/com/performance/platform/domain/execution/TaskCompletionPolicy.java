package com.performance.platform.domain.execution;

/**
 * Politique de complétion pour tâches multi-claim.
 * FIRST_COMPLETE : premier resultat fait foi.
 * ALL_COMPLETE : attend tous les resultats.
 */
public enum TaskCompletionPolicy {
    FIRST_COMPLETE,
    ALL_COMPLETE
}
