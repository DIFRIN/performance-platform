package com.performance.platform.transport;

import com.performance.platform.domain.event.AgentSignal;

/**
 * Handler appele par le transport lorsqu'un {@link AgentSignal} est recu.
 * Le handler est enregistre cote agent via
 * {@link ExecutionTransport#receiveSignal(AgentSignalHandler)}.
 */
@FunctionalInterface
public interface AgentSignalHandler {

    /**
     * Appele a la reception d'un signal broadcast depuis l'orchestrateur.
     *
     * @param signal le signal recu
     */
    void onSignal(AgentSignal signal);
}
