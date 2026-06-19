package com.performance.platform.transport.rabbitmq;

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
import com.performance.platform.transport.config.RabbitMQTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation RabbitMQ de {@link ExecutionTransport}.
 * <p>
 * <strong>Architecture broadcasting :</strong>
 * Trois exchanges FANOUT durables sont declares au connect :
 * <ul>
 *   <li>tasks — broadcast des {@link TaskExecutionRequest}</li>
 *   <li>events — broadcast des {@link ExecutionEvent} et {@link AgentLifecycleEvent}</li>
 *   <li>signals — broadcast des {@link AgentSignal}</li>
 * </ul>
 * Chaque agent cree une file exclusive auto-delete et la bind aux trois
 * exchanges. Le consumer unique utilise {@code basicConsume} avec ack manuel
 * (at-least-once). Le dispatching par type de message est effectue via le
 * marqueur {@code @type} dans le corps JSON.
 * <p>
 * Les I/O sont executes sur des Virtual Threads (configures au niveau de
 * la {@code ThreadFactory} de la {@code ConnectionFactory}).
 * <p>
 * Selectionnee par {@code TransportConfiguration} avec le bean conditionnel
 * {@code @ConditionalOnProperty(name = "transport.type", havingValue = "RABBITMQ")}.
 */
public class RabbitMQExecutionTransport implements ExecutionTransport {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQExecutionTransport.class);

    private final RabbitMQTransportProperties props;
    private final RabbitMQMessageCodec codec;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // Handler registries (thread-safe, shared with consumer manager)
    private final List<TaskRequestHandler> taskHandlers = new CopyOnWriteArrayList<>();
    private final List<AgentSignalHandler> signalHandlers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<RabbitMQSubscription, ExecutionEventHandler> subscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<RabbitMQSubscription, AgentLifecycleEventHandler> agentSubscriptions =
            new ConcurrentHashMap<>();

    // RabbitMQ infrastructure
    private Connection connection;
    private Channel producerChannel;
    private RabbitMQConsumerManager consumerManager;
    private String queueName;

    public RabbitMQExecutionTransport(RabbitMQTransportProperties props) {
        this.props = props;
        this.codec = new RabbitMQMessageCodec();
    }

    // === ExecutionTransport ===

    @Override
    public void connect() throws TransportException {
        if (connected.get()) {
            return;
        }
        try {
            var factory = buildConnectionFactory();
            this.connection = factory.newConnection();
            this.producerChannel = connection.createChannel();

            // Declare durable FANOUT exchanges
            producerChannel.exchangeDeclare(props.tasksExchange(), "fanout", true);
            producerChannel.exchangeDeclare(props.eventsExchange(), "fanout", true);
            producerChannel.exchangeDeclare(props.signalsExchange(), "fanout", true);

            // Create exclusive auto-delete queue (server-named)
            this.queueName = producerChannel.queueDeclare(
                    "", false, true, true, null).getQueue();

            // Bind queue to all 3 exchanges
            producerChannel.queueBind(queueName, props.tasksExchange(), "");
            producerChannel.queueBind(queueName, props.eventsExchange(), "");
            producerChannel.queueBind(queueName, props.signalsExchange(), "");

            // Start consumer manager (ack manuel, dispatching by @type)
            this.consumerManager = new RabbitMQConsumerManager(
                    connection, queueName, codec,
                    taskHandlers, signalHandlers, subscriptions, agentSubscriptions);
            consumerManager.start();

            connected.set(true);
            log.info("action=connect transport=RABBITMQ host={} port={} virtualHost={} queue={}",
                    props.host(), props.port(), props.virtualHost(), queueName);
        } catch (Exception e) {
            throw new TransportException(
                    "Failed to connect RabbitMQ transport: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        if (consumerManager != null) {
            consumerManager.stop();
        }
        closeQuietly(producerChannel);
        closeQuietly(connection);
        log.info("action=disconnect transport=RABBITMQ");
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public TransportType getType() {
        return TransportType.RABBITMQ;
    }

    @Override
    public void dispatchTask(TaskExecutionRequest request) {
        if (request == null) {
            throw new TransportException("request must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeTaskRequest(request);
        publish(props.tasksExchange(), data, "dispatch_task",
                request.executionId().value());
    }

    @Override
    public void publishEvent(ExecutionEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeExecutionEvent(event);
        publish(props.eventsExchange(), data, "publish_event",
                event.executionId().value());
    }

    @Override
    public void publishAgentEvent(AgentLifecycleEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeAgentLifecycleEvent(event);
        publish(props.eventsExchange(), data, "publish_agent_event", null);
    }

    @Override
    public void broadcastSignal(AgentSignal signal) {
        if (signal == null) {
            throw new TransportException("signal must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeSignal(signal);
        publish(props.signalsExchange(), data, "broadcast_signal", null);
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
    public Subscription subscribe(ExecutionEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new RabbitMQSubscription(subscriptions);
        subscriptions.put(sub, handler);
        return sub;
    }

    @Override
    public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new RabbitMQSubscription(agentSubscriptions);
        agentSubscriptions.put(sub, handler);
        return sub;
    }

    // === Internal ===

    private ConnectionFactory buildConnectionFactory() {
        var factory = new ConnectionFactory();
        factory.setHost(props.host());
        factory.setPort(props.port());
        factory.setUsername(props.username());
        factory.setPassword(props.password());
        if (props.virtualHost() != null && !props.virtualHost().isBlank()) {
            factory.setVirtualHost(props.virtualHost());
        }
        // I/O blocking operations execute on Virtual Threads
        factory.setThreadFactory(Thread.ofVirtual().factory());
        return factory;
    }

    private void publish(String exchange, byte[] data,
                         String action, String executionId) {
        try {
            producerChannel.basicPublish(exchange, "", null, data);
            log.debug("action={} executionId={} exchange={}",
                    action, executionId, exchange);
        } catch (Exception e) {
            throw new TransportException(
                    "Failed to publish to " + exchange + ": " + e.getMessage(), e);
        }
    }

    private void ensureConnected() {
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                /* ignore */
            }
        }
    }
}
