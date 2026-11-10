package com.performance.platform.app.config;

import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.plugin.ExecutionCompletionCleaner;
import com.performance.platform.transport.SignalChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ExecutionResourceCleanupTest {

    @ParameterizedTest
    @EnumSource(Verdict.class)
    void shouldCleanLocalResourcesForEveryTerminalVerdict(Verdict verdict) {
        var factory = new DefaultListableBeanFactory();
        var cleaner = mock(ExecutionCompletionCleaner.class);
        factory.registerSingleton("cleaner", cleaner);
        var listener = new ExecutionResourceCleanup(factory.getBeanProvider(ExecutionCompletionCleaner.class),
                factory.getBeanProvider(SignalChannel.class), "LOCAL");
        var event = finished(verdict);

        listener.onFinished(event);

        verify(cleaner).cleanup(event.executionId());
    }

    @ParameterizedTest
    @EnumSource(Verdict.class)
    void shouldBroadcastTargetedCleanupWithoutCleaningOrchestratorResources(Verdict verdict) {
        var factory = new DefaultListableBeanFactory();
        var channel = mock(SignalChannel.class);
        var cleaner = mock(ExecutionCompletionCleaner.class);
        factory.registerSingleton("channel", channel);
        factory.registerSingleton("cleaner", cleaner);
        var listener = new ExecutionResourceCleanup(factory.getBeanProvider(ExecutionCompletionCleaner.class),
                factory.getBeanProvider(SignalChannel.class), "DISTRIBUTED");
        var event = finished(verdict);

        listener.onFinished(event);

        verify(channel).broadcastSignal(argThat(signal -> signal instanceof ScenarioRestartSignal restart
                && restart.executionId().equals(event.executionId())
                && restart.reason().equals("EXECUTION_FINISHED")));
        verifyNoInteractions(cleaner);
    }

    private static ScenarioFinished finished(Verdict verdict) {
        return new ScenarioFinished(ExecutionId.generate(), ScenarioId.of("test"), verdict,
                Duration.ZERO, Instant.now());
    }
}
