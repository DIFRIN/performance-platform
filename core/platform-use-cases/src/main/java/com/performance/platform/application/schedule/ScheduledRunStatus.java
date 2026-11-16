package com.performance.platform.application.schedule;

/**
 * Cycle de vie d'un run planifie.
 * 0 annotation framework.
 */
public enum ScheduledRunStatus {
    /** En attente du prochain declenchement (one-shot ou cron). */
    PENDING,
    /** Execution unique declenchee. */
    COMPLETED,
    /** Le declenchement a echoue (scenario illisible ou moteur indisponible). */
    FAILED,
    /** Annule par l'utilisateur. */
    CANCELLED
}
