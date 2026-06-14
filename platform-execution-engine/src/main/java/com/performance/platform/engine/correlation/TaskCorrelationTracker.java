package com.performance.platform.engine.correlation;

import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;

import java.util.Set;

/**
 * Suit la correlation MessageId -> claims -> resultats (1:N) et determine
 * la completion selon {@link TaskCompletionPolicy}.
 * <p>
 * Dans le modele broadcast multi-agent, plusieurs agents peuvent claimer la
 * meme task. Ce tracker enregistre chaque claim et chaque resultat
 * independamment, et expose la completion via la policy demandee.
 * <p>
 * Thread-safe : les implementations doivent supporter les appels concurrents
 * de plusieurs agents publiant en parallele.
 */
public interface TaskCorrelationTracker {

    /**
     * Enregistre le dispatch initial d'une task.
     *
     * @param messageId   identifiant du message de transport
     * @param taskId      identifiant de la task
     * @param executionId identifiant de l'execution parente
     */
    void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId);

    /**
     * Enregistre qu'un agent a claim la task.
     *
     * @param messageId identifiant du message
     * @param agentId   identifiant de l'agent
     */
    void onClaimed(MessageId messageId, AgentId agentId);

    /**
     * Enregistre la completion reussie d'une task par un agent.
     *
     * @param messageId identifiant du message
     * @param agentId   identifiant de l'agent
     * @param result    le resultat produit par l'agent
     */
    void onCompleted(MessageId messageId, AgentId agentId, TaskResult result);

    /**
     * Enregistre l'echec d'une task par un agent.
     *
     * @param messageId identifiant du message
     * @param agentId   identifiant de l'agent
     * @param error     description de l'erreur
     */
    void onFailed(MessageId messageId, AgentId agentId, String error);

    /**
     * Retourne l'ensemble des agents ayant claim pour ce messageId.
     *
     * @param messageId identifiant du message
     * @return ensemble immuable des agents claimers (vide si aucun)
     */
    Set<AgentId> claimsFor(MessageId messageId);

    /**
     * Determine si la task est complete selon la politique indiquee.
     * <ul>
     * <li>{@code FIRST_COMPLETE} : true des le premier onCompleted.</li>
     * <li>{@code ALL_COMPLETE} : true quand tous les agents ayant claim
     * ont complete (SUCCESS) ou echoue (FAILED).</li>
     * </ul>
     *
     * @param messageId identifiant du message
     * @param policy    politique de completion
     * @return true si la condition est satisfaite
     */
    boolean isComplete(MessageId messageId, TaskCompletionPolicy policy);
}
