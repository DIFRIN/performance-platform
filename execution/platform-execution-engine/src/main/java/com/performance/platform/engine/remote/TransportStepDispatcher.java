package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.TaskDispatched;
import com.performance.platform.domain.event.TaskGrantSignal;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.scenario.AgentLossPolicy;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.shared.StepDispatchResult;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.transport.SignalChannel;
import com.performance.platform.transport.TaskChannel;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation unique du {@link StepDispatcher} (ADR-059).
 * <p>
 * Le moteur dispatche toujours via les ports de transport ; en LOCAL le
 * transport est le transport en memoire et l'agent est embarque. Le mode
 * n'est lu que par la racine de composition.
 * <p>
 * Dispatch en deux temps (ADR-055) : l'orchestrateur diffuse la demande,
 * collecte les claims, puis accorde ({@code fanOut: one}) ou accorde tous
 * les claims de la fenetre ({@code fanOut: all}). Un agent n'execute
 * qu'apres avoir recu son grant.
 */
public class TransportStepDispatcher implements StepDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TransportStepDispatcher.class);

    private static final String NO_AGENT_GRANTED = "No agent was granted the task";

    private final TaskChannel taskChannel;
    private final SignalChannel signalChannel;
    private final AgentAvailabilityChecker availabilityChecker;
    private final TaskCorrelationTracker tracker;
    private final ExecutionConfig config;
    private final ApplicationEventPublisher eventPublisher;

    /** Service demarres : executionId::taskId -> messageId (ADR-054). */
    private final Map<String, MessageId> runningServices = new HashMap<>();

    public TransportStepDispatcher(
            TaskChannel taskChannel,
            SignalChannel signalChannel,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionConfig config,
            ApplicationEventPublisher eventPublisher) {
        this.taskChannel = taskChannel;
        this.signalChannel = signalChannel;
        this.availabilityChecker = availabilityChecker;
        this.tracker = tracker;
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<StepDispatchResult> dispatch(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();
        RetryPolicy retry = stepDef.retryPolicy() != null ? stepDef.retryPolicy() : RetryPolicy.defaults();
        int maxAttempts = Math.max(1, retry.maxAttempts());
        List<StepDispatchResult> last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            last = dispatchOnce(execStep, context);
            if (!isTransientFailure(last) || attempt == maxAttempts) {
                return last;
            }
            log.warn("action=step_retry taskId={} attempt={}/{} reason={}",
                    stepDef.id().value(), attempt, maxAttempts, last.getFirst().result().errorMessage());
            try {
                Thread.sleep(retryDelay(retry, attempt).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return failure(stepDef, "Interrupted before retry");
            }
        }
        return last == null ? failure(stepDef, "No dispatch attempt") : last;
    }

    private List<StepDispatchResult> dispatchOnce(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();
        try {
            availabilityChecker.awaitAgentFor(stepDef.taskName(), config.taskAvailabilityTimeout());
            MessageId messageId = dispatchRequest(execStep, context);
            try {
                String grantFailure = awaitGrant(messageId, stepDef);
                if (grantFailure != null) {
                    return failure(stepDef, grantFailure);
                }
                Duration timeout = stepDef.timeout() != null ? stepDef.timeout() : config.taskExecutionTimeout();
                boolean abortOnLoss = stepDef.fanOut() == FanOut.ALL
                        && stepDef.onAgentLoss() == AgentLossPolicy.ABORT;
                if (!awaitCompletion(messageId, timeout, abortOnLoss)) {
                    return failure(stepDef, "Task execution timed out");
                }
                if (abortOnLoss && !tracker.lostFor(messageId).isEmpty()) {
                    return agentLostFailure(stepDef, messageId);
                }
                return results(stepDef, messageId);
            } finally {
                tracker.release(messageId);
            }
        } catch (NoAvailableAgentException e) {
            log.warn("action=no_agent_available taskName={} error={}", stepDef.taskName(), e.getMessage());
            return failure(stepDef, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(stepDef, "Interrupted while awaiting task completion");
        }
    }

    /**
     * Seuls les echecs transitoires (pas d'agent accorde, timeout) sont
     * rejoues : un echec metier du TaskExecutor ne doit pas etre rejoue.
     */
    private static boolean isTransientFailure(List<StepDispatchResult> results) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        return results.stream().allMatch(r -> r.result().status() == TaskStatus.FAILED)
                && results.getFirst().result().cause() == null;
    }

    private static Duration retryDelay(RetryPolicy retry, int attempt) {
        double factor = Math.pow(retry.multiplier(), Math.max(0, attempt - 1));
        long delayMs = (long) (retry.initialDelay().toMillis() * factor);
        long capped = Math.min(delayMs, retry.maxDelay().toMillis());
        return Duration.ofMillis(Math.max(0, capped));
    }

    @Override
    public void startService(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();
        String key = serviceKey(context, stepDef);
        try {
            availabilityChecker.awaitAgentFor(stepDef.taskName(), config.taskAvailabilityTimeout());
            MessageId messageId = dispatchRequest(execStep, context);
            String grantFailure = awaitGrant(messageId, stepDef);
            if (grantFailure != null) {
                tracker.release(messageId);
                throw new IllegalStateException(
                        grantFailure + " (service step " + stepDef.id().value() + ")");
            }
            runningServices.put(key, messageId);
            log.info("action=service_started taskId={} messageId={} executionId={}",
                    stepDef.id().value(), messageId.value(), context.executionId().value());
        } catch (NoAvailableAgentException e) {
            throw new IllegalStateException("No agent available for service " + stepDef.id().value(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting service " + stepDef.id().value(), e);
        }
    }

    @Override
    public List<StepDispatchResult> stopService(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();
        String key = serviceKey(context, stepDef);
        MessageId messageId = runningServices.remove(key);
        if (messageId == null) {
            return failure(stepDef, "Service was not started: " + stepDef.id().value());
        }
        try {
            var params = new HashMap<String, Object>(stepDef.parameters());
            params.put(ExecutionLifecycleSignal.PARAM_TASK_NAME, stepDef.taskName());
            signalChannel.broadcastSignal(ExecutionLifecycleSignal.stop(
                    SignalId.generate(), context.executionId(), stepDef.id(), Map.copyOf(params)));

            Duration timeout = stepDef.timeout() != null ? stepDef.timeout() : config.taskExecutionTimeout();
            if (!awaitCompletion(messageId, timeout)) {
                return failure(stepDef, "Service stop timed out");
            }
            return results(stepDef, messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(stepDef, "Interrupted while stopping service");
        } finally {
            tracker.release(messageId);
        }
    }

    // ==================== Helpers ====================

    private MessageId dispatchRequest(ExecutionStep execStep, ExecutionContext context) {
        StepDefinition stepDef = execStep.step();
        var partialCtx = PartialContextBuilder.build(context, execStep.requiredContextKeys());
        MessageId messageId = MessageId.generate();
        RetryPolicy retry = stepDef.retryPolicy() != null ? stepDef.retryPolicy() : RetryPolicy.defaults();
        var request = new TaskExecutionRequest(
                messageId, context.executionId(), stepDef, partialCtx, Instant.now(), retry);

        tracker.trackDispatched(messageId, stepDef.id(), context.executionId(), stepDef.fanOut());
        taskChannel.dispatchTask(request);
        eventPublisher.publishEvent(new TaskDispatched(
                context.executionId(), stepDef.id(), stepDef.taskName(), messageId, Instant.now()));

        log.info("action=task_dispatched taskId={} taskName={} messageId={} fanOut={}",
                stepDef.id().value(), stepDef.taskName(), messageId.value(), stepDef.fanOut());
        return messageId;
    }

    /**
     * Attend les claims : premier claim en {@code one}, fenetre de claim en
     * {@code all} puis settlement. Les grants {@code one} sont diffuses par
     * le handler d'evenements ; ceux de {@code all} sont diffuses ici, avec
     * leur part egale (ADR-067) apres controle de {@code minAgents}.
     *
     * @return {@code null} si accorde, sinon le motif d'echec
     */
    private String awaitGrant(MessageId messageId, StepDefinition stepDef) throws InterruptedException {
        FanOut fanOut = stepDef.fanOut();
        Duration stepTimeout = stepDef.timeout();
        Duration claimWindow = stepTimeout != null && stepTimeout.compareTo(config.claimWindow()) < 0
                ? stepTimeout
                : config.claimWindow();
        long windowStart = System.nanoTime();
        try {
            tracker.claimSignal(messageId).get(claimWindow.toMillis(), TimeUnit.MILLISECONDS);
            if (fanOut == FanOut.ALL) {
                // Le premier claim ne ferme pas la fenetre : les autres agents peuvent encore claimer (ADR-067).
                long remainingMs = claimWindow.toMillis() - (System.nanoTime() - windowStart) / 1_000_000;
                if (remainingMs > 0) {
                    Thread.sleep(remainingMs);
                }
            }
        } catch (TimeoutException e) {
            if (fanOut == FanOut.ALL) {
                // Fenetre ecoulee sans claim : settlement a vide, controle minAgents plus bas.
                log.debug("action=claim_window_closed_without_claim messageId={}", messageId.value());
            } else {
                log.warn("action=claim_timeout messageId={} windowMs={}",
                        messageId.value(), claimWindow.toMillis());
                return NO_AGENT_GRANTED;
            }
        } catch (ExecutionException e) {
            log.warn("action=claim_signal_failed messageId={}", messageId.value(), e);
            return NO_AGENT_GRANTED;
        }

        if (fanOut == FanOut.ALL) {
            List<AgentId> granted = tracker.settleClaims(messageId).stream()
                    .sorted(java.util.Comparator.comparing(AgentId::value))
                    .toList();
            var executionId = tracker.executionIdFor(messageId);
            var taskId = tracker.taskIdFor(messageId);
            int required = stepDef.minAgents();
            if (granted.size() < required) {
                log.warn("action=min_agents_not_met granted={} required={} messageId={}",
                        granted.size(), required, messageId.value());
                for (AgentId agentId : granted) {
                    signalChannel.broadcastSignal(new TaskGrantSignal(
                            SignalId.generate(), executionId, taskId, messageId, agentId,
                            false, Instant.now(), GrantShare.single()));
                }
                return "min_agents_not_met granted=" + granted.size() + " required=" + required;
            }
            int count = granted.size();
            for (int i = 0; i < count; i++) {
                AgentId agentId = granted.get(i);
                tracker.onGranted(messageId, agentId);
                signalChannel.broadcastSignal(new TaskGrantSignal(
                        SignalId.generate(), executionId, taskId, messageId, agentId, true,
                        Instant.now(), GrantShare.equal(i, count)));
            }
            log.info("action=claims_settled messageId={} granted={}", messageId.value(), count);
        }

        return tracker.grantedFor(messageId).isEmpty() ? NO_AGENT_GRANTED : null;
    }

    private boolean awaitCompletion(MessageId messageId, Duration timeout) throws InterruptedException {
        return awaitCompletion(messageId, timeout, false);
    }

    /**
     * @param abortOnLoss si vrai, la perte d'un agent accorde interrompt l'attente
     *                    (politique ABORT) ; le cleanup existant libere les agents restants
     */
    private boolean awaitCompletion(MessageId messageId, Duration timeout, boolean abortOnLoss)
            throws InterruptedException {
        try {
            CompletableFuture<Void> signal = abortOnLoss
                    ? CompletableFuture.anyOf(tracker.completionSignal(messageId), tracker.lossSignal(messageId))
                            .thenApply(o -> (Void) null)
                    : tracker.completionSignal(messageId);
            signal.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            log.warn("action=completion_timeout messageId={} timeoutMs={}",
                    messageId.value(), timeout.toMillis());
            return false;
        } catch (ExecutionException e) {
            log.warn("action=completion_failed messageId={}", messageId.value(), e);
            return false;
        }
    }

    private List<StepDispatchResult> results(StepDefinition stepDef, MessageId messageId) {
        Map<AgentId, TaskResult> results = tracker.getResults(messageId);
        if (results.isEmpty()) {
            return failure(stepDef, "No results received");
        }
        boolean all = stepDef.fanOut() == FanOut.ALL;
        int granted = tracker.grantedFor(messageId).size();
        int reporting = results.size();
        var ordered = new ArrayList<StepDispatchResult>(results.size());
        results.keySet().stream()
                .sorted(java.util.Comparator.comparing(AgentId::value))
                .forEach(agentId -> ordered.add(new StepDispatchResult(agentId,
                        all ? withCounters(results.get(agentId), granted, reporting) : results.get(agentId))));
        return ordered;
    }

    private static TaskResult withCounters(TaskResult r, int granted, int reporting) {
        var outputs = new HashMap<String, Object>(r.outputs() == null ? Map.of() : r.outputs());
        outputs.put("grantedAgents", granted);
        outputs.put("reportingAgents", reporting);
        return new TaskResult(r.taskId(), r.taskName(), r.status(), r.duration(), Map.copyOf(outputs),
                r.errorMessage(), r.cause(), r.completedAt());
    }

    private List<StepDispatchResult> agentLostFailure(StepDefinition stepDef, MessageId messageId) {
        String lost = tracker.lostFor(messageId).stream().map(AgentId::value).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        log.warn("action=step_aborted_agent_lost taskId={} messageId={} lost={}",
                stepDef.id().value(), messageId.value(), lost);
        String message = "agent_lost: " + lost + " (onAgentLoss=abort)";
        // cause non nulle : echec non transitoire, pas de retry.
        return List.of(new StepDispatchResult(
                AgentId.of(TransportStepDispatcher.class.getSimpleName()),
                TaskResult.failed(stepDef.id(), stepDef.taskName(), Duration.ZERO, message,
                        new IllegalStateException(message))));
    }

    private static List<StepDispatchResult> failure(StepDefinition stepDef, String message) {
        return List.of(new StepDispatchResult(
                AgentId.of(TransportStepDispatcher.class.getSimpleName()),
                TaskResult.failed(stepDef.id(), stepDef.taskName(), Duration.ZERO, message, null)));
    }

    private static String serviceKey(ExecutionContext context, StepDefinition stepDef) {
        return context.executionId().value() + "::" + stepDef.id().value();
    }
}
