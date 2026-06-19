package com.performance.platform.transport.kafka;

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
import com.performance.platform.transport.config.KafkaTransportProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation Kafka de {@link ExecutionTransport}.
 * <p>
 * <strong>Producer side (orchestrateur) :</strong>
 * {@code dispatchTask} vers le topic tasks,
 * {@code publishEvent} et {@code publishAgentEvent} vers le topic events,
 * {@code broadcastSignal} vers le topic signals.
 * <p>
 * <strong>Consumer side (agent) :</strong>
 * Chaque agent utilise un consumer group unique (son {@code agentId},
 * ADR-009) pour recevoir tous les messages en broadcast.
 * Le filtrage se fait cote agent via {@code TaskSpecializationFilter}.
 * <p>
 * Les boucles de consommation sont deleguees a {@link KafkaConsumerManager}
 * qui les execute sur des Virtual Threads.
 * <p>
 * Selectionnee par {@code TransportConfiguration} avec le bean conditionnel
 * {@code @ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")}.
 */
public class KafkaExecutionTransport implements ExecutionTransport {

    private static final Logger log = LoggerFactory.getLogger(KafkaExecutionTransport.class);

    private final KafkaTransportProperties props;
    private final KafkaMessageCodec codec;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // Handler registries (thread-safe, shared with consumer manager)
    private final List<TaskRequestHandler> taskHandlers = new CopyOnWriteArrayList<>();
    private final List<AgentSignalHandler> signalHandlers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<KafkaSubscription, ExecutionEventHandler> subscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<KafkaSubscription, AgentLifecycleEventHandler> agentSubscriptions =
            new ConcurrentHashMap<>();

    // Kafka infrastructure
    private KafkaProducer<String, byte[]> producer;
    private KafkaConsumerManager consumerManager;

    public KafkaExecutionTransport(KafkaTransportProperties props) {
        this.props = props;
        this.codec = new KafkaMessageCodec();
    }

    // === ExecutionTransport ===

    @Override
    public void connect() throws TransportException {
        if (connected.get()) {
            return;
        }
        try {
            this.producer = createProducer();
            this.consumerManager = new KafkaConsumerManager(
                    props.bootstrapServers(), props.consumerGroup(),
                    props.tasksTopic(), props.eventsTopic(), props.signalsTopic(),
                    codec, taskHandlers, signalHandlers, subscriptions, agentSubscriptions);
            consumerManager.start();
            connected.set(true);
            log.info("action=connect transport=KAFKA bootstrap={} consumerGroup={}",
                    props.bootstrapServers(), props.consumerGroup());
        } catch (Exception e) {
            throw new TransportException("Failed to connect Kafka transport: " + e.getMessage(), e);
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
        closeQuietly(producer);
        log.info("action=disconnect transport=KAFKA");
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public TransportType getType() {
        return TransportType.KAFKA;
    }

    @Override
    public void dispatchTask(TaskExecutionRequest request) {
        if (request == null) {
            throw new TransportException("request must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeTaskRequest(request);
        sendRecord(props.tasksTopic(), request.id().value(), data, "dispatch_task",
                request.executionId().value());
    }

    @Override
    public void publishEvent(ExecutionEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeExecutionEvent(event);
        sendRecord(props.eventsTopic(), event.id().value(), data, "publish_event",
                event.executionId().value());
    }

    @Override
    public void publishAgentEvent(AgentLifecycleEvent event) {
        if (event == null) {
            throw new TransportException("event must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeAgentLifecycleEvent(event);
        sendRecord(props.eventsTopic(), event.id().value(), data, "publish_agent_event",
                null);
    }

    @Override
    public void broadcastSignal(AgentSignal signal) {
        if (signal == null) {
            throw new TransportException("signal must not be null");
        }
        ensureConnected();
        byte[] data = codec.encodeSignal(signal);
        sendRecord(props.signalsTopic(), signal.id().value(), data, "broadcast_signal",
                null);
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
        var sub = new KafkaSubscription(subscriptions);
        subscriptions.put(sub, handler);
        return sub;
    }

    @Override
    public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
        if (handler == null) {
            throw new TransportException("handler must not be null");
        }
        var sub = new KafkaSubscription(agentSubscriptions);
        agentSubscriptions.put(sub, handler);
        return sub;
    }

    // === Producer ===

    private void sendRecord(String topic, String key, byte[] data,
                            String action, String executionId) {
        var record = new ProducerRecord<>(topic, key, data);
        try {
            producer.send(record).get();
            log.debug("action={} executionId={} topic={} key={}",
                    action, executionId, topic, key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Interrupted while sending to " + topic, e);
        } catch (ExecutionException e) {
            throw new TransportException("Failed to send to " + topic + ": "
                    + e.getCause().getMessage(), e.getCause());
        }
    }

    private KafkaProducer<String, byte[]> createProducer() {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.ACKS_CONFIG, props.producerAcks()
        );
        return new KafkaProducer<>(config);
    }

    private void ensureConnected() {
        if (!connected.get()) {
            throw new TransportException("transport is not connected");
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) { /* ignore */ }
        }
    }

}
