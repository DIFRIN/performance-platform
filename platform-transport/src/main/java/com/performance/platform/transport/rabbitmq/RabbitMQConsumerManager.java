package com.performance.platform.transport.rabbitmq;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.AgentSignalHandler;
import com.performance.platform.transport.ExecutionEventHandler;
import com.performance.platform.transport.TaskRequestHandler;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestion du consumer RabbitMQ pour {@link RabbitMQExecutionTransport}.
 * <p>
 * Cree un consumer unique sur la file exclusive de l'agent.
 * Le dispatching est effectue par type de message (via le marqueur
 * {@code @type} decode par {@link RabbitMQMessageCodec}).
 * <p>
 * Utilise {@code basicConsume} en mode ack manuel (at-least-once).
 * Les deliveries sont traitees sur les Virtual Threads configures
 * au niveau de la {@code ConnectionFactory}.
 * <p>
 * Package-private : usage interne a {@code rabbitmq}.
 */
class RabbitMQConsumerManager {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumerManager.class);

    private final Connection connection;
    private final String queueName;
    private final RabbitMQMessageCodec codec;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final List<TaskRequestHandler> taskHandlers;
    private final List<AgentSignalHandler> signalHandlers;
    private final ConcurrentMap<RabbitMQSubscription, ExecutionEventHandler> subscriptions;
    private final ConcurrentMap<RabbitMQSubscription, AgentLifecycleEventHandler> agentSubscriptions;

    private Channel consumerChannel;
    private String consumerTag;

    RabbitMQConsumerManager(Connection connection, String queueName,
                            RabbitMQMessageCodec codec,
                            List<TaskRequestHandler> taskHandlers,
                            List<AgentSignalHandler> signalHandlers,
                            ConcurrentMap<RabbitMQSubscription, ExecutionEventHandler> subscriptions,
                            ConcurrentMap<RabbitMQSubscription, AgentLifecycleEventHandler> agentSubscriptions) {
        this.connection = connection;
        this.queueName = queueName;
        this.codec = codec;
        this.taskHandlers = taskHandlers;
        this.signalHandlers = signalHandlers;
        this.subscriptions = subscriptions;
        this.agentSubscriptions = agentSubscriptions;
    }

    /**
     * Demarre le consumer sur la file avec ack manuel.
     * Les deliveries sont distribuees aux handlers par type de message.
     */
    void start() throws IOException {
        consumerChannel = connection.createChannel();
        running.set(true);
        consumerTag = consumerChannel.basicConsume(
                queueName, false, this::handleDelivery, this::handleCancel);
        log.debug("action=consumer_start queue={} consumerTag={}", queueName, consumerTag);
    }

    /**
     * Arrete le consumer et ferme le canal.
     */
    void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (consumerTag != null) {
                consumerChannel.basicCancel(consumerTag);
            }
        } catch (IOException e) {
            log.warn("action=cancel_consumer_error queue={} consumerTag={}",
                    queueName, consumerTag, e);
        }
        closeQuietly(consumerChannel);
        log.debug("action=consumer_stop queue={}", queueName);
    }

    // === Delivery callback (executed on RabbitMQ client Virtual Thread) ===

    private void handleDelivery(String consumerTag, Delivery delivery) {
        try {
            Object message = codec.decodeMessage(delivery.getBody());
            switch (message) {
                case TaskExecutionRequest request ->
                        taskHandlers.forEach(h -> h.onRequest(request));
                case ExecutionEvent event ->
                        subscriptions.forEach((sub, h) -> {
                            if (sub.isActive()) h.onEvent(event);
                        });
                case AgentLifecycleEvent event ->
                        agentSubscriptions.forEach((sub, h) -> {
                            if (sub.isActive()) h.onEvent(event);
                        });
                case AgentSignal signal ->
                        signalHandlers.forEach(h -> h.onSignal(signal));
                default ->
                        log.warn("action=unknown_message_type queue={} type={}",
                                queueName, message.getClass().getSimpleName());
            }
            consumerChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("action=dispatch_error queue={} deliveryTag={}",
                    queueName, delivery.getEnvelope().getDeliveryTag(), e);
            try {
                consumerChannel.basicNack(
                        delivery.getEnvelope().getDeliveryTag(), false, false);
            } catch (IOException ignored) {
                // Channel likely broken — transport will reconnect
            }
        }
    }

    private void handleCancel(String consumerTag) {
        log.info("action=consumer_cancelled queue={} consumerTag={}",
                queueName, consumerTag);
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
