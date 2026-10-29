package com.performance.platform.transport;

/**
 * Canal des evenements de cycle de vie d'agent (registration, heartbeat,
 * deregistration, perte) — separe du canal execution (ADR-012).
 */
public interface LifecycleChannel {

    /**
     * Publie un evenement de cycle de vie d'agent.
     *
     * @param event l'evenement a publier
     * @throws TransportException en cas d'erreur de communication
     */
    void publishAgentEvent(AgentLifecycleEvent event);

    /**
     * Souscrit aux evenements de cycle de vie d'agent.
     *
     * @param handler le handler
     * @return une souscription annulable
     */
    Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler);
}
