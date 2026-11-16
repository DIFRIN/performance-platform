package com.performance.platform.domain.assertion;

/**
 * Statut d'une assertion evaluee.
 * <p>
 * {@link #INCONCLUSIVE} est distinct de {@link #ERROR} : la mesure manque ou
 * est insuffisante (couverture, donnee absente), l'evaluation n'a pas echoue.
 * Il ne produit jamais un succes, et alimente la limitation du rapport.
 */
public enum AssertionStatus {
    PASSED,
    FAILED,
    SKIPPED,
    INCONCLUSIVE,
    ERROR
}
