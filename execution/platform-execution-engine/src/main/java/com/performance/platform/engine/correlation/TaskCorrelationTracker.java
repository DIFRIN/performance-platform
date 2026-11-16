package com.performance.platform.engine.correlation;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.task.TaskResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Suit la correlation MessageId -> claims -> grants -> resultats (ADR-055).
 * <p>
 * L'orchestrateur est unique (ADR-058) : la decision d'accord n'a besoin
 * d'aucun verrou distribue. Pour {@code fanOut: one}, le premier claim est
 * accorde et les suivants sont rejetes. Pour {@code fanOut: all}, tous les
 * claims recus pendant la fenetre de claim sont accordes.
 * <p>
 * Thread-safe : plusieurs agents publient claims et resultats en parallele.
 */
public interface TaskCorrelationTracker {

    /**
     * Enregistre le dispatch initial d'une task et son mode de fan-out.
     */
    void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId, FanOut fanOut);

    /**
     * Enregistre un claim et retourne la decision immediate.
     * <ul>
     *   <li>{@code one} : premier claim GRANTED, suivants REJECTED ;</li>
     *   <li>{@code all} : PENDING jusqu'au {@link #settleClaims(MessageId)},
     *       REJECTED apres le settlement.</li>
     * </ul>
     */
    GrantDecision onClaimed(MessageId messageId, AgentId agentId);

    /**
     * Ferme la fenetre de claim : tous les claims en attente sont accordes.
     *
     * @return les agents accordes a l'issue du settlement
     */
    List<AgentId> settleClaims(MessageId messageId);

    /**
     * Enregistre qu'un agent a ete accorde (utilise pour les grants diffuses).
     */
    void onGranted(MessageId messageId, AgentId agentId);

    /**
     * Enregistre la completion reussie d'une task par un agent.
     */
    void onCompleted(MessageId messageId, AgentId agentId, TaskResult result);

    /**
     * Enregistre l'echec d'une task par un agent.
     */
    void onFailed(MessageId messageId, AgentId agentId, String error);

    /**
     * Agents ayant claim pour ce messageId (accordes ou non).
     */
    Set<AgentId> claimsFor(MessageId messageId);

    /**
     * Agents accordes pour ce messageId.
     */
    Set<AgentId> grantedFor(MessageId messageId);

    /**
     * Futur complete au premier claim recu (attente du dispatch).
     */
    CompletableFuture<Void> claimSignal(MessageId messageId);

    /**
     * Futur complete quand tous les agents accordes ont repondu
     * (ou quand l'agent accorde a repondu en {@code fanOut: one}).
     */
    CompletableFuture<Void> completionSignal(MessageId messageId);

    /**
     * Tous les resultats enregistres, par agent.
     */
    Map<AgentId, TaskResult> getResults(MessageId messageId);

    /**
     * Resultats dans l'ordre de completion (deterministe).
     */
    List<TaskResult> resultsInCompletionOrder(MessageId messageId);

    /**
     * TaskId associe au messageId, ou {@code null} si inconnu.
     */
    TaskId taskIdFor(MessageId messageId);

    /**
     * ExecutionId associe au messageId, ou {@code null} si inconnu.
     */
    ExecutionId executionIdFor(MessageId messageId);

    /**
     * Un agent perdu voit ses grants non repondus marques comme echoues.
     */
    void onAgentLost(AgentId agentId);

    /**
     * Libere l'etat de correlation d'un messageId.
     */
    void release(MessageId messageId);
}
