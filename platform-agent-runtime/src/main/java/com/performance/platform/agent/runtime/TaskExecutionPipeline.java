package com.performance.platform.agent.runtime;

import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pipeline d'exécution de tâche côté agent distribué.
 * <p>
 * Encapsule le cycle de vie complet de l'exécution d'une tâche :
 * reporting de progression, résolution du {@link TaskExecutor},
 * conversion {@link PartialExecutionContext} → {@link ExecutionContext},
 * exécution, publication du résultat, et nettoyage final.
 * <p>
 * Utilisé par {@link DistributedAgentRuntime} et {@code LocalAgent}.
 */
public class TaskExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionPipeline.class);

    /** TaskName utilisé pour les TaskResult wrappers dans le bridge PartialExecutionContext → ExecutionContext. */
    static final String PARTIAL_TASK_WRAPPER = "_partial_";

    private final ExecutionTransport transport;
    private final Map<String, TaskExecutor> taskExecutors;
    private final AgentId agentId;
    private final ScheduledExecutorService progressScheduler;
    private final Duration taskExecutionTimeout;
    private final ConcurrentMap<MessageId, Future<?>> activeTasks;
    private final AtomicInteger activeTaskCount;
    private final AtomicReference<AgentState> currentState;

    public TaskExecutionPipeline(
            ExecutionTransport transport,
            Map<String, TaskExecutor> taskExecutors,
            AgentId agentId,
            ScheduledExecutorService progressScheduler,
            Duration taskExecutionTimeout,
            ConcurrentMap<MessageId, Future<?>> activeTasks,
            AtomicInteger activeTaskCount,
            AtomicReference<AgentState> currentState
    ) {
        this.transport = transport;
        this.taskExecutors = taskExecutors;
        this.agentId = agentId;
        this.progressScheduler = progressScheduler;
        this.taskExecutionTimeout = taskExecutionTimeout;
        this.activeTasks = activeTasks;
        this.activeTaskCount = activeTaskCount;
        this.currentState = currentState;
    }

    public int executorCount() {
        return taskExecutors.size();
    }

    /**
     * Exécute une tâche sur le thread courant (Virtual Thread).
     * <p>
     * Gère le reporting périodique de progression, la résolution du TaskExecutor,
     * la conversion du contexte, l'exécution, la publication du résultat,
     * et le nettoyage final (décrément du compteur, transition d'état).
     */
    public void execute(TaskExecutionRequest request) {
        var startTime = Instant.now();
        var step = request.step();
        var executionId = request.executionId();
        var taskId = step.id();
        var messageId = request.id();

        ScheduledFuture<?> progressFuture = null;

        try {
            // Démarrer le reporting périodique de progression
            long progressIntervalMs = Math.max(1_000, taskExecutionTimeout.toMillis() / 3);
            progressFuture = progressScheduler.scheduleAtFixedRate(
                    () -> publishProgress(executionId, taskId, messageId),
                    progressIntervalMs,
                    progressIntervalMs,
                    TimeUnit.MILLISECONDS
            );

            // Résoudre le TaskExecutor
            var executor = taskExecutors.get(step.taskName());
            if (executor == null) {
                log.error("action=no_executor agentId={} taskName={} messageId={}",
                        agentId.value(), step.taskName(), messageId.value());
                var failedResult = TaskResult.failed(
                        taskId, step.taskName(), Duration.ZERO,
                        "No TaskExecutor registered for taskName=" + step.taskName(), null);
                publishTaskCompleted(request, failedResult);
                return;
            }

            // Construire l'ExecutionContext à partir du PartialExecutionContext
            var executionContext = toExecutionContext(request.context());

            // Exécuter la tâche
            var result = executor.execute(executionContext, step);
            var duration = Duration.between(startTime, Instant.now());

            // Publier le résultat
            publishTaskCompleted(request, result);

            log.info("action=task_executed agentId={} executionId={} taskName={} status={} durationMs={}",
                    agentId.value(), executionId.value(), step.taskName(),
                    result.status(), duration.toMillis());

        } catch (Exception e) {
            log.error("action=task_execution_error agentId={} executionId={} taskName={}",
                    agentId.value(), executionId.value(), step.taskName(), e);
            var duration = Duration.between(startTime, Instant.now());
            var failedResult = TaskResult.failed(
                    taskId, step.taskName(), duration, e.getMessage(), e);
            publishTaskCompleted(request, failedResult);

        } finally {
            // Arrêter le reporting de progression
            if (progressFuture != null) {
                progressFuture.cancel(false);
            }

            // Nettoyer (remove retourne null si déjà retiré par onScenarioRestart)
            if (activeTasks.remove(messageId) != null) {
                int remaining = activeTaskCount.decrementAndGet();

                // Transition d'état si plus aucune tâche active
                if (remaining == 0) {
                    var current = currentState.get();
                    if (current == AgentState.EXECUTING) {
                        currentState.compareAndSet(AgentState.EXECUTING, AgentState.IDLE);
                    }
                }
            }
        }
    }

    // === Helpers de publication d'événements ===

    public void publishClaimEvent(TaskExecutionRequest request) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("taskName", request.step().taskName());
        payload.put("agentId", agentId.value());

        var event = ExecutionEvent.of(
                EventId.generate(),
                request.executionId(),
                request.id(),
                agentId,
                ExecutionEvent.TASK_CLAIMED,
                payload,
                Instant.now()
        );

        try {
            transport.publishEvent(event);
        } catch (TransportException e) {
            log.warn("action=publish_claim_failed agentId={} executionId={}",
                    agentId.value(), request.executionId().value(), e);
        }
    }

    private void publishTaskCompleted(TaskExecutionRequest request, TaskResult result) {
        var eventType = result.isSuccess()
                ? ExecutionEvent.TASK_COMPLETED
                : ExecutionEvent.TASK_FAILED;

        var payload = new LinkedHashMap<String, Object>();
        payload.put("taskName", result.taskName());
        payload.put("taskId", result.taskId().value());
        payload.put("status", result.status().name());
        payload.put("durationMs", result.duration().toMillis());
        if (!result.isSuccess() && result.errorMessage() != null) {
            payload.put("error", result.errorMessage());
        }
        // Inclure les outputs pour TASK_COMPLETED
        if (result.isSuccess() && !result.outputs().isEmpty()) {
            payload.put("outputs", result.outputs());
        }

        var event = ExecutionEvent.of(
                EventId.generate(),
                request.executionId(),
                request.id(),
                agentId,
                eventType,
                payload,
                Instant.now()
        );

        try {
            transport.publishEvent(event);
        } catch (TransportException e) {
            log.warn("action=publish_result_failed agentId={} eventType={} executionId={}",
                    agentId.value(), eventType, request.executionId().value(), e);
        }
    }

    private void publishProgress(ExecutionId executionId, TaskId taskId, MessageId messageId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("taskId", taskId.value());
        payload.put("agentId", agentId.value());
        payload.put("messageId", messageId.value());

        var event = ExecutionEvent.of(
                EventId.generate(),
                executionId,
                messageId,
                agentId,
                ExecutionEvent.TASK_WORK_IN_PROGRESS,
                payload,
                Instant.now()
        );

        try {
            transport.publishEvent(event);
        } catch (TransportException e) {
            log.debug("action=publish_progress_failed agentId={} executionId={}",
                    agentId.value(), executionId.value(), e);
        }
    }

    // === Conversion PartialExecutionContext → ExecutionContext ===

    /**
     * Convertit un {@link PartialExecutionContext} (côté agent) en
     * {@link ExecutionContext} compatible avec {@link TaskExecutor#execute}.
     * <p>
     * Chaque entrée du store partiel (Object) est wrappée dans un {@link TaskResult}
     * factice pour satisfaire le contrat de {@link ExecutionContext#get}
     * et {@link ExecutionContext#getFirst}.
     */
    static ExecutionContext toExecutionContext(PartialExecutionContext partial) {
        Map<String, Map<String, TaskResult>> store = new HashMap<>();

        for (var entry : partial.store().entrySet()) {
            var taskId = entry.getKey();
            var agentResults = new HashMap<String, TaskResult>();
            for (var agentEntry : entry.getValue().entrySet()) {
                var wrapperResult = TaskResult.success(
                        TaskId.of(taskId),
                        PARTIAL_TASK_WRAPPER,
                        Duration.ZERO,
                        Map.of("value", agentEntry.getValue())
                );
                agentResults.put(agentEntry.getKey(), wrapperResult);
            }
            store.put(taskId, Map.copyOf(agentResults));
        }

        return new ExecutionContext(
                partial.executionId(),
                partial.scenarioId(),
                Map.copyOf(store)
        );
    }
}
