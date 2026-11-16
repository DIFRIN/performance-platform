package com.performance.platform.engine.lifecycle;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import com.performance.platform.transport.SignalChannel;

/**
 * Dispatcher unique des signaux de cycle de vie (ADR-059).
 * <p>
 * Les signaux passent toujours par le canal de signaux du transport, quel que
 * soit le mode : en LOCAL, le transport en memoire livre le signal a l'agent
 * embarque sur un virtual thread. Il n'y a plus de chemin d'appel direct.
 */
public class TransportLifecycleDispatcher implements ExecutionLifecycleDispatcher {

    private final SignalChannel signalChannel;

    public TransportLifecycleDispatcher(SignalChannel signalChannel) {
        this.signalChannel = signalChannel;
    }

    @Override
    public void dispatch(ExecutionLifecycleSignal signal) {
        signalChannel.broadcastSignal(signal);
    }
}
