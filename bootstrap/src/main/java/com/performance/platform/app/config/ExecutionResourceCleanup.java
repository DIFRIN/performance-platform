package com.performance.platform.app.config;

import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.plugin.ExecutionCompletionCleaner;
import com.performance.platform.transport.SignalChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ExecutionResourceCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExecutionResourceCleanup.class);
    private final ObjectProvider<ExecutionCompletionCleaner> cleaners;
    private final ObjectProvider<SignalChannel> signals;
    private final String mode;

    public ExecutionResourceCleanup(ObjectProvider<ExecutionCompletionCleaner> cleaners,
                                    ObjectProvider<SignalChannel> signals,
                                    @Value("${runtime.mode:LOCAL}") String mode) {
        this.cleaners = cleaners;
        this.signals = signals;
        this.mode = mode;
    }

    @EventListener
    @Order(100)
    public void onFinished(ScenarioFinished event) {
        if ("DISTRIBUTED".equals(mode)) {
            signals.getObject().broadcastSignal(new ScenarioRestartSignal(SignalId.generate(),
                    event.executionId(), "EXECUTION_FINISHED", Instant.now()));
            return;
        }
        for (var cleaner : cleaners.stream().toList()) {
            try {
                cleaner.cleanup(event.executionId());
            } catch (RuntimeException e) {
                log.error("action=execution_cleanup_failed executionId={} cleaner={}",
                        event.executionId().value(), cleaner.getClass().getName(), e);
            }
        }
    }
}
