package com.performance.platform.engine.correlation;

import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation thread-safe de {@link TaskCorrelationTracker} utilisant
 * des {@link ConcurrentHashMap} pour gerer les acces concurrents de
 * plusieurs agents publiant en parallele.
 * <p>
 * Chaque messageId est associe a un {@link CorrelationState} qui suit
 * independamment les claims, completions et echecs.
 * <p>
 * Conforme a ADR-011 : tous les claims sont acceptes, la completion
 * depend de {@link TaskCompletionPolicy}.
 * <p>
 * Actif uniquement en mode DISTRIBUTED (le tracking de correlation
 * multi-agent n'est pas necessaire en LOCAL).
 */
@Component
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class DefaultTaskCorrelationTracker implements TaskCorrelationTracker {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskCorrelationTracker.class);

    private final ConcurrentHashMap<MessageId, CorrelationState> states = new ConcurrentHashMap<>();

    @Override
    public void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        state.taskId = taskId;
        log.info("action=track_dispatched messageId={} taskId={} executionId={}",
                messageId.value(), taskId.value(), executionId.value());
    }

    @Override
    public void onClaimed(MessageId messageId, AgentId agentId) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        state.claimedAgents.add(agentId);
        log.info("action=task_claimed messageId={} agentId={} claimCount={}",
                messageId.value(), agentId.value(), state.claimedAgents.size());
    }

    @Override
    public void onCompleted(MessageId messageId, AgentId agentId, TaskResult result) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        state.completedAgents.add(agentId);
        state.results.put(agentId, result);
        log.info("action=task_completed messageId={} agentId={} taskStatus={} completedCount={}/{}",
                messageId.value(), agentId.value(), result.status(),
                state.completedAgents.size(), state.claimedAgents.size());
    }

    @Override
    public void onFailed(MessageId messageId, AgentId agentId, String error) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        state.failedAgents.add(agentId);
        log.warn("action=task_failed messageId={} agentId={} error={} failedCount={}/{}",
                messageId.value(), agentId.value(), error,
                state.failedAgents.size(), state.claimedAgents.size());
    }

    @Override
    public Set<AgentId> claimsFor(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(state.claimedAgents);
    }

    @Override
    public boolean isComplete(MessageId messageId, TaskCompletionPolicy policy) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return false;
        }
        return switch (policy) {
            // Fast-fail: si tous les agents claimers ont echoue (agent perdu
            // inclus), aucun succes n'est plus possible.
            case FIRST_COMPLETE -> !state.completedAgents.isEmpty()
                    || (!state.claimedAgents.isEmpty()
                        && state.failedAgents.containsAll(state.claimedAgents));
            case ALL_COMPLETE -> isAllComplete(state);
        };
    }

    @Override
    public Map<AgentId, TaskResult> getResults(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(state.results);
    }

    @Override
    public TaskId taskIdFor(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        return state != null ? state.taskId : null;
    }

    @Override
    public void onAgentLost(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        int affected = 0;
        for (var entry : states.entrySet()) {
            CorrelationState state = entry.getValue();
            if (state.claimedAgents.contains(agentId)
                    && !state.completedAgents.contains(agentId)
                    && state.failedAgents.add(agentId)) {
                affected++;
                log.warn("action=claim_failed_agent_lost messageId={} agentId={} taskId={}",
                        entry.getKey().value(), agentId.value(),
                        state.taskId != null ? state.taskId.value() : "?");
            }
        }
        if (affected > 0) {
            log.warn("action=agent_lost_claims_failed agentId={} affectedMessages={}",
                    agentId.value(), affected);
        }
    }

    @Override
    public void release(MessageId messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        if (states.remove(messageId) != null) {
            log.debug("action=correlation_released messageId={} trackedMessages={}",
                    messageId.value(), states.size());
        }
    }

    /**
     * Nombre d'etats de correlation actuellement suivis.
     * Visible au niveau package pour les tests de fuite memoire.
     */
    int stateCount() {
        return states.size();
    }

    /**
     * Verifie la completion ALL_COMPLETE : tous les agents ayant claim
     * ont soit complete, soit echoue. Necessite au moins un claim.
     */
    private boolean isAllComplete(CorrelationState state) {
        if (state.claimedAgents.isEmpty()) {
            return false;
        }
        int completedAndFailed = state.completedAgents.size() + state.failedAgents.size();
        return completedAndFailed == state.claimedAgents.size();
    }

    /**
     * Etat de correlation interne pour un messageId donne.
     * Chaque champ est un ensemble thread-safe pour supporter les acces concurrents.
     */
    private static class CorrelationState {
        volatile TaskId taskId;
        final Set<AgentId> claimedAgents = ConcurrentHashMap.newKeySet();
        final Set<AgentId> completedAgents = ConcurrentHashMap.newKeySet();
        final Set<AgentId> failedAgents = ConcurrentHashMap.newKeySet();
        final ConcurrentHashMap<AgentId, TaskResult> results = new ConcurrentHashMap<>();
    }
}
