package com.performance.platform.agent.registration;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.LifecycleChannel;
import com.performance.platform.transport.TransportException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Implémentation de {@link AgentRegistrationPort} qui publie les événements
 * de cycle de vie d'agent via {@link ExecutionTransport#publishAgentEvent}.
 * <p>
 * Utilise {@link AgentLifecycleEvent} (ADR-012) — pas d'{@code ExecutionId},
 * canal séparé des événements d'exécution.
 */
public class TransportAgentRegistration implements AgentRegistrationPort {

    private static final Logger log = LoggerFactory.getLogger(TransportAgentRegistration.class);

    private final LifecycleChannel transport;

    public TransportAgentRegistration(LifecycleChannel transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public void register(AgentDescriptor descriptor) throws RegistrationException {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        try {
            var event = new AgentLifecycleEvent(
                    EventId.generate(),
                    descriptor.id(),
                    AgentLifecycleEvent.AGENT_REGISTERED,
                    AgentLifecyclePayloadCodec.encode(descriptor),
                    Instant.now()
            );
            transport.publishAgentEvent(event);
            log.info("action=agent_registered agentId={} name={} supportedTasks={}",
                    descriptor.id().value(), descriptor.name(), descriptor.supportedTaskNames().size());
        } catch (TransportException e) {
            log.error("action=agent_registration_failed agentId={}", descriptor.id().value(), e);
            throw new RegistrationException("Failed to register agent: " + descriptor.id().value(), e);
        }
    }

    @Override
    public void deregister(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        try {
            var event = new AgentLifecycleEvent(
                    EventId.generate(),
                    agentId,
                    AgentLifecycleEvent.AGENT_DEREGISTERED,
                    Map.of("agentId", agentId.value()),
                    Instant.now()
            );
            transport.publishAgentEvent(event);
            log.info("action=agent_deregistered agentId={}", agentId.value());
        } catch (TransportException e) {
            log.error("action=agent_deregistration_failed agentId={}", agentId.value(), e);
            throw new RegistrationException("Failed to deregister agent: " + agentId.value(), e);
        }
    }

    @Override
    public void sendHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(heartbeat, "heartbeat must not be null");
        try {
            var event = new AgentLifecycleEvent(
                    EventId.generate(),
                    agentId,
                    AgentLifecycleEvent.AGENT_HEARTBEAT,
                    AgentLifecyclePayloadCodec.encode(heartbeat),
                    Instant.now()
            );
            transport.publishAgentEvent(event);
            log.debug("action=heartbeat_sent agentId={} state={} activeTasks={}",
                    agentId.value(), heartbeat.state(), heartbeat.activeTasks());
        } catch (TransportException e) {
            log.error("action=heartbeat_failed agentId={}", agentId.value(), e);
            throw new RegistrationException("Failed to send heartbeat for agent: " + agentId.value(), e);
        }
    }

}
