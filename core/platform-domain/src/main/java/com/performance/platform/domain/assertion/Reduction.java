package com.performance.platform.domain.assertion;

/**
 * Regle d'agregation multi-agents / multi-claims.
 * <p>
 * Les quantiles ne sont jamais moyennes : {@code MAX} (pire agent) est la
 * regle par defaut. Les compteurs s'additionnent ({@code SUM}), les etats
 * exigent l'unanimite ({@code ALL}).
 */
public enum Reduction {
    SUM,
    MAX,
    MIN,
    WEIGHTED,
    ALL,
    ANY,
    FIRST,
    /** Histogrammes sommes bucket par bucket, quantile calcule sur la distribution fusionnee (ADR-068). */
    MERGE
}
