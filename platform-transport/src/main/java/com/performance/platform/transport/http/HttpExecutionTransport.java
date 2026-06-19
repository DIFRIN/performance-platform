package com.performance.platform.transport.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.AgentSignalHandler;
import com.performance.platform.transport.ExecutionEventHandler;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.Subscription;
import com.performance.platform.transport.TaskRequestHandler;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.TransportType;
import com.performance.platform.transport.config.HttpTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation HTTP de {@link ExecutionTransport}.
 * <p>
 * <strong>Modele de communication :</strong> l'orchestrateur POST les
 * {@link TaskExecutionRequest} aux agents capables via leurs endpoints
 * {@code httpCallbackUrl}. Les agents repondent 202 Accepted, puis
 * publient leurs events en POST sur le {@code callbackBasePath} de
 * l'orchestrateur.
 * <p>
 * <strong>Broadcast mode :</strong>
 * <ul>
 *   <li>{@code ALL_CAPABLE} — POST a tous les agents capables
 *       (multi-claim). Defaut.</li>
 *   <li>{@code FIRST_AVAILABLE} — POST au premier agent capable
 *       seulement.</li>
 * </ul>
 * <p>
 * Tous les appels HTTP bloquants (POST vers les agents) sont executes
 * sur des Virtual Threads via l'{@code Executor} du {@link HttpClient}
 * JDK.
 * <p>
 * <strong>Cote orchestrateur :</strong> {@code dispatchTask} et
 * {@code broadcastSignal} POSTent vers les agents. Les events
 * remontent des agents via {@link HttpEventCallbackController} et
 * sont dispatches aux souscripteurs locaux.
 * <p>
 * Selectionnee par le bean conditionnel dans
 * {@link com.performance.platform.transport.config.TransportConfiguration}.
 *
 * <p><b>CC-02</b>: class ~352 lines — implements the full
 * {@link ExecutionTransport} contract (13 methods) spanning 3
 * communication patterns (task dispatch, signal broadcast, event pub/sub).
 * Splitting would create artificial indirection across the shared state
 * (handlers, subscriptions, connected flag) without reducing complexity.</p>
 */
public class HttpExecutionTransport implements ExecutionTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutionTransport.class);

    static final String BROADCAST_ALL_CAPABLE = "ALL_CAPABLE";
    static final String BROADCAST_FIRST_AVAILABLE = "FIRST_AVAILABLE";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final HttpTransportProperties props;
    private final AgentRegistryPort registry;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // Handler registries (thread-safe)
    private final List<TaskRequestHandler> taskHandlers = new CopyOnWriteArrayList<>();
    private final List<AgentSignalHandler> signalHandlers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<HttpSubscription, ExecutionEventHandler> subscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<HttpSubscription, AgentLifecycleEventHandler> agentSubscriptions =
            new ConcurrentHashMap<>();

    /**
     * @param props    proprietes de configuration HTTP
     * @param registry registre des agents; nullable dans les tests sans registre
     */
    public HttpExecutionTransport(HttpTransportProperties props, AgentRegistryPort registry) {
        this.props = props;
        this.registry = registry;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        int timeout = props.requestTimeoutSeconds() > 0
                ? props.requestTimeoutSeconds() : 30;
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    // === ExecutionTransport ===

    @Override
    public void connect() throws TransportException {
        if (connected.get()) {
            return;
        }
        connected.set(true);
        log.info("action=connect transport=HTTP broadcastMode={}", props.broadcastMode());
    }

    @Override
    public void disconnect() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        log.info("action=disconnect transport=HTTP");
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public TransportType getType() {
        return TransportType.HTTP;
    }

    /**
     * Dispatche une tache vers les agents capables.
     *
     * <p><b>CC-02</b>: method ~45 lines — single orchestration flow (validate,
     * query registry, select targets, serialize, POST parallel, join).
     * Extracting sub-steps would scatter the linear null-guard/error-handling
     * and require passing intermediate state across methods.</p>
     */
    @Override
    public void dispatchTask(TaskExecutionRequest request) {
        if (request == null) {
            throw new TransportException("request must not be null");
        }
        ensureConnected();
        ensureRegistry();
        String taskName = request.step().taskName();
        log.info("action=dispatch_task executionId={} taskName={}",
                request.executionId().value(), taskName);

        List<AgentDescriptor> capableAgents = registry.findByTaskName(taskName);
        List<AgentDescriptor> targets = selectTargets(capableAgents,
                request.executionId().value());

        if (targets.isEmpty()) {
            log.warn("action=no_agent_available executionId={} taskName={} agentCount={}",
                    request.executionId().value(), taskName, capableAgents.size());
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new TransportException("Failed to serialize task request: " + e.getMessage(), e);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AgentDescriptor agent : targets) {
            CompletableFuture<Void> future = postAsync(
                    agent.httpCallbackUrl(), json,
                    request.executionId().value(), taskName, agent.id().value());
            futures.add(future);
        }

        // Wait for all POSTs to complete (Virtual Threads — blocking is fine)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            throw new TransportException("Failed to dispatch task to agents: " + e.getMessage(), e);
        }

        log.info("action=dispatch_complete executionId={} taskName={} agentCount={}",
                request.executionId().value(), taskName, targets.size());
    }

    /**
     * Diffuse un signal a tous les agents enregistres avec callback URL.
     *
     * <p><b>CC-02</b>: method ~44 lines — single broadcast flow (validate,
     * query all agents, filter by callback URL, serialize, POST parallel,
     * join). Same cohesive pattern as {@link #dispatchTask}.</p>
     */
    @Override
    public void broadcastSignal(AgentSignal signal) {
        if (signal == null) {
            throw new TransportException("signal must not be null");
        }
        ensureConnected();
        ensureRegistry();
        log.info("action=broadcast_signal signalId={} signalType={}",
                signal.id().value(), signal.getClass().getSimpleName());

        List<AgentDescriptor> allAgents = registry.findAll();
        List<AgentDescriptor> targets = allAgents.stream()
                .filter(a -> a.httpCallbackUrl() != null && !a.httpCallbackUrl().isBlank())
                .toList();

        if (targets.isEmpty()) {
            log.warn("action=no_agent_for_signal signalId={}", signal.id().value());
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(signal);
        } catch (JsonProcessingException e) {
            throw new TransportException("Failed to serialize signal: " + e.getMessage(), e);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AgentDescriptor agent : targets) {
            String signalUrl = agent.httpCallbackUrl() + "/signals";
            CompletableFuture<Void> future = postAsync(
                    signalUrl, json,
                    null, signal.getClass().getSimpleName(), agent.id().value());
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            throw new TransportException("Failed to broadcast signal: " + e.getMessage(), e);
        }

        log.info("action=broadcast_signal_complete signalId={} agentCount={}",
                signal.id().value(), targets.size());
    }

    @Override
    public void publishEvent(ExecutionEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();
        log.debug("action=publish_event executionId={} eventId={} agentId={} type={} subscriberCount={}",
                event.executionId().value(), event.id().value(),
                event.agentId() != null ? event.agentId().value() : "null",
                event.eventType(), subscriptions.size());
        for (var entry : subscriptions.entrySet()) {
            if (entry.getKey().isActive()) {
                entry.getValue().onEvent(event);
            }
        }
    }

    @Override
    public void publishAgentEvent(AgentLifecycleEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();
        log.debug("action=publish_agent_event eventId={} agentId={} type={} subscriberCount={}",
                event.id().value(), event.agentId().value(),
                event.eventType(), agentSubscriptions.size());
        for (var entry : agentSubscriptions.entrySet()) {
            if (entry.getKey().isActive()) {
                entry.getValue().onEvent(event);
            }
        }
    }

    @Override
    public Subscription subscribe(ExecutionEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new HttpSubscription(subscriptions);
        subscriptions.put(sub, handler);
        return sub;
    }

    @Override
    public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new HttpSubscription(agentSubscriptions);
        agentSubscriptions.put(sub, handler);
        return sub;
    }

    @Override
    public void receiveTask(TaskRequestHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        taskHandlers.add(handler);
    }

    @Override
    public void receiveSignal(AgentSignalHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        signalHandlers.add(handler);
    }

    // === Internal ===

    /**
     * Selectionne les agents cibles selon le mode de broadcast.
     * Filtre les agents sans {@code httpCallbackUrl}.
     */
    private List<AgentDescriptor> selectTargets(List<AgentDescriptor> capableAgents,
                                                 String executionId) {
        List<AgentDescriptor> withUrl = capableAgents.stream()
                .filter(a -> a.httpCallbackUrl() != null && !a.httpCallbackUrl().isBlank())
                .toList();

        String mode = props.broadcastMode();
        if (withUrl.isEmpty()) {
            return List.of();
        }

        if (BROADCAST_FIRST_AVAILABLE.equals(mode)) {
            AgentDescriptor first = withUrl.getFirst();
            log.debug("action=target_selection executionId={} mode=FIRST_AVAILABLE agentId={}",
                    executionId, first.id().value());
            return List.of(first);
        }

        // ALL_CAPABLE (default) — send to all
        log.debug("action=target_selection executionId={} mode=ALL_CAPABLE agentCount={}",
                executionId, withUrl.size());
        return withUrl;
    }

    /**
     * POST les donnees JSON de maniere asynchrone vers l'URL de callback de l'agent.
     * Les futures sont completees sur le pool Virtual Threads du {@link HttpClient}.
     */
    private CompletableFuture<Void> postAsync(String url, String json,
                                               String executionId, String taskName,
                                               String agentId) {
        return httpClient.sendAsync(buildPostRequest(url, json), HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    int status = response.statusCode();
                    if (status == 202) {
                        log.debug("action=post_accepted executionId={} taskName={} agentId={} url={} status={}",
                                executionId, taskName, agentId, url, status);
                    } else {
                        log.warn("action=post_unexpected_status executionId={} taskName={} agentId={} url={} status={}",
                                executionId, taskName, agentId, url, status);
                    }
                })
                .exceptionally(ex -> {
                    log.error("action=post_failed executionId={} taskName={} agentId={} url={} error={}",
                            executionId, taskName, agentId, url, ex.getMessage(), ex);
                    return null; // ne pas propager — les autres POST continuent
                });
    }

    private HttpRequest buildPostRequest(String url, String json) {
        int timeout = props.requestTimeoutSeconds() > 0
                ? props.requestTimeoutSeconds() : 30;
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private void ensureConnected() {
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
    }

    private void ensureRegistry() {
        if (registry == null) {
            throw new TransportException(
                    "AgentRegistryPort is not available; HTTP transport requires a registry");
        }
    }

    // === HttpSubscription (inner class) ===

    /**
     * Souscription associee a ce transport HTTP.
     * L'annulation retire le handler du registre des souscriptions.
     */
    static final class HttpSubscription implements Subscription {

        private final ConcurrentMap<HttpSubscription, ?> registry;
        private final AtomicBoolean active = new AtomicBoolean(true);

        HttpSubscription(ConcurrentMap<HttpSubscription, ?> registry) {
            this.registry = registry;
        }

        @Override
        public void cancel() {
            if (active.compareAndSet(true, false)) {
                registry.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }
}
