package com.performance.platform.engine.correlation;

/**
 * Decision de l'orchestrateur face a un claim d'agent (ADR-055).
 */
public enum GrantDecision {

    /** L'agent doit executer la tache (le grant lui est transmis). */
    GRANTED,

    /** Un autre agent a ete accorde : l'agent ne doit pas executer. */
    REJECTED,

    /** Le claim est enregistre ; la decision attend la fin de la fenetre de claim. */
    PENDING
}
