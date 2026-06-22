package com.performance.platform.transport.kafka;

import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.AgentSignalHandler;
import com.performance.platform.transport.ExecutionEventHandler;
import com.performance.platform.transport.TaskRequestHandler;
import com.performance.platform.transport.message.ExecutionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registre dynamique de KafkaMessageListenerContainer crees a la demande.
 * Remplace KafkaConsumerManager (boucles de polling manuel avec
 * KafkaConsumer raw) par des containers Spring Kafka qui gerent
 * automatiquement le polling, le rebalance et la reconnexion.
 *
 * <p>ADR-009 : chaque agent a son propre consumer group
 * (groupId = agentId) pour recevoir tous les messages broadcastes
 * sur les topics tasks et signals.
 *
 * <p>Thread-safe. Tous les containers utilisent
 * ContainerProperties.AckMode.RECORD (ack apres chaque message).
 *
 * <p>CC-02: La combinaison des registres (tasks, signals, events/execution,
 * events/lifecycle, stopAll) et du helper createContainer forme un pipeline
 * d'enregistrement/cycle de vie cohesif inseparable.
 */
public class DynamicKafkaListenerRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynamicKafkaListenerRegistry.class);

    private final ConsumerFactory<String, byte[]> consumerFactory;
    private final KafkaMessageCodec codec;
    private final String tasksTopic;
    private final String signalsTopic;
    private final String eventsTopic;

    private final ConcurrentMap<String, List<KafkaMessageListenerContainer<String, byte[]>>>
            containersByAgent = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, List<TaskRequestHandler>> taskHandlersByAgent =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, List<AgentSignalHandler>> signalHandlersByAgent =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Boolean> hasTaskContainer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> hasSignalContainer = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, EventGroup> eventGroups = new ConcurrentHashMap<>();

    private static class EventGroup {
        final KafkaMessageListenerContainer<String, byte[]> container;
        final List<ExecutionEventHandler> executionHandlers = new CopyOnWriteArrayList<>();
        final List<AgentLifecycleEventHandler> lifecycleHandlers = new CopyOnWriteArrayList<>();

        EventGroup(KafkaMessageListenerContainer<String, byte[]> container) {
            this.container = container;
        }

        boolean isEmpty() {
            return executionHandlers.isEmpty() && lifecycleHandlers.isEmpty();
        }
    }

    public DynamicKafkaListenerRegistry(
            ConsumerFactory<String, byte[]> consumerFactory,
            KafkaMessageCodec codec,
            String tasksTopic, String signalsTopic, String eventsTopic) {
        this.consumerFactory = consumerFactory;
        this.codec = codec;
        this.tasksTopic = tasksTopic;
        this.signalsTopic = signalsTopic;
        this.eventsTopic = eventsTopic;
    }

    // ==================== Task listeners ====================

    /**
     * Cree et demarre un container tasks pour cet agent si pas encore existant,
     * et enregistre le handler. Les appels suivants pour le meme agent
     * ajoutent simplement le handler a la liste existante.
     */
    public void registerTaskListener(String agentId, TaskRequestHandler handler) {
        List<KafkaMessageListenerContainer<String, byte[]>> containers =
                containersByAgent.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>());

        taskHandlersByAgent.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(handler);

        if (hasTaskContainer.putIfAbsent(agentId, Boolean.TRUE) == null) {
            KafkaMessageListenerContainer<String, byte[]> container =
                    createContainer(tasksTopic, agentId, record -> {
                        try {
                            var request = codec.decodeTaskRequest(record.value());
                            List<TaskRequestHandler> handlers = taskHandlersByAgent.get(agentId);
                            if (handlers != null) {
                                handlers.forEach(h -> {
                                    try { h.onRequest(request); } catch (Exception ex) {
                                        log.error("action=task_handler_error agentId={} taskId={}",
                                                agentId, request.id().value(), ex);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            log.error("action=decode_task_error agentId={} offset={}",
                                    agentId, record.offset(), e);
                        }
                    });
            containers.add(container);
            log.info("action=task_container_started agentId={} topic={}", agentId, tasksTopic);
        } else {
            log.debug("action=task_handler_added agentId={}", agentId);
        }
    }

    // ==================== Signal listeners ====================

    /**
     * Cree et demarre un container signals pour cet agent.
     */
    public void registerSignalListener(String agentId, AgentSignalHandler handler) {
        List<KafkaMessageListenerContainer<String, byte[]>> containers =
                containersByAgent.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>());

        signalHandlersByAgent.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(handler);

        if (hasSignalContainer.putIfAbsent(agentId, Boolean.TRUE) == null) {
            KafkaMessageListenerContainer<String, byte[]> container =
                    createContainer(signalsTopic, agentId, record -> {
                        try {
                            var signal = codec.decodeSignal(record.value());
                            List<AgentSignalHandler> handlers = signalHandlersByAgent.get(agentId);
                            if (handlers != null) {
                                handlers.forEach(h -> {
                                    try { h.onSignal(signal); } catch (Exception ex) {
                                        log.error("action=signal_handler_error agentId={} signalId={}",
                                                agentId, signal.id().value(), ex);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            log.error("action=decode_signal_error agentId={} offset={}",
                                    agentId, record.offset(), e);
                        }
                    });
            containers.add(container);
            log.info("action=signal_container_started agentId={} topic={}", agentId, signalsTopic);
        } else {
            log.debug("action=signal_handler_added agentId={}", agentId);
        }
    }

    // ==================== Event listeners ====================

    /**
     * Enregistre un handler ExecutionEvent sur le topic events.
     *
     * <p><b>CC-02</b>: method ~42 lines - getOrCreateEventGroup (create container
     * or reuse existing), add handler, return cleanup Runnable. The container
     * lifecycle, handler dispatch, and cleanup form a single cohesive unit.</p>
     *
     * @return un Runnable de nettoyage qui retire uniquement ce handler
     */
    public Runnable registerExecutionHandler(String groupId, ExecutionEventHandler handler) {
        EventGroup group = getOrCreateEventGroup(groupId);

        group.executionHandlers.add(handler);
        log.debug("action=execution_handler_added groupId={}", groupId);

        return () -> {
            group.executionHandlers.remove(handler);
            log.debug("action=execution_handler_removed groupId={}", groupId);
            cleanupEventGroupIfEmpty(groupId, group);
        };
    }

    /**
     * Enregistre un handler AgentLifecycleEvent sur le topic events.
     *
     * <p><b>CC-02</b>: method ~43 lines - getOrCreateEventGroup (create container
     * or reuse existing), add handler, return cleanup Runnable. The container
     * lifecycle, handler dispatch, and cleanup form a single cohesive unit.</p>
     *
     * @return un Runnable de nettoyage
     */
    public Runnable registerAgentLifecycleHandler(String groupId,
                                                   AgentLifecycleEventHandler handler) {
        EventGroup group = getOrCreateEventGroup(groupId);

        group.lifecycleHandlers.add(handler);
        log.debug("action=lifecycle_handler_added groupId={}", groupId);

        return () -> {
            group.lifecycleHandlers.remove(handler);
            log.debug("action=lifecycle_handler_removed groupId={}", groupId);
            cleanupEventGroupIfEmpty(groupId, group);
        };
    }

    /**
     * Retourne un EventGroup existant ou en cree un nouveau avec un container
     * Kafka ecoutant le topic events. Factorise la duplication entre
     * registerExecutionHandler() et registerAgentLifecycleHandler().
     *
     * @param groupId le consumer group Kafka
     * @return l'EventGroup nouvellement cree ou existant
     */
    private EventGroup getOrCreateEventGroup(String groupId) {
        return eventGroups.computeIfAbsent(groupId, gid -> {
            KafkaMessageListenerContainer<String, byte[]> container =
                    createContainer(eventsTopic, gid, record -> {
                        EventGroup eg = eventGroups.get(gid);
                        if (eg == null) return;
                        try {
                            Object evt = codec.decodeEvent(record.value());
                            if (evt instanceof ExecutionEvent ee) {
                                eg.executionHandlers.forEach(h -> {
                                    try { h.onEvent(ee); } catch (Exception ex) {
                                        log.error("action=execution_handler_error groupId={}", gid, ex);
                                    }
                                });
                            } else if (evt instanceof AgentLifecycleEvent ae) {
                                eg.lifecycleHandlers.forEach(h -> {
                                    try { h.onEvent(ae); } catch (Exception ex) {
                                        log.error("action=lifecycle_handler_error groupId={}", gid, ex);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            log.error("action=decode_event_error groupId={} offset={}",
                                    gid, record.offset(), e);
                        }
                    });
            List<KafkaMessageListenerContainer<String, byte[]>> agentContainers =
                    containersByAgent.computeIfAbsent(gid, k -> new CopyOnWriteArrayList<>());
            agentContainers.add(container);
            log.info("action=event_container_started groupId={} topic={}", gid, eventsTopic);
            return new EventGroup(container);
        });
    }

    private void cleanupEventGroupIfEmpty(String groupId, EventGroup group) {
        if (group.isEmpty()) {
            eventGroups.remove(groupId);
            group.container.stop();
            List<KafkaMessageListenerContainer<String, byte[]>> agentContainers =
                    containersByAgent.get(groupId);
            if (agentContainers != null) {
                agentContainers.remove(group.container);
            }
            log.info("action=event_container_stopped groupId={}", groupId);
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Stoppe et supprime tous les containers de l'agent donne.
     */
    public void unregisterAgent(String agentId) {
        List<KafkaMessageListenerContainer<String, byte[]>> containers =
                containersByAgent.remove(agentId);
        if (containers != null) {
            containers.forEach(c -> {
                c.stop();
                log.info("action=container_stopped agentId={}", agentId);
            });
        }
        taskHandlersByAgent.remove(agentId);
        signalHandlersByAgent.remove(agentId);
        hasTaskContainer.remove(agentId);
        hasSignalContainer.remove(agentId);
        log.info("action=agent_unregistered agentId={}", agentId);
    }

    /**
     * Stoppe tous les containers actifs (appele lors de disconnect()).
     */
    public void stopAll() {
        // Collect unique containers (event containers appear in both maps)
        var allContainers = new java.util.LinkedHashSet<KafkaMessageListenerContainer<String, byte[]>>();
        containersByAgent.values().forEach(allContainers::addAll);
        eventGroups.values().forEach(g -> allContainers.add(g.container));

        allContainers.forEach(c -> {
            c.stop();
            log.info("action=container_stopped");
        });

        containersByAgent.clear();
        taskHandlersByAgent.clear();
        signalHandlersByAgent.clear();
        hasTaskContainer.clear();
        hasSignalContainer.clear();
        eventGroups.clear();
        log.info("action=all_containers_stopped");
    }

    // ==================== Helpers ====================

    /**
     * Cree et demarre un KafkaMessageListenerContainer.
     * AckMode.RECORD : ack apres chaque message traite.
     */
    private KafkaMessageListenerContainer<String, byte[]> createContainer(
            String topic, String groupId, MessageListener<String, byte[]> listener) {
        ContainerProperties props = new ContainerProperties(topic);
        props.setGroupId(groupId);
        props.setMessageListener(listener);
        props.setAckMode(ContainerProperties.AckMode.RECORD);
        KafkaMessageListenerContainer<String, byte[]> container =
                new KafkaMessageListenerContainer<>(consumerFactory, props);
        container.start();
        return container;
    }
}
