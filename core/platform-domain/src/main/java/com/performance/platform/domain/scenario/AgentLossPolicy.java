package com.performance.platform.domain.scenario;

/**
 * Politique appliquée quand un agent est perdu pendant l'injection (ADR-067).
 * Aucune réallocation de charge n'est effectuée.
 */
public enum AgentLossPolicy {

    /** L'étape échoue et les agents restants sont annulés (défaut). */
    ABORT,

    /** Les agents survivants conservent leur part ; la charge délivrée baisse. */
    CONTINUE
}
