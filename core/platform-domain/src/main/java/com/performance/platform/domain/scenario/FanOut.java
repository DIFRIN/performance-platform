package com.performance.platform.domain.scenario;

/**
 * Nombre d'agents autorises a executer une etape (ADR-055).
 * <p>
 * {@code ONE} : l'orchestrateur accorde le premier claim et rejette les
 * suivants. {@code ALL} : tous les claims recus pendant la fenetre de claim
 * sont accordes (injection de charge multi-agents).
 */
public enum FanOut {

    /** Un seul agent execute l'etape (defaut). */
    ONE,

    /** Tous les agents competents executent l'etape. */
    ALL
}
