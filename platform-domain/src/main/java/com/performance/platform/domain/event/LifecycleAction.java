package com.performance.platform.domain.event;

/**
 * Action de cycle de vie du signal envoye par l'orchestrateur aux agents.
 * <p>
 * START : demarrage d'une tache (precede l'injection).
 * STOP  : arret d'une tache (suit l'injection / l'assertion).
 * <p>
 * Enum 0-framework, faisant partie du domaine.
 */
public enum LifecycleAction {
    START,
    STOP
}
