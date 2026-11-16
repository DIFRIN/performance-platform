package com.performance.platform.domain.assertion;

/** Type de critere temporel applique a une serie. */
public enum CriterionType {
    /** Au moins un point satisfait l'attente. */
    REACH,
    /** Part du temps conforme superieure ou egale a {@code limit} (0..1). */
    SUSTAINED,
    /** Coefficient de variation (sigma/mu) inferieur ou egal a {@code limit}. */
    SPREAD,
    /** Pente des moindres carres comprise dans [operator threshold, limit]. */
    TREND,
    /** Retour sous le seuil dans {@code within} apres la fin d'injection. */
    RECOVERY,
    /** Nombre de points et trous suffisants. */
    COVERAGE,
    /** Serie monotone dans le sens demande. */
    MONOTONIC
}
