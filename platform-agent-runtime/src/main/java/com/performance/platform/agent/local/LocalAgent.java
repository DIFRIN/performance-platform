package com.performance.platform.agent.local;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskFilterResult;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.restart.ScenarioRestartHandler;
import com.performance.platform.agent.restart.StatefulResourceCleaner;
import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.agent.runtime.TaskExecutionPipeline;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;

import java.time.Instant;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent unique pour le mode LOCAL.
 * <p>
 * Déclare toutes les spécialisations disponibles ({@code supportedTaskNames}
 * dérivées de tous les {@link TaskExecutor} enregistrés) et utilise le
 * transport in-memory ({@link InMemoryExecutionTransport}).
 * <p>
 * Cycle de vie : OFFLINE → IDLE → EXECUTING → (DRAINING) → OFFLINE.
 * Pas d'enregistrement distant (même JVM que l'orchestrateur), pas de heartbeat.
 * <p>
 * Le filtre retourne toujours {@code Responsible} car l'agent LOCAL supporte
 * toutes les tâches disponibles.
 *
 * <h3>Flux de réception d'une tâche</h3>
 * <ol>
 *   <li>Réception broadcast via {@link InMemoryExecutionTransport#receiveTask}</li>
 *   <li>Filtrage via {@link TaskSpecializationFilter} — toujours {@code Responsible}</li>
 *   <li>Vérification d'idempotence (MessageId déjà traité ?)</li>
 *   <li>Publication {@code TASK_CLAIMED} via {@link TaskExecutionPipeline#publishClaimEvent}</li>
 *   <li>Exécution sur Virtual Thread via {@link TaskExecutionPipeline#execute}</li>
 * </ol>
 *
 * <h3>ScenarioRestart</h3>
 * Délégué à {@link ScenarioRestartHandler}.
 *
 * <h3>Thread safety</h3>
 * Tous les champs mutables utilisent des structures concurrentes :
 * {@link ConcurrentHashMap}, {@link AtomicReference}, {@link AtomicInteger}.
 * L'exécution des tâches utilise {@code Thread.ofVirtual()}.
 */
public class LocalAgent implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(LocalAgent.class);

    // === Dépendances ===

    private final InMemoryExecutionTransport transport;
    private final TaskSpecializationFilter filter;
    private final AgentDescriptor staticDescriptor;
    private final Duration taskExecutionTimeout;

    // === Collaborateurs ===

    private final TaskExecutionPipeline taskPipeline;
    private final ScenarioRestartHandler restartHandler;

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

    // === Verrou pour start/stop ===

    private final Object lifecycleLock = new Object();
    private volatile boolean started = false;
    private volatile boolean stopped = false;

    /**
     * Construit un agent local pour le mode LOCAL.
     *
     * @param transport            le transport in-memory
     * @param descriptor           le descripteur statique de l'agent (id, nom, capacités, etc.)
     *                             Les {@code supportedTaskNames} doivent inclure tous les noms supportés
     * @param taskExecutionTimeout le timeout d'exécution d'une tâche
     * @param taskExecutors        la liste des {@link TaskExecutor} disponibles
     * @param cleaners             la liste des {@link StatefulResourceCleaner} (peut être vide)
     */
    public LocalAgent(
            InMemoryExecutionTransport transport,
            AgentDescriptor descriptor,
            Duration taskExecutionTimeout,
            List<TaskExecutor> taskExecutors,
            List<StatefulResourceCleaner> cleaners
    ) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.staticDescriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.taskExecutionTimeout = Objects.requireNonNull(taskExecutionTimeout, "taskExecutionTimeout must not be null");
        if (taskExecutionTimeout.isNegative() || taskExecutionTimeout.isZero()) {
            throw new IllegalArgumentException("taskExecutionTimeout must be positive, got " + taskExecutionTimeout);
        }
        Objects.requireNonNull(taskExecutors, "taskExecutors must not be null");

        var executorMap = taskExecutors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TaskExecutor::getSupportedTaskName,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalArgumentException(
                                    "Duplicate TaskExecutor for taskName=" + a.getSupportedTaskName());
                        }
                ));

        this.filter = new DefaultTaskSpecializationFilter(
                Set.copyOf(descriptor.supportedTaskNames()), descriptor.id());

        this.taskPipeline = new TaskExecutionPipeline(
                transport, executorMap, descriptor.id(),
                progressScheduler, taskExecutionTimeout,
                activeTasks, activeTaskCount, currentState
        );

        this.restartHandler = new ScenarioRestartHandler(
                Objects.requireNonNull(cleaners, "cleaners must not be null"));
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

        log.info("action=agent_starting agentId={} name={} supportedTasks={} mode=LOCAL",
                staticDescriptor.id().value(), staticDescriptor.name(),
                staticDescriptor.supportedTaskNames());

        try {
            // 1. Connexion au transport in-memory
            transport.connect();
            log.info("action=transport_connected agentId={} transport=IN_MEMORY",
                    staticDescriptor.id().value());

            // 2. Abonnement aux tâches et signaux
            transport.receiveTask(this::onTaskReceived);
            transport.receiveSignal(this::onSignalReceived);

            // 3. Nettoyage périodique des MessageId expirés
            long cleanupIntervalSeconds = Math.max(60, taskExecutionTimeout.toSeconds() * 2);
            cleanupFuture = cleanupScheduler.scheduleAtFixedRate(
                    this::cleanupExpiredMessageIds,
                    cleanupIntervalSeconds,
                    cleanupIntervalSeconds,
                    TimeUnit.SECONDS
            );

            currentState.set(AgentState.IDLE);
            log.info("action=agent_started agentId={} state=IDLE mode=LOCAL",
                    staticDescriptor.id().value());

        } catch (TransportException e) {
            currentState.set(AgentState.OFFLINE);
            log.error("action=agent_start_failed agentId={} mode=LOCAL",
                    staticDescriptor.id().value(), e);
            throw new TransportException(
                    "Failed to start local agent: " + staticDescriptor.id().value(), e);
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

        // Drain des tâches actives avec timeout
        drainActiveTasks();

        // Arrêter le nettoyage périodique
        var cf = cleanupFuture;
        if (cf != null) {
            cf.cancel(false);
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
                Instant.now(),
                staticDescriptor.registrationTtl()
        );
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

        restartHandler.onSignal(signal, activeTasks, activeTaskCount, currentState,
                staticDescriptor.id());

        log.info("action=scenario_restart_complete agentId={} remainingTasks={}",
                staticDescriptor.id().value(), activeTaskCount.get());
    }

    // === Réception de tâche (broadcast) ===

    /**
     * Handler appelé par le transport in-memory à la réception d'une {@link TaskExecutionRequest}.
     * <p>
     * En mode LOCAL, le filtre retourne toujours {@code Responsible} puisque toutes les
     * spécialisations sont déclarées.
     */
    private void onTaskReceived(TaskExecutionRequest request) {
        if (currentState.get() == AgentState.OFFLINE || currentState.get() == AgentState.DRAINING) {
            log.debug("action=task_ignored agentId={} messageId={} state={}",
                    staticDescriptor.id().value(), request.id().value(), currentState.get());
            return;
        }

        // 1. Filtrage (toujours Responsible en mode LOCAL)
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
        taskPipeline.publishClaimEvent(request);

        // 4. Transition d'état
        currentState.compareAndSet(AgentState.IDLE, AgentState.EXECUTING);
        activeTaskCount.incrementAndGet();

        // 5. Exécution asynchrone sur Virtual Thread
        var future = taskExecutorService.submit(() -> taskPipeline.execute(request));
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

    // === Drain des tâches actives ===

    /**
     * Attend la fin des tâches actives (avec timeout), puis annule les restantes.
     */
    private void drainActiveTasks() {
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
            for (var entry : activeTasks.entrySet()) {
                entry.getValue().cancel(true);
            }
            activeTasks.clear();
            activeTaskCount.set(0);
        }
    }

    // === Nettoyage des MessageId expirés ===

    /**
     * Nettoie périodiquement le set des MessageId déjà traités.
     */
    private void cleanupExpiredMessageIds() {
        int beforeSize = processedMessageIds.size();
        if (beforeSize > 10_000) {
            processedMessageIds.clear();
            log.info("action=cleanup_message_ids agentId={} before={} after=0",
                    staticDescriptor.id().value(), beforeSize);
        }
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
        return taskPipeline.executorCount();
    }

    int cleanerCount() {
        return restartHandler.cleanerCount();
    }
}
