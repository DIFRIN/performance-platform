package com.performance.platform.engine;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;

/**
 * Dispatche les signaux de cycle de vie START/STOP.
 * <p>
 * En LOCAL : appelle {@code LocalAgent.onLifecycleSignal()}.
 * En DISTRIBUTED : appelle {@code transport.broadcastSignal()}.
 * <p>
 * Interface fonctionnelle, 0 annotation framework.
 */
@FunctionalInterface
public interface ExecutionLifecycleDispatcher {
    void dispatch(ExecutionLifecycleSignal signal);
}
