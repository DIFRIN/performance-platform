package com.performance.platform.agent.registration;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.id.AgentId;

/**
 * Port de sortie pour l'enregistrement et le heartbeat de l'agent
 * auprès de l'orchestrateur via la couche de transport.
 * <p>
 * Implémentation par défaut : {@link TransportAgentRegistration}
 * qui publie des {@code ExecutionEvent} via {@code ExecutionTransport}.
 */
public interface AgentRegistrationPort {

    /**
     * Enregistre cet agent auprès de l'orchestrateur.
     *
     * @param descriptor le descripteur complet de l'agent
     * @throws RegistrationException en cas d'erreur de communication
     */
    void register(AgentDescriptor descriptor) throws RegistrationException;

    /**
     * Désenregistre cet agent.
     *
     * @param agentId l'identifiant de l'agent à désenregistrer
     */
    void deregister(AgentId agentId);

    /**
     * Envoie un heartbeat à l'orchestrateur.
     *
     * @param agentId   l'identifiant de l'agent
     * @param heartbeat les données du heartbeat
     */
    void sendHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
}
