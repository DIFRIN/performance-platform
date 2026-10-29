package com.performance.platform.transport;

import com.performance.platform.domain.event.AgentSignal;

/**
 * Canal des signaux broadcast (orchestrateur → agents) : restart de scenario,
 * signaux de cycle de vie START/STOP.
 */
public interface SignalChannel {

    /**
     * Diffuse un signal a tous les agents.
     *
     * @param signal le signal a diffuser
     * @throws TransportException en cas d'erreur de communication
     */
    void broadcastSignal(AgentSignal signal);

    /**
     * Enregistre le handler appele a la reception d'un signal (cote agent).
     *
     * @param handler le handler
     */
    void receiveSignal(AgentSignalHandler handler);
}
