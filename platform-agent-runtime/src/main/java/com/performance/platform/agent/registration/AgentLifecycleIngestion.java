package com.performance.platform.agent.registration;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.event.AgentLost;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.AgentLifecycleEventHandler;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Objects;

/**
 * Pont entre le canal transport de cycle de vie et le registre d'agents
 * de l'orchestrateur.
 * <p>
 * Sans ce composant, les evenements {@code AgentRegistered}/{@code AgentHeartbeat}/
 * {@code AgentDeregistered} publies par les agents ne sont jamais consommes :
 * le {@code AgentRegistry} reste vide et l'orchestrateur ne peut dispatcher
 * aucune tache.
 * <p>
 * Actif uniquement en mode DISTRIBUTED + role ORCHESTRATOR.
 */
public class AgentLifecycleIngestion
        implements AgentLifecycleEventHandler, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleIngestion.class);

    private final ExecutionTransport transport;
    private final AgentRegistryPort registry;
    private final ApplicationEventPublisher eventPublisher;

    private volatile Subscription subscription;

    public AgentLifecycleIngestion(ExecutionTransport transport,
                                   AgentRegistryPort registry,
                                   ApplicationEventPublisher eventPublisher) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Override
    public void afterPropertiesSet() {
        subscription = transport.subscribeAgentEvents(this);
        log.info("action=agent_lifecycle_ingestion_started");
    }

    @Override
    public void destroy() {
        Subscription current = subscription;
        if (current != null) {
            current.cancel();
            subscription = null;
        }
        log.info("action=agent_lifecycle_ingestion_stopped");
    }

    @Override
    public void onEvent(AgentLifecycleEvent event) {
        if (event == null) {
            return;
        }
        String type = event.eventType();
        switch (type) {
            case AgentLifecycleEvent.AGENT_REGISTERED -> {
                var descriptor = AgentLifecyclePayloadCodec.decodeDescriptor(
                        event.agentId(), event.payload());
                registry.onAgentRegistered(descriptor);
                log.info("action=agent_lifecycle_registered agentId={} supportedTasks={}",
                        event.agentId().value(), descriptor.supportedTaskNames().size());
            }
            case AgentLifecycleEvent.AGENT_HEARTBEAT -> {
                var heartbeat = AgentLifecyclePayloadCodec.decodeHeartbeat(
                        event.agentId(), event.payload());
                registry.onAgentHeartbeat(event.agentId(), heartbeat);
            }
            case AgentLifecycleEvent.AGENT_DEREGISTERED -> {
                registry.onAgentDeregistered(event.agentId());
                log.info("action=agent_lifecycle_deregistered agentId={}",
                        event.agentId().value());
            }
            case AgentLifecycleEvent.AGENT_LOST -> {
                registry.onAgentExpired(event.agentId());
                publishAgentLost(event.agentId(), "agent lifecycle event");
            }
            default -> log.warn("action=agent_lifecycle_unknown_type agentId={} eventType={}",
                    event.agentId().value(), type);
        }
    }

    /**
     * Publie un {@link AgentLost} sur le bus Spring. Les composants de
     * l'engine l'ecoutent pour invalider les claims de cet agent
     * (fast-fail au lieu d'attendre le timeout).
     */
    public void publishAgentLost(com.performance.platform.domain.id.AgentId agentId, String reason) {
        eventPublisher.publishEvent(new AgentLost(agentId, reason, Instant.now()));
        log.warn("action=agent_lost agentId={} reason={}", agentId.value(), reason);
    }
}
