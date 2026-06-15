package com.performance.platform.agent.runtime;

import com.performance.platform.agent.filter.TaskFilterResult;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.registration.HeartbeatScheduler;
import com.performance.platform.agent.registration.RegistrationException;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.StepDefinition;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implémentation distribuée de {@link AgentRuntime} pour le mode DISTRIBUTED, role AGENT.
 * <p>
 * Cycle de vie : OFFLINE → REGISTERING → IDLE → EXECUTING → (DRAINING) → OFFLINE.
 * Chaque transition est tracée via SLF4J avec contexte (agentId, executionId, taskName).
 * </p>
 *
 * <h3>Flux de réception d'une tâche</h3>
 * <ol>
 *   <li>Réception broadcast via {@link ExecutionTransport#receiveTask}</li>
 *   <li>Filtrage via {@link TaskSpecializationFilter} — match sur supportedTaskNames</li>
 *   <li>Si {@code Responsible} : vérification d'idempotence (MessageId déjà traité ?)</li>
 *   <li>Publication {@code TASK_CLAIMED} via {@link ExecutionTransport#publishEvent}</li>
 *   <li>Exécution sur {@code Virtual Thread} avec reporting périodique de progression</li>
 *   <li>Publication {@code TASK_COMPLETED} ou {@code TASK_FAILED}</li>
 * </ol>
 *
 * <h3>Multi-claim (ADR-011)</h3>
 * Chaque agent spécialisé exécute indépendamment. Pas de coordination inter-agents.
 * L'orchestrateur consolide les résultats selon {@code TaskCompletionPolicy}.
 *
 * <h3>Idempotence</h3>
 * Les {@code MessageId} déjà traités sont ignorés. Le set est nettoyé périodiquement
 * (rétention basée sur le {@code taskExecutionTimeout}).
 *
 * <h3>Thread safety</h3>
 * Tous les champs mutables utilisent des structures concurrentes :
 * {@link ConcurrentHashMap}, {@link AtomicReference}, {@link AtomicInteger}.
 * L'exécution des tâches utilise {@code Thread.ofVirtual()}.
 */
public class DistributedAgentRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(DistributedAgentRuntime.class);

    // === Dépendances ===

    private final ExecutionTransport transport;
    private final TaskSpecializationFilter filter;
    private final AgentRegistrationPort registrationPort;
    private final HeartbeatScheduler heartbeatScheduler;
    private final Map<String, TaskExecutor> taskExecutors;
    private final AgentDescriptor staticDescriptor;
    private final Duration taskExecutionTimeout;

    // === État concurrent ===

    private final AtomicReference<AgentState> currentState =
            new AtomicReference<>(AgentState.OFFLINE);
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    /** Messages déjà traités (idempotence). Nettoyé périodiquement. */
    private final Set<MessageId> processedMessageIds = ConcurrentHashMap.newKeySet();

    /** Tâches en cours d'exécution, indexées par MessageId (pour annulation). */
    private final ConcurrentMap<MessageId, Future<?>> activeTasks = new ConcurrentHashMap<>();

    /** Scheduler pour les rapports de progression périodiques. */
    private final ScheduledExecutorService progressScheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    /** Scheduler pour le nettoyage des MessageId expirés. */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private volatile ScheduledFuture<?> cleanupFuture;

    /** Executor pour l'exécution des tâches (Virtual Threads). */
    private final ExecutorService taskExecutorService = Executors.newVirtualThreadPerTaskExecutor();

    /** TaskName utilisé pour les TaskResult wrappers dans le bridge PartialExecutionContext → ExecutionContext. */
    private static final String PARTIAL_TASK_WRAPPER = "_partial_";

    // === Verrou pour start/stop ===

    private final Object lifecycleLock = new Object();
    private volatile boolean started = false;
    private volatile boolean stopped = false;

    /**
     * Construit un runtime d'agent distribué.
     *
     * @param transport            le transport de communication
     * @param filter               le filtre de spécialisation
     * @param registrationPort     le port d'enregistrement
     * @param descriptor           le descripteur statique de l'agent (id, nom, capacités, etc.)
     * @param heartbeatInterval    l'intervalle entre deux heartbeats
     * @param taskExecutionTimeout le timeout d'exécution d'une tâche (utilisé pour le reporting)
     * @param taskExecutors        la liste des {@link TaskExecutor} disponibles (résolus par taskName)
     */
    public DistributedAgentRuntime(
            ExecutionTransport transport,
            TaskSpecializationFilter filter,
            AgentRegistrationPort registrationPort,
            AgentDescriptor descriptor,
            Duration heartbeatInterval,
            Duration taskExecutionTimeout,
            List<TaskExecutor> taskExecutors
    ) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.filter = Objects.requireNonNull(filter, "filter must not be null");
        this.registrationPort = Objects.requireNonNull(registrationPort, "registrationPort must not be null");
        this.staticDescriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.taskExecutionTimeout = Objects.requireNonNull(taskExecutionTimeout, "taskExecutionTimeout must not be null");
        if (taskExecutionTimeout.isNegative() || taskExecutionTimeout.isZero()) {
            throw new IllegalArgumentException("taskExecutionTimeout must be positive, got " + taskExecutionTimeout);
        }
        Objects.requireNonNull(taskExecutors, "taskExecutors must not be null");
        this.taskExecutors = taskExecutors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TaskExecutor::getSupportedTaskName,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalArgumentException(
                                    "Duplicate TaskExecutor for taskName=" + a.getSupportedTaskName());
                        }
                ));

        var heartbeatSeconds = Math.toIntExact(heartbeatInterval.getSeconds());
        if (heartbeatSeconds < 1) {
            throw new IllegalArgumentException("heartbeatInterval must be >= 1 second");
        }
        this.heartbeatScheduler = new HeartbeatScheduler(
                registrationPort,
                descriptor.id(),
                heartbeatSeconds,
                this::getState,
                activeTaskCount::get
        );
    }

    // === AgentRuntime ===

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (started) {
                log.warn("action=start_ignored agentId={} reason=already_started",
                        staticDescriptor.id().value());
                return;
            }
            started = true;
            stopped = false;
        }

        log.info("action=agent_starting agentId={} name={} supportedTasks={}",
                staticDescriptor.id().value(), staticDescriptor.name(),
                staticDescriptor.supportedTaskNames());

        try {
            // 1. Connexion au transport
            transport.connect();
            log.info("action=transport_connected agentId={}", staticDescriptor.id().value());

            // 2. Enregistrement auprès de l'orchestrateur
            currentState.set(AgentState.REGISTERING);
            registrationPort.register(liveDescriptor());
            log.info("action=agent_registered agentId={} name={}",
                    staticDescriptor.id().value(), staticDescriptor.name());

            // 3. Démarrage du heartbeat
            heartbeatScheduler.start();

            // 4. Abonnement aux tâches et signaux
            transport.receiveTask(this::onTaskReceived);
            transport.receiveSignal(this::onSignalReceived);

            // 5. Nettoyage périodique des MessageId expirés
            long cleanupIntervalSeconds = Math.max(60, taskExecutionTimeout.toSeconds() * 2);
            cleanupFuture = cleanupScheduler.scheduleAtFixedRate(
                    this::cleanupExpiredMessageIds,
                    cleanupIntervalSeconds,
                    cleanupIntervalSeconds,
                    TimeUnit.SECONDS
            );

            currentState.set(AgentState.IDLE);
            log.info("action=agent_started agentId={} state=IDLE heartbeatInterval={}s ttl={}s",
                    staticDescriptor.id().value(),
                    heartbeatScheduler.registrationTtlSeconds() / 3,
                    heartbeatScheduler.registrationTtlSeconds());

        } catch (TransportException | RegistrationException e) {
            currentState.set(AgentState.OFFLINE);
            log.error("action=agent_start_failed agentId={}", staticDescriptor.id().value(), e);
            throw new RegistrationException(
                    "Failed to start agent: " + staticDescriptor.id().value(), e);
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            if (!started || stopped) {
                log.warn("action=stop_ignored agentId={} started={} stopped={}",
                        staticDescriptor.id().value(), started, stopped);
                return;
            }
            stopped = true;
        }

        log.info("action=agent_stopping agentId={} activeTasks={}",
                staticDescriptor.id().value(), activeTaskCount.get());
        currentState.set(AgentState.DRAINING);

        // Attendre la fin des tâches actives (avec timeout)
        long drainTimeoutMs = taskExecutionTimeout.toMillis() * 2;
        long deadline = System.currentTimeMillis() + drainTimeoutMs;
        while (activeTaskCount.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remaining = activeTaskCount.get();
        if (remaining > 0) {
            log.warn("action=drain_timeout agentId={} remainingTasks={} cancelling",
                    staticDescriptor.id().value(), remaining);
            // Annuler les tâches restantes
            for (var entry : activeTasks.entrySet()) {
                entry.getValue().cancel(true);
            }
            activeTasks.clear();
            activeTaskCount.set(0);
        }

        // Arrêter le heartbeat
        heartbeatScheduler.stop();

        // Arrêter le nettoyage périodique
        var cf = cleanupFuture;
        if (cf != null) {
            cf.cancel(false);
        }

        // Désenregistrement
        try {
            registrationPort.deregister(staticDescriptor.id());
        } catch (RegistrationException e) {
            log.warn("action=deregister_failed agentId={} (continuing stop)",
                    staticDescriptor.id().value(), e);
        }

        // Déconnexion du transport
        try {
            transport.disconnect();
        } catch (Exception e) {
            log.warn("action=disconnect_failed agentId={} (continuing stop)",
                    staticDescriptor.id().value(), e);
        }

        currentState.set(AgentState.OFFLINE);
        log.info("action=agent_stopped agentId={} state=OFFLINE", staticDescriptor.id().value());
    }

    @Override
    public AgentState getState() {
        return currentState.get();
    }

    @Override
    public AgentDescriptor getDescriptor() {
        return liveDescriptor();
    }

    @Override
    public boolean canExecute(String taskName) {
        return staticDescriptor.supportedTaskNames().contains(taskName);
    }

    @Override
    public void onScenarioRestart(ScenarioRestartSignal signal) {
        log.info("action=scenario_restart agentId={} executionId={} reason={}",
                staticDescriptor.id().value(),
                signal.executionId() != null ? signal.executionId().value() : "ALL",
                signal.reason());

        // Annuler les tâches correspondant à l'executionId (ou toutes si null)
        var targetExecutionId = signal.executionId();
        var toCancel = new ArrayList<Map.Entry<MessageId, Future<?>>>();

        for (var entry : activeTasks.entrySet()) {
            if (targetExecutionId == null || matchesExecutionId(entry.getKey(), targetExecutionId)) {
                toCancel.add(entry);
            }
        }

        for (var entry : toCancel) {
            log.info("action=cancel_task agentId={} messageId={} reason=scenario_restart",
                    staticDescriptor.id().value(), entry.getKey().value());
            entry.getValue().cancel(true);
            // Remove atomically — si déjà retiré par executeTask, ne pas double-décrémenter
            if (activeTasks.remove(entry.getKey()) != null) {
                activeTaskCount.decrementAndGet();
            }
        }

        // Repasser à IDLE si plus aucune tâche active
        if (activeTaskCount.get() == 0) {
            currentState.compareAndSet(AgentState.EXECUTING, AgentState.IDLE);
            currentState.compareAndSet(AgentState.DRAINING, AgentState.IDLE);
        }

        log.info("action=scenario_restart_complete agentId={} cancelledTasks={} remainingTasks={}",
                staticDescriptor.id().value(), toCancel.size(), activeTaskCount.get());
    }

    // === Réception de tâche (broadcast) ===

    /**
     * Handler appelé par le transport à la réception d'une {@link TaskExecutionRequest}.
     * <p>
     * Exécuté sur le thread du transport (peut être un Virtual Thread selon l'implémentation).
     * La tâche elle-même est soumise à un Virtual Thread dédié.
     */
    private void onTaskReceived(TaskExecutionRequest request) {
        if (currentState.get() == AgentState.OFFLINE || currentState.get() == AgentState.DRAINING) {
            log.debug("action=task_ignored agentId={} messageId={} state={}",
                    staticDescriptor.id().value(), request.id().value(), currentState.get());
            return;
        }

        // 1. Filtrage
        var filterResult = filter.filter(request);
        if (filterResult instanceof TaskFilterResult.NotResponsible) {
            log.debug("action=task_not_responsible agentId={} messageId={} taskName={}",
                    staticDescriptor.id().value(), request.id().value(),
                    request.step().taskName());
            return;
        }

        // 2. Idempotence
        if (!processedMessageIds.add(request.id())) {
            log.info("action=task_duplicate agentId={} messageId={} taskName={}",
                    staticDescriptor.id().value(), request.id().value(),
                    request.step().taskName());
            return;
        }

        log.info("action=task_claimed agentId={} executionId={} messageId={} taskName={}",
                staticDescriptor.id().value(), request.executionId().value(),
                request.id().value(), request.step().taskName());

        // 3. Publication du claim
        publishExecutionEvent(request, ExecutionEvent.TASK_CLAIMED, Map.of(
                "taskName", request.step().taskName(),
                "agentId", staticDescriptor.id().value()
        ));

        // 4. Transition d'état
        currentState.compareAndSet(AgentState.IDLE, AgentState.EXECUTING);
        activeTaskCount.incrementAndGet();

        // 5. Exécution asynchrone sur Virtual Thread
        var future = taskExecutorService.submit(() -> executeTask(request));
        activeTasks.put(request.id(), future);
    }

    // === Réception de signal ===

    private void onSignalReceived(AgentSignal signal) {
        log.info("action=signal_received agentId={} signalType={}",
                staticDescriptor.id().value(), signal.getClass().getSimpleName());

        if (signal instanceof ScenarioRestartSignal restartSignal) {
            onScenarioRestart(restartSignal);
        }
    }

    // === Exécution de tâche ===

    /**
     * Exécute une tâche sur le thread courant (Virtual Thread).
     * Gère le reporting périodique de progression et la publication du résultat.
     */
    private void executeTask(TaskExecutionRequest request) {
        var startTime = Instant.now();
        var step = request.step();
        var executionId = request.executionId();
        var taskId = step.id();
        var agentId = staticDescriptor.id();
        var messageId = request.id();

        ScheduledFuture<?> progressFuture = null;

        try {
            // Démarrer le reporting périodique de progression
            long progressIntervalMs = Math.max(1_000, taskExecutionTimeout.toMillis() / 3);
            progressFuture = progressScheduler.scheduleAtFixedRate(
                    () -> publishProgress(executionId, taskId, agentId, messageId),
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

    private void publishExecutionEvent(TaskExecutionRequest request, String eventType,
                                        Map<String, Object> extraPayload) {
        var payload = new LinkedHashMap<>(extraPayload);
        payload.put("taskName", request.step().taskName());
        payload.put("taskId", request.step().id().value());

        var event = ExecutionEvent.of(
                EventId.generate(),
                request.executionId(),
                request.id(),
                staticDescriptor.id(),
                eventType,
                payload,
                Instant.now()
        );

        try {
            transport.publishEvent(event);
        } catch (TransportException e) {
            log.warn("action=publish_event_failed agentId={} eventType={} executionId={}",
                    staticDescriptor.id().value(), eventType,
                    request.executionId().value(), e);
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
                staticDescriptor.id(),
                eventType,
                payload,
                Instant.now()
        );

        try {
            transport.publishEvent(event);
        } catch (TransportException e) {
            log.warn("action=publish_result_failed agentId={} eventType={} executionId={}",
                    staticDescriptor.id().value(), eventType,
                    request.executionId().value(), e);
        }
    }

    private void publishProgress(ExecutionId executionId, TaskId taskId,
                                  AgentId agentId, MessageId messageId) {
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
    private ExecutionContext toExecutionContext(PartialExecutionContext partial) {
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

    // === Nettoyage des MessageId expirés ===

    /**
     * Nettoie périodiquement le set des MessageId déjà traités pour éviter
     * une fuite mémoire. Supprime les entrées plus anciennes que
     * {@code 2 × taskExecutionTimeout}.
     */
    private void cleanupExpiredMessageIds() {
        // Stratégie simple : on nettoie si le set dépasse un seuil raisonnable
        // car on ne stocke pas de timestamp par MessageId.
        // On vide complètement le set — les messages en double arrivent
        // dans une fenêtre courte (retry transport), donc un nettoyage
        // périodique à 2× taskExecutionTimeout est sûr.
        int beforeSize = processedMessageIds.size();
        if (beforeSize > 10_000) {
            processedMessageIds.clear();
            log.info("action=cleanup_message_ids agentId={} before={} after=0",
                    staticDescriptor.id().value(), beforeSize);
        }
    }

    // === Helpers ===

    /**
     * Construit un {@link AgentDescriptor} reflétant l'état courant.
     */
    private AgentDescriptor liveDescriptor() {
        return new AgentDescriptor(
                staticDescriptor.id(),
                staticDescriptor.name(),
                staticDescriptor.host(),
                staticDescriptor.port(),
                staticDescriptor.httpCallbackUrl(),
                staticDescriptor.supportedTaskNames(),
                staticDescriptor.capabilities(),
                currentState.get(),
                staticDescriptor.registeredAt(),
                Instant.now(), // lastHeartbeatAt mis à jour localement
                Duration.ofSeconds(heartbeatScheduler.registrationTtlSeconds())
        );
    }

    /**
     * Vérifie si un MessageId est associé à un ExecutionId donné.
     * Simplification : on ne peut pas retrouver l'executionId depuis le MessageId seul
     * sans le conserver. Pour le restart, on annule toutes les tâches si executionId
     * n'est pas trouvé dans le cache.
     */
    private boolean matchesExecutionId(MessageId messageId, ExecutionId targetExecutionId) {
        // Note: on ne conserve pas le mapping MessageId → ExecutionId dans cette version.
        // En pratique, un ScenarioRestart avec executionId non-null annulera toutes les
        // tâches (comportement conservateur). L'optimisation viendra avec ISSUE-037.
        return true;
    }

    // === Accesseurs package-private pour les tests ===

    int activeTaskCount() {
        return activeTaskCount.get();
    }

    int processedMessageCount() {
        return processedMessageIds.size();
    }

    boolean isStarted() {
        return started;
    }

    boolean isStopped() {
        return stopped;
    }

    int executorCount() {
        return taskExecutors.size();
    }
}
