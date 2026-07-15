package com.performance.platform.engine.lifecycle;

import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementation LOCAL du dispatcher de signaux de cycle de vie.
 * Appelle {@code LocalAgent.onLifecycleSignal()} directement (meme JVM, pas de transport).
 *
 * <p>Active uniquement quand {@code runtime.mode=LOCAL}.</p>
 */
@Component
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalLifecycleDispatcher implements ExecutionLifecycleDispatcher {

    private final AgentRuntime localAgentRuntime;

    public LocalLifecycleDispatcher(AgentRuntime localAgentRuntime) {
        this.localAgentRuntime = localAgentRuntime;
    }

    @Override
    public void dispatch(ExecutionLifecycleSignal signal) {
        localAgentRuntime.onLifecycleSignal(signal);
    }
}
