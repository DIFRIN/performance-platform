package com.performance.platform.domain.scenario;

/**
 * Portee de vie d'une etape de type service (ADR-054).
 * <p>
 * Une etape de service est demarree (START), maintenue en vie, puis arretee
 * (STOP) aux bornes de sa portee.
 */
public enum ServiceScope {

    /** Demarree avant INJECTION, arretee apres INJECTION. */
    INJECTION,

    /** Demarree avant PREPARATION, arretee apres ASSERTION. */
    SCENARIO
}
