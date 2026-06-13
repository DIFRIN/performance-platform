package com.performance.platform.application.ports.out;

import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;

import java.util.Map;
import java.util.Optional;

/**
 * Port sortant vers le stockage de l'etat d'execution.
 * Permet de persister et de requeter les etats d'execution des scenarios.
 * 0 annotation framework.
 */
public interface ExecutionRepository {

    /**
     * Persiste l'etat complet d'une execution.
     *
     * @param state l'etat a sauvegarder
     */
    void save(ExecutionState state);

    /**
     * Recupere l'etat d'une execution par son identifiant.
     *
     * @param id l'identifiant de l'execution
     * @return l'etat d'execution s'il existe
     */
    Optional<ExecutionState> findById(ExecutionId id);

    /**
     * Met a jour le statut d'une phase pour une execution donnee.
     *
     * @param id     l'identifiant de l'execution
     * @param phase  la phase concernee
     * @param status le nouveau statut de la phase
     */
    void updatePhase(ExecutionId id, Phase phase, PhaseStatus status);

    /**
     * Sauvegarde un resultat de tache pour une execution.
     * Supporter N appels pour le meme taskId (pattern multi-claim ADR-011).
     *
     * @param id       l'identifiant de l'execution
     * @param taskId   l'identifiant de la tache
     * @param agentId  l'identifiant de l'agent
     * @param result   le resultat de la tache
     */
    void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result);

    /**
     * Recupere tous les resultats d'une tache (possiblement multi-agent).
     *
     * @param id     l'identifiant de l'execution
     * @param taskId l'identifiant de la tache
     * @return une map associant chaque agent a son resultat
     */
    Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId);
}
