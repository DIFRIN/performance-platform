package com.performance.platform.agent.registration;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.message.ExecutionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implémentation de {@link AgentRegistrationPort} qui publie les événements
 * d'enregistrement et de heartbeat via {@link ExecutionTransport}.
 * <p>
 * Chaque méthode construit un {@link ExecutionEvent} avec le type approprié
 * et le payload contenant les données du domaine, puis le publie.
 */
public class TransportAgentRegistration implements AgentRegistrationPort {

    /** ExecutionId sentinelle pour les événements non liés à une exécution. */
    static final ExecutionId NO_EXECUTION = ExecutionId.of("NO_EXECUTION");

    private static final Logger log = LoggerFactory.getLogger(TransportAgentRegistration.class);

    private final ExecutionTransport transport;

    public TransportAgentRegistration(ExecutionTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public void register(AgentDescriptor descriptor) throws RegistrationException {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        try {
            var event = new ExecutionEvent(
                    EventId.generate(),
                    NO_EXECUTION,
                    null,
                    descriptor.id(),
                    ExecutionEvent.AGENT_REGISTERED,
                    toPayload(descriptor),
                    Instant.now()
            );
            transport.publishEvent(event);
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
            var event = new ExecutionEvent(
                    EventId.generate(),
                    NO_EXECUTION,
                    null,
                    agentId,
                    ExecutionEvent.AGENT_DEREGISTERED,
                    Map.of("agentId", agentId.value()),
                    Instant.now()
            );
            transport.publishEvent(event);
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
            var event = new ExecutionEvent(
                    EventId.generate(),
                    NO_EXECUTION,
                    null,
                    agentId,
                    ExecutionEvent.AGENT_HEARTBEAT,
                    toPayload(heartbeat),
                    Instant.now()
            );
            transport.publishEvent(event);
            log.debug("action=heartbeat_sent agentId={} state={} activeTasks={}",
                    agentId.value(), heartbeat.state(), heartbeat.activeTasks());
        } catch (TransportException e) {
            log.error("action=heartbeat_failed agentId={}", agentId.value(), e);
            throw new RegistrationException("Failed to send heartbeat for agent: " + agentId.value(), e);
        }
    }

    // === Conversion des objets domaine en payload Map ===

    private static Map<String, Object> toPayload(AgentDescriptor d) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agentId", d.id().value());
        payload.put("name", d.name());
        payload.put("host", d.host());
        payload.put("port", d.port());
        payload.put("httpCallbackUrl", d.httpCallbackUrl());
        payload.put("supportedTaskNames", d.supportedTaskNames());
        payload.put("state", d.state().name());
        payload.put("registeredAt", d.registeredAt().toString());
        payload.put("lastHeartbeatAt", d.lastHeartbeatAt().toString());
        payload.put("registrationTtlSeconds", d.registrationTtl().toSeconds());
        // AgentCapabilities est un record — on le sérialise via ses composants
        payload.put("capabilities", capabilitiesToPayload(d.capabilities()));
        return payload;
    }

    private static Map<String, Object> toPayload(AgentHeartbeat h) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agentId", h.agentId().value());
        payload.put("state", h.state().name());
        payload.put("activeTasks", h.activeTasks());
        payload.put("sentAt", h.sentAt().toString());
        return payload;
    }

    private static Map<String, Object> capabilitiesToPayload(
            com.performance.platform.domain.agent.AgentCapabilities caps) {
        var map = new LinkedHashMap<String, Object>();
        map.put("maxConcurrentTasks", caps.maxConcurrentTasks());
        map.put("version", caps.version());
        return map;
    }
}
