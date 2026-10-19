package com.performance.platform.engine.correlation;

import com.performance.platform.application.config.ResultSelectionPolicy;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Retourne tous les resultats enregistres pour ce messageId.
     * La cle est l'identifiant de l'agent, la valeur son TaskResult.
     *
     * @param messageId identifiant du message
     * @return map immuable des resultats par agent (vide si aucun)
     */
    Map<AgentId, TaskResult> getResults(MessageId messageId);

    /**
     * Retourne le {@link TaskId} associe a ce messageId lors du
     * {@link #trackDispatched}, ou {@code null} si le messageId
     * est inconnu.
     *
     * @param messageId identifiant du message
     * @return le taskId, ou {@code null}
     */
    TaskId taskIdFor(MessageId messageId);

    /**
     * Notifie le tracker qu'un agent est perdu (heartbeat expire).
     * Tous les claims de cet agent qui ne sont ni completes ni echoues
     * sont marques comme echoues, afin de rendre une politique
     * {@code ALL_COMPLETE} satisfiable et de declencher un fast-fail
     * plutot qu'une attente jusqu'au timeout.
     *
     * @param agentId l'agent perdu
     */
    void onAgentLost(AgentId agentId);

    /**
     * Retourne les resultats dans l'ordre de completion des agents.
     * Ordre deterministe, jamais l'ordre d'iteration d'une map.
     *
     * @param messageId identifiant du message
     * @return liste immuable des resultats, dans l'ordre de completion
     */
    List<TaskResult> resultsInCompletionOrder(MessageId messageId);

    /**
     * Retourne un signal complete lorsque la politique est satisfaite.
     * Remplace le polling : le tracker complete le futur des qu'un
     * evenement (claim, completion, echec, perte d'agent) rend la
     * completion vraie. Si la completion est deja vraie, le futur retourne
     * est deja complete.
     *
     * @param messageId identifiant du message
     * @param policy    politique de completion
     * @return un futur complete a la satisfaction de la politique
     */
    CompletableFuture<Void> completionSignal(MessageId messageId, TaskCompletionPolicy policy);

    /**
     * Selectionne un resultat selon la politique demandee.
     *
     * @param messageId identifiant du message
     * @param policy    politique de selection
     * @return le resultat selectionne, ou vide si aucun agent n'a complete
     */
    Optional<TaskResult> selectResult(MessageId messageId, ResultSelectionPolicy policy);

    /**
     * Libere l'etat de correlation d'un messageId.
     * A appeler une fois le resultat consomme, sous peine de fuite memoire.
     *
     * @param messageId identifiant du message termine
     */
    void release(MessageId messageId);
}
