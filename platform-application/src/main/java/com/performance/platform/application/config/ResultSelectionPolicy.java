package com.performance.platform.application.config;

/**
 * Politique de selection du resultat lorsqu'un meme step est execute par
 * plusieurs agents (multi-claim, ADR-011).
 * <p>
 * La selection s'appuie sur l'ordre de completion enregistre par le
 * tracker, jamais sur l'ordre d'iteration d'une map : deux executions
 * identiques selectionnent le meme resultat.
 */
public enum ResultSelectionPolicy {

    /** Resultat du premier agent a avoir complete. */
    FIRST_COMPLETED,

    /** Resultat du dernier agent a avoir complete. */
    LAST_COMPLETED
}
