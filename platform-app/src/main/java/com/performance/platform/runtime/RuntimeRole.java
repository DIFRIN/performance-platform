package com.performance.platform.runtime;

/**
 * Role d'une instance de la plateforme.
 * <p>
 * Determine via l'env var {@code MODE} ou la property {@code runtime.role}
 * (ADR-006 : env var prioritaire).
 */
public enum RuntimeRole {
    /** Orchestrateur — coordonne les executions distribuees. */
    ORCHESTRATOR,
    /** Agent — execute les taches assignees par l'orchestrateur. */
    AGENT,
    /** Aucun role specifique — mode local par defaut. */
    NONE
}
