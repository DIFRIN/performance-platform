package com.performance.platform.transport.inmemory;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.transport.*;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implémentation en mémoire de {@link ExecutionTransport} pour le mode LOCAL et les tests.
 * <p>
 * Livraison synchrone par défaut : {@code dispatchTask} invoque immédiatement les handlers
 * enregistrés, {@code publishEvent} notifie les souscripteurs abonnés.
 * <p>
 * Sélectionnée par {@code TransportConfiguration} qui expose un {@code @Bean} conditionnel
 * sur {@code transport.type=IN_MEMORY}.
 * <p>
 * Thread-safe : utilise {@link CopyOnWriteArrayList} pour les listes de handlers et
 * {@link ConcurrentHashMap} pour le registre des souscriptions.
 */
public class InMemoryExecutionTransport implements ExecutionTransport {

    private static final Logger log = LoggerFactory.getLogger(InMemoryExecutionTransport.class);

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final List<TaskRequestHandler> taskHandlers = new CopyOnWriteArrayList<>();
    private final List<AgentSignalHandler> signalHandlers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<InMemorySubscription, ExecutionEventHandler> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<InMemorySubscription, AgentLifecycleEventHandler> agentSubscriptions = new ConcurrentHashMap<>();

    // === ExecutionTransport ===

    @Override
    public void dispatchTask(TaskExecutionRequest request) {
        if (request == null) {
            throw new TransportException("request must not be null");
        }
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
        log.debug("action=dispatch_task executionId={} messageId={} taskName={} handlerCount={}",
                request.executionId().value(), request.id().value(),
                request.step().taskName(), taskHandlers.size());
        for (TaskRequestHandler handler : taskHandlers) {
            handler.onRequest(request);
        }
    }

    @Override
    public void broadcastSignal(AgentSignal signal) {
        if (signal == null) {
            throw new TransportException("signal must not be null");
        }
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
        for (AgentSignalHandler handler : signalHandlers) {
            handler.onSignal(signal);
        }
    }

    @Override
    public void publishEvent(ExecutionEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
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
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
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
    public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var subscription = new InMemorySubscription(agentSubscriptions);
        agentSubscriptions.put(subscription, handler);
        return subscription;
    }

    @Override
    public Subscription subscribe(ExecutionEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var subscription = new InMemorySubscription(subscriptions);
        subscriptions.put(subscription, handler);
        return subscription;
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

    @Override
    public void connect() throws TransportException {
        connected.set(true);
        log.info("action=connect transport=IN_MEMORY");
    }

    @Override
    public void disconnect() {
        connected.set(false);
        log.info("action=disconnect transport=IN_MEMORY");
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public TransportType getType() {
        return TransportType.IN_MEMORY;
    }

    // === Package-private pour les tests ===

    /**
     * Retourne le nombre de handlers de task enregistrés (pour vérification en test).
     */
    int taskHandlerCount() {
        return taskHandlers.size();
    }

    /**
     * Retourne le nombre de handlers de signal enregistrés (pour vérification en test).
     */
    int signalHandlerCount() {
        return signalHandlers.size();
    }

    /**
     * Retourne le nombre de souscriptions actives (pour vérification en test).
     */
    int activeSubscriptionCount() {
        return (int) subscriptions.keySet().stream().filter(InMemorySubscription::isActive).count();
    }

    // === InMemorySubscription (inner class) ===

    /**
     * Souscription en mémoire associée à ce transport.
     * L'annulation retire le handler du registre des souscriptions.
     */
    private static final class InMemorySubscription implements Subscription {

        private final ConcurrentMap<InMemorySubscription, ?> registry;
        private final AtomicBoolean active = new AtomicBoolean(true);

        InMemorySubscription(ConcurrentMap<InMemorySubscription, ?> registry) {
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
