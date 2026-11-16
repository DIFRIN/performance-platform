package com.performance.platform.engine.correlation;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation thread-safe du {@link TaskCorrelationTracker}.
 * <p>
 * Un etat par messageId : fan-out, claims, grants, reponses. La completion
 * est signalee via des {@link CompletableFuture} (pas de polling).
 * <p>
 * Le tracker est un bean unique dans tous les modes (ADR-059) : en LOCAL
 * l'agent embarque publie des claims et des resultats comme un agent distant.
 */
@Component
public class DefaultTaskCorrelationTracker implements TaskCorrelationTracker {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskCorrelationTracker.class);

    private final ConcurrentHashMap<MessageId, CorrelationState> states = new ConcurrentHashMap<>();

    @Override
    public void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId, FanOut fanOut) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        state.taskId = taskId;
        state.executionId = executionId;
        state.fanOut = fanOut == null ? FanOut.ONE : fanOut;
        log.info("action=track_dispatched messageId={} taskId={} executionId={} fanOut={}",
                messageId.value(), taskId.value(), executionId.value(), state.fanOut);
    }

    @Override
    public GrantDecision onClaimed(MessageId messageId, AgentId agentId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());

        synchronized (state) {
            state.claimedAgents.add(agentId);

            if (state.fanOut == FanOut.ONE) {
                if (state.grantedAgents.isEmpty()) {
                    state.grantedAgents.add(agentId);
                    state.claimSignal.complete(null);
                    log.info("action=claim_granted messageId={} agentId={} fanOut=one",
                            messageId.value(), agentId.value());
                    return GrantDecision.GRANTED;
                }
                log.info("action=claim_rejected messageId={} agentId={} grantedTo={}",
                        messageId.value(), agentId.value(), state.grantedAgents.iterator().next().value());
                return GrantDecision.REJECTED;
            }

            if (state.settled) {
                return GrantDecision.REJECTED;
            }
            state.claimSignal.complete(null);
            log.info("action=claim_pending messageId={} agentId={} fanOut=all",
                    messageId.value(), agentId.value());
            return GrantDecision.PENDING;
        }
    }

    @Override
    public List<AgentId> settleClaims(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            if (state.settled) {
                return List.copyOf(state.grantedAgents);
            }
            state.settled = true;
            state.grantedAgents.addAll(state.claimedAgents);
            state.claimSignal.complete(null);
            signalCompletion(state);
            log.info("action=claims_settled messageId={} granted={}",
                    messageId.value(), state.grantedAgents.size());
            return List.copyOf(state.grantedAgents);
        }
    }

    @Override
    public void onGranted(MessageId messageId, AgentId agentId) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        synchronized (state) {
            state.grantedAgents.add(agentId);
            state.settled = true;
        }
    }

    @Override
    public void onCompleted(MessageId messageId, AgentId agentId, TaskResult result) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        synchronized (state) {
            if (state.completedAgents.add(agentId)) {
                state.completionOrder.add(agentId);
            }
            state.results.put(agentId, result);
            log.info("action=task_completed messageId={} agentId={} taskStatus={} completed={}/{}",
                    messageId.value(), agentId.value(), result.status(),
                    state.completedAgents.size(), Math.max(1, state.grantedAgents.size()));
            signalCompletion(state);
        }
    }

    @Override
    public void onFailed(MessageId messageId, AgentId agentId, String error) {
        CorrelationState state = states.computeIfAbsent(messageId, k -> new CorrelationState());
        synchronized (state) {
            state.failedAgents.add(agentId);
            log.warn("action=task_failed messageId={} agentId={} error={} failed={}/{}",
                    messageId.value(), agentId.value(), error,
                    state.failedAgents.size(), Math.max(1, state.grantedAgents.size()));
            signalCompletion(state);
        }
    }

    @Override
    public Set<AgentId> claimsFor(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        return state == null ? Set.of() : Collections.unmodifiableSet(state.claimedAgents);
    }

    @Override
    public Set<AgentId> grantedFor(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        return state == null ? Set.of() : Collections.unmodifiableSet(state.grantedAgents);
    }

    @Override
    public CompletableFuture<Void> claimSignal(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (state) {
            if (!state.claimedAgents.isEmpty()) {
                state.claimSignal.complete(null);
            }
            return state.claimSignal;
        }
    }

    @Override
    public CompletableFuture<Void> completionSignal(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (state) {
            if (isComplete(state)) {
                state.completionSignal.complete(null);
            }
            return state.completionSignal;
        }
    }

    @Override
    public Map<AgentId, TaskResult> getResults(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return Collections.emptyMap();
        }
        synchronized (state) {
            return new HashMap<>(state.results);
        }
    }

    @Override
    public List<TaskResult> resultsInCompletionOrder(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            var ordered = new ArrayList<TaskResult>(state.completionOrder.size());
            for (AgentId agentId : state.completionOrder) {
                TaskResult result = state.results.get(agentId);
                if (result != null) {
                    ordered.add(result);
                }
            }
            return List.copyOf(ordered);
        }
    }

    @Override
    public TaskId taskIdFor(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        return state != null ? state.taskId : null;
    }

    @Override
    public ExecutionId executionIdFor(MessageId messageId) {
        CorrelationState state = states.get(messageId);
        return state != null ? state.executionId : null;
    }

    @Override
    public void onAgentLost(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        for (var entry : states.entrySet()) {
            CorrelationState state = entry.getValue();
            synchronized (state) {
                if (state.grantedAgents.contains(agentId)
                        && !state.completedAgents.contains(agentId)
                        && state.failedAgents.add(agentId)) {
                    log.warn("action=grant_failed_agent_lost messageId={} agentId={} taskId={}",
                            entry.getKey().value(), agentId.value(),
                            state.taskId != null ? state.taskId.value() : "?");
                    signalCompletion(state);
                }
            }
        }
    }

    @Override
    public void release(MessageId messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        CorrelationState removed = states.remove(messageId);
        if (removed != null) {
            synchronized (removed) {
                removed.claimSignal.complete(null);
                removed.completionSignal.complete(null);
            }
            log.debug("action=correlation_released messageId={} trackedMessages={}",
                    messageId.value(), states.size());
        }
    }

    private static void signalCompletion(CorrelationState state) {
        if (isComplete(state)) {
            state.completionSignal.complete(null);
        }
    }

    /**
     * {@code one} : l'agent accorde a repondu. {@code all} : la fenetre est
     * fermee et tous les agents accordes ont repondu. Aucun agent accorde
     * est traite comme complet (le dispatcher echoue explicitement).
     */
    private static boolean isComplete(CorrelationState state) {
        if (state.fanOut == FanOut.ONE) {
            return state.grantedAgents.stream().anyMatch(state::answered);
        }
        if (state.settled) {
            return state.grantedAgents.stream().allMatch(state::answered);
        }
        return false;
    }

    /**
     * Nombre d'etats de correlation actuellement suivis (tests de fuite memoire).
     */
    int stateCount() {
        return states.size();
    }

    private static class CorrelationState {
        volatile TaskId taskId;
        volatile ExecutionId executionId;
        volatile FanOut fanOut = FanOut.ONE;
        volatile boolean settled;
        final Set<AgentId> claimedAgents = ConcurrentHashMap.newKeySet();
        final Set<AgentId> grantedAgents = ConcurrentHashMap.newKeySet();
        final Set<AgentId> completedAgents = ConcurrentHashMap.newKeySet();
        final Set<AgentId> failedAgents = ConcurrentHashMap.newKeySet();
        final ConcurrentHashMap<AgentId, TaskResult> results = new ConcurrentHashMap<>();
        final List<AgentId> completionOrder = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> claimSignal = new CompletableFuture<>();
        final CompletableFuture<Void> completionSignal = new CompletableFuture<>();

        boolean answered(AgentId agentId) {
            return completedAgents.contains(agentId) || failedAgents.contains(agentId);
        }
    }
}
