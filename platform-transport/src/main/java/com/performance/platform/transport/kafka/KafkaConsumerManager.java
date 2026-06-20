package com.performance.platform.transport.kafka;

import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.AgentSignalHandler;
import com.performance.platform.transport.ExecutionEventHandler;

import com.performance.platform.transport.TaskRequestHandler;
import com.performance.platform.transport.message.ExecutionEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentMap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestion des consumers Kafka pour {@link KafkaExecutionTransport}.
 * <p>
 * Cree et gere trois consumers (tasks, events, signals), chacun avec
 * sa propre boucle de polling sur Virtual Thread. Le dispatching est
 * delegue aux registres de handlers fournis par le transport parent.
 * <p>
 * Package-private : usage interne a {@code kafka}.
 */
class KafkaConsumerManager {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerManager.class);

    private final String bootstrapServers;
    private final String consumerGroup;
    private final String tasksTopic;
    private final String eventsTopic;
    private final String signalsTopic;
    private final KafkaMessageCodec codec;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // Handler registries (shared with transport)
    private final List<TaskRequestHandler> taskHandlers;
    private final List<AgentSignalHandler> signalHandlers;
    private final ConcurrentMap<KafkaSubscription, ExecutionEventHandler> subscriptions;
    private final ConcurrentMap<KafkaSubscription, AgentLifecycleEventHandler> agentSubscriptions;

    // Kafka consumers
    private KafkaConsumer<String, byte[]> taskConsumer;
    private KafkaConsumer<String, byte[]> eventConsumer;
    private KafkaConsumer<String, byte[]> signalConsumer;

    // Virtual threads
    private ExecutorService consumerExecutor;
    private Future<?> taskFuture;
    private Future<?> eventFuture;
    private Future<?> signalFuture;

    KafkaConsumerManager(String bootstrapServers, String consumerGroup,
                         String tasksTopic, String eventsTopic, String signalsTopic,
                         KafkaMessageCodec codec,
                         List<TaskRequestHandler> taskHandlers,
                         List<AgentSignalHandler> signalHandlers,
                         ConcurrentMap<KafkaSubscription, ExecutionEventHandler> subscriptions,
                         ConcurrentMap<KafkaSubscription, AgentLifecycleEventHandler> agentSubscriptions) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroup = consumerGroup;
        this.tasksTopic = tasksTopic;
        this.eventsTopic = eventsTopic;
        this.signalsTopic = signalsTopic;
        this.codec = codec;
        this.taskHandlers = taskHandlers;
        this.signalHandlers = signalHandlers;
        this.subscriptions = subscriptions;
        this.agentSubscriptions = agentSubscriptions;
    }

    void start() {
        taskConsumer = createConsumer(tasksTopic);
        eventConsumer = createConsumer(eventsTopic);
        signalConsumer = createConsumer(signalsTopic);
        running.set(true);
        consumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        taskFuture = consumerExecutor.submit(this::taskConsumerLoop);
        eventFuture = consumerExecutor.submit(this::eventConsumerLoop);
        signalFuture = consumerExecutor.submit(this::signalConsumerLoop);
    }

    void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        wakeupAllConsumers();
        awaitFuture(taskFuture, "task");
        awaitFuture(eventFuture, "event");
        awaitFuture(signalFuture, "signal");
        closeQuietly(taskConsumer);
        closeQuietly(eventConsumer);
        closeQuietly(signalConsumer);
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
        }
    }

    // === Consumer loops (Virtual Threads) ===

    private void taskConsumerLoop() {
        log.debug("action=consumer_loop_start topic={}", tasksTopic);
        try {
            while (running.get()) {
                var records = taskConsumer.poll(Duration.ofSeconds(1));
                if (records.isEmpty() || taskHandlers.isEmpty()) continue;
                records.forEach(record -> {
                    try {
                        var request = codec.decodeTaskRequest(record.value());
                        taskHandlers.forEach(h -> h.onRequest(request));
                    } catch (Exception e) {
                        log.error("action=dispatch_task_error offset={} partition={}",
                                record.offset(), record.partition(), e);
                    }
                });
            }
        } catch (WakeupException e) {
            // Normal shutdown
        } catch (Exception e) {
            log.error("action=task_consumer_loop_error topic={}", tasksTopic, e);
        }
        log.debug("action=consumer_loop_stop topic={}", tasksTopic);
    }

    private void eventConsumerLoop() {
        log.debug("action=consumer_loop_start topic={}", eventsTopic);
        try {
            while (running.get()) {
                var records = eventConsumer.poll(Duration.ofSeconds(1));
                if (records.isEmpty()) continue;
                if (subscriptions.isEmpty() && agentSubscriptions.isEmpty()) continue;
                records.forEach(record -> {
                    try {
                        Object event = codec.decodeEvent(record.value());
                        if (event instanceof ExecutionEvent ee) {
                            subscriptions.forEach((sub, h) -> { if (sub.isActive()) h.onEvent(ee); });
                        } else if (event instanceof AgentLifecycleEvent ae) {
                            agentSubscriptions.forEach((sub, h) -> { if (sub.isActive()) h.onEvent(ae); });
                        }
                    } catch (Exception e) {
                        log.error("action=dispatch_event_error offset={} partition={}",
                                record.offset(), record.partition(), e);
                    }
                });
            }
        } catch (WakeupException e) {
            // Normal shutdown
        } catch (Exception e) {
            log.error("action=event_consumer_loop_error topic={}", eventsTopic, e);
        }
        log.debug("action=consumer_loop_stop topic={}", eventsTopic);
    }

    private void signalConsumerLoop() {
        log.debug("action=consumer_loop_start topic={}", signalsTopic);
        try {
            while (running.get()) {
                var records = signalConsumer.poll(Duration.ofSeconds(1));
                if (records.isEmpty() || signalHandlers.isEmpty()) continue;
                records.forEach(record -> {
                    try {
                        var signal = codec.decodeSignal(record.value());
                        signalHandlers.forEach(h -> h.onSignal(signal));
                    } catch (Exception e) {
                        log.error("action=dispatch_signal_error offset={} partition={}",
                                record.offset(), record.partition(), e);
                    }
                });
            }
        } catch (WakeupException e) {
            // Normal shutdown
        } catch (Exception e) {
            log.error("action=signal_consumer_loop_error topic={}", signalsTopic, e);
        }
        log.debug("action=consumer_loop_stop topic={}", signalsTopic);
    }

    // === Kafka infrastructure ===

    private KafkaConsumer<String, byte[]> createConsumer(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, consumerGroup,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true"
        );
        var consumer = new KafkaConsumer<String, byte[]>(config);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private void wakeupAllConsumers() {
        wakeupQuietly(taskConsumer);
        wakeupQuietly(eventConsumer);
        wakeupQuietly(signalConsumer);
    }

    private void wakeupQuietly(KafkaConsumer<?, ?> consumer) {
        if (consumer != null) {
            try { consumer.wakeup(); } catch (Exception ignored) { /* ignore */ }
        }
    }

    private void awaitFuture(Future<?> future, String name) {
        if (future != null) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("action=consumer_loop_await_timeout loop={}", name, e);
            }
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) { /* ignore */ }
        }
    }
}
