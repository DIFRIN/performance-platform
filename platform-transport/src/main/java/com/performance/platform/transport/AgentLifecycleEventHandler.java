package com.performance.platform.transport;

/**
 * Handler appelé par le transport lorsqu'un {@link AgentLifecycleEvent} est reçu
 * via le mécanisme de souscription. Enregistré via
 * {@link ExecutionTransport#subscribeAgentEvents(AgentLifecycleEventHandler)}.
 * <p>
 * Utilisé côté orchestrateur pour alimenter l'{@code AgentRegistry}.
 */
@FunctionalInterface
public interface AgentLifecycleEventHandler {

    /**
     * Appelé à la réception d'un événement de cycle de vie d'agent.
     *
     * @param event l'événement reçu
     */
    void onEvent(AgentLifecycleEvent event);
}
