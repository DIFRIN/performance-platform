package com.performance.platform.transport;

import com.performance.platform.transport.message.ExecutionEvent;

/**
 * Handler appele par le transport lorsqu'un {@link ExecutionEvent} est recu
 * via le mecanisme de souscription. Enregistre via
 * {@link ExecutionTransport#subscribe(ExecutionEventHandler)}.
 */
@FunctionalInterface
public interface ExecutionEventHandler {

    /**
     * Appele a la reception d'un evenement d'execution.
     *
     * @param event l'evenement recu
     */
    void onEvent(ExecutionEvent event);
}
