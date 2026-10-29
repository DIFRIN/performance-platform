package com.performance.platform.transport;

import com.performance.platform.transport.message.ExecutionEvent;

/**
 * Canal des evenements d'execution (agents → orchestrateur).
 */
public interface EventChannel {

    /**
     * Publie un evenement d'execution.
     *
     * @param event l'evenement a publier
     * @throws TransportException en cas d'erreur de communication
     */
    void publishEvent(ExecutionEvent event);

    /**
     * Souscrit aux evenements d'execution.
     *
     * @param handler le handler
     * @return une souscription annulable
     */
    Subscription subscribe(ExecutionEventHandler handler);
}
