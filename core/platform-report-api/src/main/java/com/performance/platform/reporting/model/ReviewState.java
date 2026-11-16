package com.performance.platform.reporting.model;

/**
 * Etat de revue humaine d'un rapport.
 * <p>
 * {@link #REQUIRED} est produit par le generateur de rapport (checks
 * {@code INCONCLUSIVE}, panneaux sans requete, limitations) et non par une
 * assertion. Il interdit {@code SUCCESS} sans revue explicite.
 */
public enum ReviewState {
    NONE,
    REQUIRED,
    ACCEPTED,
    REJECTED
}
