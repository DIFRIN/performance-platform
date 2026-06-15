package com.performance.platform.agent.runtime;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.ScenarioRestartSignal;

/**
 * Contrat de cycle de vie d'un agent de la plateforme.
 * <p>
 * Implémentations :
 * <ul>
 *   <li>{@code DistributedAgentRuntime} — mode DISTRIBUTED, role AGENT</li>
 *   <li>{@code LocalAgent} — mode LOCAL (ISSUE-038)</li>
 * </ul>
 * <p>
 * Le cycle de vie nominal est :
 * {@code OFFLINE → REGISTERING → IDLE → EXECUTING → (DRAINING) → OFFLINE}.
 * Un {@code ScenarioRestartSignal} replace l'agent à l'état {@code IDLE}.
 */
public interface AgentRuntime {

    /**
     * Démarre l'agent : connexion au transport, enregistrement, heartbeat.
     */
    void start();

    /**
     * Arrête l'agent : drain des tâches actives, désenregistrement, déconnexion.
     */
    void stop();

    /**
     * Retourne l'état courant de l'agent.
     *
     * @return l'état du cycle de vie
     */
    AgentState getState();

    /**
     * Retourne le descripteur complet de l'agent (identité, capacités, état).
     *
     * @return le descripteur (immuable)
     */
    AgentDescriptor getDescriptor();

    /**
     * Vérifie si cet agent est spécialisé pour le nom de tâche donné.
     *
     * @param taskName le nom de la tâche
     * @return true si l'agent déclare supporter cette tâche
     */
    boolean canExecute(String taskName);

    /**
     * Appelé lors de la réception d'un {@link ScenarioRestartSignal}.
     * L'agent doit annuler toute exécution en cours pour l'{@code executionId}
     * (ou toutes si {@code executionId == null}), libérer les ressources
     * stateful, et repasser à l'état {@code IDLE}.
     *
     * @param signal le signal de restart
     */
    void onScenarioRestart(ScenarioRestartSignal signal);
}
