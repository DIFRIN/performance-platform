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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation Kafka de ExecutionTransport.
 * <p>
 * Producer side: KafkaTemplate. Consumer side: DynamicKafkaListenerRegistry
 * (ISSUE-090) qui cree des KafkaMessageListenerContainer Spring par agent.
 * ADR-009: chaque agent a son propre consumer group.
 */
public class KafkaExecutionTransport implements ExecutionTransport {

    private static final Logger log = LoggerFactory.getLogger(KafkaExecutionTransport.class);

    private final KafkaTransportProperties props;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ConsumerFactory<String, byte[]> consumerFactory;
    private final KafkaMessageCodec codec;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    static final String ORCHESTRATOR_GROUP = "orchestrator";

    private DynamicKafkaListenerRegistry listenerRegistry;

    /**
     * Constructeur principal utilise par {@code KafkaTransportBeans}.
     *
     * @param props proprietes de transport Kafka
     * @param kafkaTemplate le KafkaTemplate qualifie transportKafkaTemplate
     * @param containerFactory la ConcurrentKafkaListenerContainerFactory qualifiee
     *                         transportContainerFactory — le ConsumerFactory est
     *                         extrait pour le DynamicKafkaListenerRegistry
     */
    public KafkaExecutionTransport(KafkaTransportProperties props,
                                   KafkaTemplate<String, byte[]> kafkaTemplate,
                                   ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory) {
        this.props = props;
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, byte[]> cf = containerFactory != null
                ? (ConsumerFactory<String, byte[]>) containerFactory.getConsumerFactory()
                : null;
        this.consumerFactory = cf;
        this.codec = new KafkaMessageCodec();
    }

    /**
     * Constructeur allege sans consumer (tests unitaires).
     */
    public KafkaExecutionTransport(KafkaTransportProperties props,
                                   KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.props = props;
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.consumerFactory = null;
        this.codec = new KafkaMessageCodec();
    }

    // === ExecutionTransport ===

    @Override
    public void connect() throws TransportException {
        if (connected.get()) return;
        try {
            if (consumerFactory != null) {
                this.listenerRegistry = new DynamicKafkaListenerRegistry(
                        consumerFactory, codec,
                        props.tasksTopic(), props.signalsTopic(), props.eventsTopic());
            }
            connected.set(true);
            log.info("action=connect transport=KAFKA bootstrap={} consumerGroup={}",
                    props.bootstrapServers(), props.consumerGroup());
        } catch (Exception e) {
            throw new TransportException("Failed to connect Kafka transport: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (!connected.compareAndSet(true, false)) return;
        if (listenerRegistry != null) listenerRegistry.stopAll();
        kafkaTemplate.flush();
        log.info("action=disconnect transport=KAFKA");
    }

    @Override
    public boolean isConnected() { return connected.get(); }

    @Override
    public TransportType getType() { return TransportType.KAFKA; }

    @Override
    public void dispatchTask(TaskExecutionRequest request) {
        if (request == null) throw new TransportException("request must not be null");
        ensureConnected();
        byte[] data = codec.encodeTaskRequest(request);
        sendRecord(props.tasksTopic(), request.id().value(), data, "dispatch_task",
                request.executionId().value());
    }

    @Override
    public void publishEvent(ExecutionEvent event) {
        if (event == null) throw new TransportException("event must not be null");
        ensureConnected();
        byte[] data = codec.encodeExecutionEvent(event);
        sendRecord(props.eventsTopic(), event.id().value(), data, "publish_event",
                event.executionId().value());
    }

    @Override
    public void publishAgentEvent(AgentLifecycleEvent event) {
        if (event == null) throw new TransportException("event must not be null");
        ensureConnected();
        byte[] data = codec.encodeAgentLifecycleEvent(event);
        sendRecord(props.eventsTopic(), event.id().value(), data, "publish_agent_event", null);
    }

    @Override
    public void broadcastSignal(AgentSignal signal) {
        if (signal == null) throw new TransportException("signal must not be null");
        ensureConnected();
        byte[] data = codec.encodeSignal(signal);
        sendRecord(props.signalsTopic(), signal.id().value(), data, "broadcast_signal", null);
    }

    @Override
    public void receiveTask(TaskRequestHandler handler) {
        if (handler == null) throw new TransportException("handler must not be null");
        ensureListenerRegistry();
        listenerRegistry.registerTaskListener(resolveAgentId(), handler);
    }

    @Override
    public void receiveSignal(AgentSignalHandler handler) {
        if (handler == null) throw new TransportException("handler must not be null");
        ensureListenerRegistry();
        listenerRegistry.registerSignalListener(resolveAgentId(), handler);
    }

    @Override
    public Subscription subscribe(ExecutionEventHandler handler) {
        if (handler == null) throw new TransportException("handler must not be null");
        ensureListenerRegistry();
        Runnable cleanup = listenerRegistry.registerExecutionHandler(ORCHESTRATOR_GROUP, handler);
        return new KafkaSubscription(cleanup);
    }

    @Override
    public Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler) {
        if (handler == null) throw new TransportException("handler must not be null");
        ensureListenerRegistry();
        Runnable cleanup = listenerRegistry.registerAgentLifecycleHandler(ORCHESTRATOR_GROUP, handler);
        return new KafkaSubscription(cleanup);
    }

    // === Producer helpers ===

    private void sendRecord(String topic, String key, byte[] data,
                            String action, String executionId) {
        try {
            kafkaTemplate.send(topic, key, data).get();
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

    private void ensureConnected() {
        if (!connected.get()) throw new TransportException("transport is not connected");
    }

    /**
     * Initialisation lazy du listenerRegistry pour supporter
     * receiveTask/receiveSignal avant connect().
     */
    private void ensureListenerRegistry() {
        if (!connected.get()) connect();
        if (listenerRegistry == null && consumerFactory != null) {
            listenerRegistry = new DynamicKafkaListenerRegistry(
                    consumerFactory, codec,
                    props.tasksTopic(), props.signalsTopic(), props.eventsTopic());
        }
    }

    /**
     * Resout l'agentId pour le consumer group (ADR-009).
     * Utilise consumerGroup des proprietes qui vaut
     * ${agent.id} pour les agents.
     */
    private String resolveAgentId() {
        return props.consumerGroup();
    }
}
