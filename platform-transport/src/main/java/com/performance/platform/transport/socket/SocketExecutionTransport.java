package com.performance.platform.transport.socket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.AgentSignalHandler;
import com.performance.platform.transport.ExecutionEventHandler;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.Subscription;
import com.performance.platform.transport.TaskRequestHandler;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.TransportType;
import com.performance.platform.transport.config.SocketTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation Socket TCP de {@link ExecutionTransport}.
 * <p>
 * <strong>Modele de communication :</strong> connexions TCP persistantes entre
 * l'orchestrateur et les agents. L'orchestrateur accepte les connexions sur
 * {@code orchestratorPort}, les agents se connectent a
 * {@code orchestratorHost:orchestratorPort}. Le broadcast se fait en ecrivant
 * sur toutes les connexions actives.
 * <p>
 * <strong>Detection automatique du role :</strong> {@code connect()} tente
 * de binder un {@link ServerSocket}. Si le bind reussit, l'instance agit
 * en orchestrateur (accepte les connexions). Si le port est deja utilise,
 * l'instance agit en agent (se connecte a l'orchestrateur).
 * <p>
 * <strong>Protocole :</strong> JSON delimite par newline. Chaque message
 * contient un champ {@code "@type"} discriminant ({@code TASK}, {@code SIGNAL},
 * {@code EVENT}, {@code AGENT_EVENT}) et le payload associe.
 * <p>
 * <strong>Best-effort</strong> : pas de garantie at-least-once. L'idempotence
 * est assuree cote agent via {@code MessageId}.
 * <p>
 * Tous les I/O bloquants (accept, read, write) sont executes sur des
 * Virtual Threads.
 * <p>
 * <strong>CC-02:</strong> Cette classe implemente le contrat complet
 * {@link ExecutionTransport} (13 methodes) avec gestion dual-mode
 * (orchestrateur + agent), accept loop, read loop, reconnect, et protocole
 * de serialisation. Extraire des sous-composants creerait une indirection
 * artificielle a travers l'etat partage (handlers, connections, flags de
 * connexion) sans reduire la complexite reelle.
 *
 * @see SocketConnectionRegistry
 */
public class SocketExecutionTransport implements ExecutionTransport {

    private static final Logger log = LoggerFactory.getLogger(SocketExecutionTransport.class);

    static final String TYPE_FIELD = "@type";
    static final String TYPE_TASK = "TASK";
    static final String TYPE_SIGNAL = "SIGNAL";
    static final String TYPE_EVENT = "EVENT";
    static final String TYPE_AGENT_EVENT = "AGENT_EVENT";

    private final SocketTransportProperties props;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean orchestratorMode = new AtomicBoolean(false);

    // Orchestrator-side
    private ServerSocket serverSocket;
    private final SocketConnectionRegistry connectionRegistry = new SocketConnectionRegistry();
    private final AtomicBoolean acceptRunning = new AtomicBoolean(false);
    private final ExecutorService acceptExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    // Agent-side
    private Socket agentSocket;
    private BufferedWriter agentWriter;
    private final AtomicBoolean readRunning = new AtomicBoolean(false);
    private final AtomicBoolean reconnectActive = new AtomicBoolean(false);
    private final ExecutorService readExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    // Handler registries (thread-safe)
    private final List<TaskRequestHandler> taskHandlers = new CopyOnWriteArrayList<>();
    private final List<AgentSignalHandler> signalHandlers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<SocketSubscription, ExecutionEventHandler> subscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<SocketSubscription, AgentLifecycleEventHandler> agentSubscriptions =
            new ConcurrentHashMap<>();

    /**
     * @param props proprietes de configuration du transport socket
     */
    public SocketExecutionTransport(SocketTransportProperties props) {
        this.props = props;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    // === ExecutionTransport ===

    /**
     * Etablit la connexion. Tente de binder un {@link ServerSocket} sur
     * {@code orchestratorPort}. Si le bind reussit, agit en orchestrateur.
     * Sinon, agit en agent et se connecte a l'orchestrateur.
     */
    @Override
    public void connect() throws TransportException {
        if (connected.get()) {
            return;
        }

        try {
            serverSocket = new ServerSocket(props.orchestratorPort(), props.backlog());
            orchestratorMode.set(true);
            connected.set(true);
            startAcceptLoop();
            log.info("action=connect transport=SOCKET mode=ORCHESTRATOR port={}",
                    props.orchestratorPort());
        } catch (BindException e) {
            // Port deja utilise → mode agent
            connectAsAgent();
        } catch (IOException e) {
            // Autre erreur → tenter le mode agent
            log.warn("action=bind_failed port={} error={} — falling back to agent mode",
                    props.orchestratorPort(), e.getMessage());
            connectAsAgent();
        }
    }

    private void connectAsAgent() throws TransportException {
        try {
            agentSocket = new Socket(props.orchestratorHost(), props.orchestratorPort());
            agentSocket.setKeepAlive(props.keepAlive());
            agentWriter = new BufferedWriter(
                    new OutputStreamWriter(agentSocket.getOutputStream()));
            orchestratorMode.set(false);
            connected.set(true);
            startReadLoop(agentSocket);
            log.info("action=connect transport=SOCKET mode=AGENT host={} port={}",
                    props.orchestratorHost(), props.orchestratorPort());
        } catch (IOException e) {
            throw new TransportException(
                    "Failed to connect to orchestrator at " +
                    props.orchestratorHost() + ":" + props.orchestratorPort() +
                    ": " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        log.info("action=disconnect transport=SOCKET mode={}",
                orchestratorMode.get() ? "ORCHESTRATOR" : "AGENT");

        stopAcceptLoop();
        stopReadLoop();
        reconnectActive.set(false);

        if (orchestratorMode.get()) {
            connectionRegistry.closeAll();
            closeServerSocket();
        } else {
            closeAgentSocket();
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public TransportType getType() {
        return TransportType.SOCKET;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cote orchestrateur : serialise la requete et la diffuse sur toutes
     * les connexions actives. Cote agent : no-op (les tasks sont toujours
     * envoyees par l'orchestrateur).
     */
    @Override
    public void dispatchTask(TaskExecutionRequest request) {
        if (request == null) {
            throw new TransportException("request must not be null");
        }
        ensureConnected();
        if (!orchestratorMode.get()) {
            log.debug("action=dispatch_task_ignored — agent mode does not dispatch tasks");
            return;
        }

        log.info("action=dispatch_task executionId={} taskName={} activeConnections={}",
                request.executionId().value(), request.step().taskName(),
                connectionRegistry.getActiveCount());

        if (connectionRegistry.getActiveCount() == 0) {
            log.warn("action=no_active_connections executionId={} taskName={}",
                    request.executionId().value(), request.step().taskName());
            return;
        }

        String message;
        try {
            message = serializeMessage(TYPE_TASK, objectMapper.valueToTree(request));
        } catch (JsonProcessingException e) {
            throw new TransportException("Failed to serialize task request: " + e.getMessage(), e);
        }

        connectionRegistry.broadcast(message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cote orchestrateur : serialise le signal et le diffuse sur toutes
     * les connexions actives. Cote agent : no-op.
     */
    @Override
    public void broadcastSignal(AgentSignal signal) {
        if (signal == null) {
            throw new TransportException("signal must not be null");
        }
        ensureConnected();
        if (!orchestratorMode.get()) {
            log.debug("action=broadcast_signal_ignored — agent mode does not broadcast signals");
            return;
        }

        log.info("action=broadcast_signal signalId={} signalType={} activeConnections={}",
                signal.id().value(), signal.getClass().getSimpleName(),
                connectionRegistry.getActiveCount());

        if (connectionRegistry.getActiveCount() == 0) {
            log.warn("action=no_active_connections_for_signal signalId={}", signal.id().value());
            return;
        }

        String message;
        try {
            message = serializeMessage(TYPE_SIGNAL, objectMapper.valueToTree(signal));
        } catch (JsonProcessingException e) {
            throw new TransportException("Failed to serialize signal: " + e.getMessage(), e);
        }

        connectionRegistry.broadcast(message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cote orchestrateur : notifie les souscripteurs locaux.
     * Cote agent : envoie l'evenement a l'orchestrateur via le socket.
     */
    @Override
    public void publishEvent(ExecutionEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();

        if (orchestratorMode.get()) {
            // Orchestrateur : dispatch aux souscripteurs locaux
            log.debug("action=publish_event executionId={} eventId={} agentId={} type={} subscriberCount={}",
                    event.executionId().value(), event.id().value(),
                    event.agentId() != null ? event.agentId().value() : "null",
                    event.eventType(), subscriptions.size());
            for (var entry : subscriptions.entrySet()) {
                if (entry.getKey().isActive()) {
                    entry.getValue().onEvent(event);
                }
            }
        } else {
            // Agent : envoie a l'orchestrateur via le socket
            sendToOrchestrator(TYPE_EVENT, event, "event",
                    event.id().value(), event.executionId().value());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cote orchestrateur : notifie les souscripteurs locaux.
     * Cote agent : envoie l'evenement a l'orchestrateur via le socket.
     */
    @Override
    public void publishAgentEvent(AgentLifecycleEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();

        if (orchestratorMode.get()) {
            log.debug("action=publish_agent_event eventId={} agentId={} type={} subscriberCount={}",
                    event.id().value(), event.agentId().value(),
                    event.eventType(), agentSubscriptions.size());
            for (var entry : agentSubscriptions.entrySet()) {
                if (entry.getKey().isActive()) {
                    entry.getValue().onEvent(event);
                }
            }
        } else {
            sendToOrchestrator(TYPE_AGENT_EVENT, event, "agentEvent",
                    event.id().value(), "n/a");
        }
    }

    @Override
    public Subscription subscribe(ExecutionEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new SocketSubscription(subscriptions);
        subscriptions.put(sub, handler);
        return sub;
    }

    @Override
    public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new SocketSubscription(agentSubscriptions);
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

    // === Internal: Accept loop (orchestrator) ===

    /**
     * Demarre la boucle d'accept des connexions agent.
     * Chaque connexion acceptee est enregistree dans le
     * {@link SocketConnectionRegistry} et se voit attribuer
     * un thread de lecture dedie.
     */
    private void startAcceptLoop() {
        acceptRunning.set(true);
        acceptExecutor.submit(() -> {
            log.info("action=accept_loop_start port={}", props.orchestratorPort());
            while (acceptRunning.get() && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setKeepAlive(props.keepAlive());
                    var connection = connectionRegistry.register(clientSocket);
                    log.info("action=agent_connected remote={} total={}",
                            clientSocket.getRemoteSocketAddress(),
                            connectionRegistry.getActiveCount());
                    // Demarrer la lecture pour cette connexion
                    startConnectionRead(clientSocket, connection);
                } catch (SocketException e) {
                    if (acceptRunning.get()) {
                        log.warn("action=accept_error error={}", e.getMessage());
                    }
                } catch (IOException e) {
                    if (acceptRunning.get()) {
                        log.error("action=accept_error error={}", e.getMessage(), e);
                    }
                }
            }
            log.info("action=accept_loop_stop");
        });
    }

    private void stopAcceptLoop() {
        acceptRunning.set(false);
        closeServerSocket();
        acceptExecutor.shutdown();
        try {
            acceptExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.debug("action=close_server_socket_error error={}", e.getMessage());
            }
        }
    }

    // === Internal: Connection read (orchestrator) ===

    /**
     * Demarre un Virtual Thread pour lire les messages d'une connexion agent.
     * Les events recus sont deserialises et dispatches aux souscripteurs locaux.
     */
    private void startConnectionRead(Socket socket,
                                     SocketConnectionRegistry.ManagedConnection connection) {
        Thread.ofVirtual().name("socket-read-" + socket.getPort()).start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleIncomingMessage(line);
                }
            } catch (SocketException e) {
                log.debug("action=connection_read_closed remote={}",
                        socket.getRemoteSocketAddress());
            } catch (IOException e) {
                log.warn("action=connection_read_error remote={} error={}",
                        socket.getRemoteSocketAddress(), e.getMessage());
            } finally {
                connectionRegistry.unregister(connection);
                log.info("action=agent_disconnected remote={} total={}",
                        socket.getRemoteSocketAddress(),
                        connectionRegistry.getActiveCount());
            }
        });
    }

    // === Internal: Read loop (agent) ===

    /**
     * Demarre un Virtual Thread pour lire les messages de l'orchestrateur.
     * Les tasks et signaux recus sont deserialises et dispatches aux
     * handlers enregistres.
     */
    private void startReadLoop(Socket socket) {
        readRunning.set(true);
        readExecutor.submit(() -> {
            log.info("action=read_loop_start host={} port={}",
                    props.orchestratorHost(), props.orchestratorPort());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                String line;
                while (readRunning.get() && (line = reader.readLine()) != null) {
                    handleIncomingMessage(line);
                }
            } catch (SocketException e) {
                log.debug("action=read_loop_closed");
            } catch (IOException e) {
                log.warn("action=read_loop_error error={}", e.getMessage());
            } finally {
                if (readRunning.get() && connected.get()) {
                    attemptReconnect();
                }
            }
            log.info("action=read_loop_stop");
        });
    }

    private void stopReadLoop() {
        readRunning.set(false);
        closeAgentSocket();
        readExecutor.shutdown();
        try {
            readExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeAgentSocket() {
        if (agentWriter != null) {
            try {
                agentWriter.close();
            } catch (IOException e) {
                log.debug("action=close_agent_writer_error error={}", e.getMessage());
            }
        }
        if (agentSocket != null && !agentSocket.isClosed()) {
            try {
                agentSocket.close();
            } catch (IOException e) {
                log.debug("action=close_agent_socket_error error={}", e.getMessage());
            }
        }
    }

    // === Internal: Reconnect (agent) ===

    /**
     * Tente de se reconnecter a l'orchestrateur apres une perte de connexion.
     * Reessaie toutes les {@code reconnectIntervalMs} millisecondes.
     */
    private void attemptReconnect() {
        if (!reconnectActive.compareAndSet(false, true)) {
            return; // Deja en cours de reconnexion
        }
        Thread.ofVirtual().name("socket-reconnect").start(() -> {
            try {
                int interval = props.reconnectIntervalMs();
                log.info("action=reconnect_attempt intervalMs={}", interval);
                while (connected.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(interval);
                        agentSocket = new Socket(
                                props.orchestratorHost(), props.orchestratorPort());
                        agentSocket.setKeepAlive(props.keepAlive());
                        agentWriter = new BufferedWriter(
                                new OutputStreamWriter(agentSocket.getOutputStream()));
                        startReadLoop(agentSocket);
                        log.info("action=reconnect_success host={} port={}",
                                props.orchestratorHost(), props.orchestratorPort());
                        return;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (IOException e) {
                        log.debug("action=reconnect_failed host={} port={} error={} — retrying in {}ms",
                                props.orchestratorHost(), props.orchestratorPort(),
                                e.getMessage(), interval);
                    }
                }
            } finally {
                reconnectActive.set(false);
            }
        });
    }

    // === Internal: Message handling ===

    /**
     * Traite un message entrant (ligne JSON). Determine le type via le
     * champ {@code "@type"} et deserialise le payload correspondant.
     */
    void handleIncomingMessage(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            JsonNode typeNode = root.get(TYPE_FIELD);
            if (typeNode == null) {
                log.warn("action=unknown_message_type — missing @type field");
                return;
            }

            String type = typeNode.asText();
            switch (type) {
                case TYPE_TASK -> handleTaskMessage(root);
                case TYPE_SIGNAL -> handleSignalMessage(root);
                case TYPE_EVENT -> handleEventMessage(root);
                case TYPE_AGENT_EVENT -> handleAgentEventMessage(root);
                default -> log.warn("action=unknown_message_type type={}", type);
            }
        } catch (JsonProcessingException e) {
            log.warn("action=deserialize_error error={}", e.getMessage());
        }
    }

    private void handleTaskMessage(JsonNode root) {
        try {
            JsonNode payload = root.get("request");
            if (payload == null) {
                log.warn("action=task_message_missing_payload");
                return;
            }
            TaskExecutionRequest request = objectMapper.treeToValue(
                    payload, TaskExecutionRequest.class);
            log.debug("action=task_received executionId={} taskName={}",
                    request.executionId().value(), request.step().taskName());
            for (TaskRequestHandler handler : taskHandlers) {
                handler.onRequest(request);
            }
        } catch (JsonProcessingException e) {
            log.warn("action=task_deserialize_error error={}", e.getMessage());
        }
    }

    private void handleSignalMessage(JsonNode root) {
        try {
            JsonNode payload = root.get("signal");
            if (payload == null) {
                log.warn("action=signal_message_missing_payload");
                return;
            }
            // AgentSignal is a sealed interface — currently the only
            // permitted subtype is ScenarioRestartSignal (matching
            // KafkaMessageCodec.decodeSignal approach).
            AgentSignal signal = objectMapper.treeToValue(
                    payload, ScenarioRestartSignal.class);
            log.debug("action=signal_received signalType={}",
                    signal.getClass().getSimpleName());
            for (AgentSignalHandler handler : signalHandlers) {
                handler.onSignal(signal);
            }
        } catch (JsonProcessingException e) {
            log.warn("action=signal_deserialize_error error={}", e.getMessage());
        }
    }

    private void handleEventMessage(JsonNode root) {
        try {
            JsonNode payload = root.get("event");
            if (payload == null) {
                log.warn("action=event_message_missing_payload");
                return;
            }
            ExecutionEvent event = objectMapper.treeToValue(
                    payload, ExecutionEvent.class);
            log.debug("action=event_received executionId={} eventId={} type={}",
                    event.executionId().value(), event.id().value(), event.eventType());
            for (var entry : subscriptions.entrySet()) {
                if (entry.getKey().isActive()) {
                    entry.getValue().onEvent(event);
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("action=event_deserialize_error error={}", e.getMessage());
        }
    }

    private void handleAgentEventMessage(JsonNode root) {
        try {
            JsonNode payload = root.get("event");
            if (payload == null) {
                log.warn("action=agent_event_message_missing_payload");
                return;
            }
            AgentLifecycleEvent event = objectMapper.treeToValue(
                    payload, AgentLifecycleEvent.class);
            log.debug("action=agent_event_received eventId={} agentId={} type={}",
                    event.id().value(), event.agentId().value(), event.eventType());
            for (var entry : agentSubscriptions.entrySet()) {
                if (entry.getKey().isActive()) {
                    entry.getValue().onEvent(event);
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("action=agent_event_deserialize_error error={}", e.getMessage());
        }
    }

    // === Internal: Serialization ===

    /**
     * Serialise un message avec le champ {@code @type} et le payload.
     *
     * @param type    valeur du champ {@code @type}
     * @param payload le payload JSON (arbre Jackson)
     * @return la ligne JSON complete
     */
    static String serializeMessage(String type, JsonNode payload)
            throws JsonProcessingException {
        // Construire manuellement pour eviter une classe wrapper
        return "{\"" + TYPE_FIELD + "\":\"" + type + "\",\""
                + payloadFieldName(type) + "\":" + payload + "}";
    }

    private static String payloadFieldName(String type) {
        return switch (type) {
            case TYPE_TASK -> "request";
            case TYPE_SIGNAL -> "signal";
            case TYPE_EVENT, TYPE_AGENT_EVENT -> "event";
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }

    /**
     * Envoie un message a l'orchestrateur (cote agent).
     */
    private void sendToOrchestrator(String type, Object payload,
                                     String logAction, String id, String executionId) {
        log.debug("action=send_{} id={} executionId={}", logAction, id, executionId);
        String message;
        try {
            message = serializeMessage(type, objectMapper.valueToTree(payload));
        } catch (JsonProcessingException e) {
            throw new TransportException(
                    "Failed to serialize " + logAction + ": " + e.getMessage(), e);
        }
        synchronized (agentWriter) {
            try {
                agentWriter.write(message);
                agentWriter.newLine();
                agentWriter.flush();
            } catch (IOException e) {
                throw new TransportException(
                        "Failed to send " + logAction + " to orchestrator: " + e.getMessage(), e);
            }
        }
    }

    // === Internal: Guards ===

    private void ensureConnected() {
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
    }

    // === SocketSubscription (inner class) ===

    /**
     * Souscription associee a ce transport Socket.
     * L'annulation retire le handler du registre des souscriptions.
     */
    static final class SocketSubscription implements Subscription {

        private final ConcurrentMap<SocketSubscription, ?> registry;
        private final AtomicBoolean active = new AtomicBoolean(true);

        SocketSubscription(ConcurrentMap<SocketSubscription, ?> registry) {
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
