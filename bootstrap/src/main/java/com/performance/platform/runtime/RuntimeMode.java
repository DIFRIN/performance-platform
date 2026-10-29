package com.performance.platform.runtime;

/**
 * Mode de fonctionnement de la plateforme.
 * <p>
 * Determine via l'env var {@code RUNTIME_MODE} ou la property {@code runtime.mode}
 * (ADR-006 : env var prioritaire).
 */
public enum RuntimeMode {
    /** Execution locale — tous les composants dans la meme JVM. */
    LOCAL,
    /** Execution distribuee — orchestrateur et agents separes. */
    DISTRIBUTED
}
