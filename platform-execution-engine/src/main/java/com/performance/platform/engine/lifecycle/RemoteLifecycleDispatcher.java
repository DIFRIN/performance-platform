package com.performance.platform.engine.lifecycle;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import com.performance.platform.transport.ExecutionTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation DISTRIBUTED du dispatcher de signaux de cycle de vie.
 * Broadcast via {@code transport.broadcastSignal()}.
 *
 * <p>Active uniquement quand {@code runtime.mode=DISTRIBUTED}.</p>
 */
@Component
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteLifecycleDispatcher implements ExecutionLifecycleDispatcher {

    private final ExecutionTransport transport;

    public RemoteLifecycleDispatcher(ExecutionTransport transport) {
        this.transport = transport;
    }

    @Override
    public void dispatch(ExecutionLifecycleSignal signal) {
        transport.broadcastSignal(signal);
    }
}
