package com.performance.platform.agent.runtime;

import com.performance.platform.agent.filter.TaskFilterResult;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.registration.HeartbeatScheduler;
import com.performance.platform.agent.registration.RegistrationException;
import com.performance.platform.agent.restart.ScenarioRestartHandler;
import com.performance.platform.agent.runtime.lifecycle.DefaultLifecycleSignalHandler;
import com.performance.platform.application.ports.out.LifecycleSignalHandler;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * Runtime d'agent unique (PDR-050), independant du mode.
 * <p>
 * La difference LOCAL / DISTRIBUTED est portee par deux collaborations
 * optionnelles :</p>
 * <ul>
 *   <li>{@code registrationPort == null} → agent LOCAL : pas de REGISTERING,
 *       pas de heartbeat, pas de deregister ;</li>
 *   <li>{@code registrationPort != null} → agent DISTRIBUTED : enregistrement,
 *       heartbeat periodique et deregister.</li>
 * </ul>
 * <p>
 * Le transport, le filtre de specialisation et les executeurs sont injectes :
 * aucun {@code if/switch} sur {@code runtime.mode}.
 */
public class TaskAgentRuntime implements AgentRuntime, LifecycleSignalHandler {

    private static final Logger log = LoggerFactory.getLogger(TaskAgentRuntime.class);

    // === Dependances ===

    private final ExecutionTransport transport;
    private final TaskSpecializationFilter filter;
    private final AgentRegistrationPort registrationPort; // nullable
    private final HeartbeatScheduler heartbeatScheduler;  // nullable
    private final AgentDescriptor staticDescriptor;
    private final Duration taskExecutionTimeout;

    // === Collaborateurs ===

    private final TaskExecutionPipeline taskPipeline;
    private final ScenarioRestartHandler restartHandler;
    private final DefaultLifecycleSignalHandler lifecycleSignalHandler;

    // === Etat concurrent ===

    private final AtomicReference<AgentState> currentState =
            new AtomicReference<>(AgentState.OFFLINE);
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    private final Set<MessageId> processedMessageIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<MessageId, Future<?>> activeTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService progressScheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private volatile ScheduledFuture<?> cleanupFuture;

    private final ExecutorService taskExecutorService = Executors.newVirtualThreadPerTaskExecutor();

    private final Object lifecycleLock = new Object();
    private volatile boolean started = false;
    private volatile boolean stopped = false;

    public TaskAgentRuntime(
            ExecutionTransport transport,
            TaskSpecializationFilter filter,
            AgentDescriptor descriptor,
            Duration taskExecutionTimeout,
            List<TaskExecutor> taskExecutors,
            List<StatefulResourceCleaner> cleaners,
            AgentRegistrationPort registrationPort,
            Duration heartbeatInterval) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.filter = Objects.requireNonNull(filter, "filter must not be null");
        this.staticDescriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.taskExecutionTimeout = Objects.requireNonNull(taskExecutionTimeout,
                "taskExecutionTimeout must not be null");
        if (taskExecutionTimeout.isNegative() || taskExecutionTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "taskExecutionTimeout must be positive, got " + taskExecutionTimeout);
        }
        Objects.requireNonNull(taskExecutors, "taskExecutors must not be null");
        this.registrationPort = registrationPort;

        if (registrationPort != null) {
            Objects.requireNonNull(heartbeatInterval, "heartbeatInterval required when registration is enabled");
            var heartbeatSeconds = Math.toIntExact(heartbeatInterval.getSeconds());
            if (heartbeatSeconds < 1) {
                throw new IllegalArgumentException("heartbeatInterval must be >= 1 second");
            }
            this.heartbeatScheduler = new HeartbeatScheduler(
                    registrationPort, descriptor.id(), heartbeatSeconds,
                    this::getState, activeTaskCount::get);
        } else {
            this.heartbeatScheduler = null;
        }

        var executorMap = taskExecutors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TaskExecutor::getSupportedTaskName,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalArgumentException(
                                    "Duplicate TaskExecutor for taskName=" + a.getSupportedTaskName());
                        }
                ));

        this.taskPipeline = new TaskExecutionPipeline(
                transport, executorMap, descriptor.id(),
                progressScheduler, taskExecutionTimeout,
                activeTasks, activeTaskCount, currentState);

        this.restartHandler = new ScenarioRestartHandler(
                Objects.requireNonNull(cleaners, "cleaners must not be null"));

        this.lifecycleSignalHandler = new DefaultLifecycleSignalHandler(filter, executorMap);
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

        boolean distributed = registrationPort != null;
        log.info("action=agent_starting agentId={} name={} supportedTasks={} distributed={}",
                staticDescriptor.id().value(), staticDescriptor.name(),
                staticDescriptor.supportedTaskNames(), distributed);

        try {
            transport.connect();
            log.info("action=transport_connected agentId={}", staticDescriptor.id().value());

            if (distributed) {
                currentState.set(AgentState.REGISTERING);
                registrationPort.register(getDescriptor());
                heartbeatScheduler.start();
                log.info("action=agent_registered agentId={} name={}",
                        staticDescriptor.id().value(), staticDescriptor.name());
            }

            transport.receiveTask(this::onTaskReceived);
            transport.receiveSignal(this::onSignalReceived);

            long cleanupIntervalSeconds = Math.max(60, taskExecutionTimeout.toSeconds() * 2);
            cleanupFuture = cleanupScheduler.scheduleAtFixedRate(
                    this::cleanupExpiredMessageIds,
                    cleanupIntervalSeconds,
                    cleanupIntervalSeconds,
                    TimeUnit.SECONDS);

            currentState.set(AgentState.IDLE);
            log.info("action=agent_started agentId={} state=IDLE distributed={}",
                    staticDescriptor.id().value(), distributed);
        } catch (TransportException | RegistrationException e) {
            currentState.set(AgentState.OFFLINE);
            log.error("action=agent_start_failed agentId={} distributed={}",
                    staticDescriptor.id().value(), distributed, e);
            if (registrationPort == null && e instanceof TransportException transportException) {
                // Comportement LOCAL historique : TransportException explicite.
                throw new TransportException(
                        "Failed to start local agent: " + staticDescriptor.id().value(),
                        transportException);
            }
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

        drainActiveTasks();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.stop();
        }

        var cf = cleanupFuture;
        if (cf != null) {
            cf.cancel(false);
        }

        if (registrationPort != null) {
            try {
                registrationPort.deregister(staticDescriptor.id());
            } catch (RegistrationException e) {
                log.warn("action=deregister_failed agentId={} (continuing stop)",
                        staticDescriptor.id().value(), e);
            }
        }

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
        Duration ttl = heartbeatScheduler != null
                ? Duration.ofSeconds(heartbeatScheduler.registrationTtlSeconds())
                : staticDescriptor.registrationTtl();
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
                ttl);
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

    @Override
    public void onLifecycleSignal(ExecutionLifecycleSignal signal) {
        lifecycleSignalHandler.handle(signal);
    }

    // === Reception de tache (broadcast) ===

    private void onTaskReceived(TaskExecutionRequest request) {
        if (currentState.get() == AgentState.OFFLINE || currentState.get() == AgentState.DRAINING) {
            log.debug("action=task_ignored agentId={} messageId={} state={}",
                    staticDescriptor.id().value(), request.id().value(), currentState.get());
            return;
        }

        var filterResult = filter.filter(request);
        if (filterResult instanceof TaskFilterResult.NotResponsible) {
            log.debug("action=task_not_responsible agentId={} messageId={} taskName={}",
                    staticDescriptor.id().value(), request.id().value(),
                    request.step().taskName());
            return;
        }

        if (!processedMessageIds.add(request.id())) {
            log.info("action=task_duplicate agentId={} messageId={} taskName={}",
                    staticDescriptor.id().value(), request.id().value(),
                    request.step().taskName());
            return;
        }

        log.info("action=task_claimed agentId={} executionId={} messageId={} taskName={}",
                staticDescriptor.id().value(), request.executionId().value(),
                request.id().value(), request.step().taskName());

        taskPipeline.publishClaimEvent(request);

        currentState.compareAndSet(AgentState.IDLE, AgentState.EXECUTING);
        activeTaskCount.incrementAndGet();

        var future = taskExecutorService.submit(() -> taskPipeline.execute(request));
        activeTasks.put(request.id(), future);
    }

    private void onSignalReceived(AgentSignal signal) {
        log.info("action=signal_received agentId={} signalType={}",
                staticDescriptor.id().value(), signal.getClass().getSimpleName());

        if (signal instanceof ScenarioRestartSignal restartSignal) {
            onScenarioRestart(restartSignal);
        } else if (signal instanceof ExecutionLifecycleSignal lifecycleSignal) {
            onLifecycleSignal(lifecycleSignal);
        }
    }

    // === Drain / cleanup ===

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

    private void cleanupExpiredMessageIds() {
        int beforeSize = processedMessageIds.size();
        if (beforeSize > 10_000) {
            processedMessageIds.clear();
            log.info("action=cleanup_message_ids agentId={} before={} after=0",
                    staticDescriptor.id().value(), beforeSize);
        }
    }

    // === Accesseurs package-private pour les tests ===

    public int activeTaskCount() {
        return activeTaskCount.get();
    }

    public int processedMessageCount() {
        return processedMessageIds.size();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isStopped() {
        return stopped;
    }

    public int executorCount() {
        return taskPipeline.executorCount();
    }

    public int cleanerCount() {
        return restartHandler.cleanerCount();
    }
}
