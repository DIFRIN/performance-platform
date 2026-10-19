package com.performance.platform.application.ports.out;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;

/**
 * Port sortant vers le composant agent capable de recevoir un signal de
 * cycle de vie (START/STOP) en mode LOCAL.
 * <p>
 * Permet a {@code platform-execution-engine} de dispatcher les signaux
 * sans dependre de {@code platform-agent-runtime} : le port est implemente
 * par {@code LocalAgent} et consomme par {@code LocalLifecycleDispatcher}.
 * <p>
 * 0 annotation framework.
 */
public interface LifecycleSignalHandler {

    /**
     * Recoit un signal de cycle de vie pour une task.
     *
     * @param signal le signal START ou STOP
     */
    void onLifecycleSignal(ExecutionLifecycleSignal signal);
}
