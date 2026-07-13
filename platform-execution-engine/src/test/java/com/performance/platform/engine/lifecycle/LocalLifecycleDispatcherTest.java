package com.performance.platform.engine.lifecycle;

import com.performance.platform.agent.local.LocalAgent;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.LifecycleAction;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("LocalLifecycleDispatcher")
@ExtendWith(MockitoExtension.class)
class LocalLifecycleDispatcherTest {

    @Mock
    private LocalAgent localAgent;

    @InjectMocks
    private LocalLifecycleDispatcher dispatcher;

    @Test
    @DisplayName("should dispatch START signal to local agent")
    void shouldDispatchStartSignal() {
        var signal = new ExecutionLifecycleSignal(
                SignalId.generate(),
                ExecutionId.generate(),
                TaskId.of("task-1"),
                LifecycleAction.START,
                Map.of("taskName", "test-task"),
                Instant.now()
        );

        dispatcher.dispatch(signal);

        verify(localAgent).onLifecycleSignal(signal);
        verifyNoMoreInteractions(localAgent);
    }

    @Test
    @DisplayName("should dispatch STOP signal to local agent")
    void shouldDispatchStopSignal() {
        var signal = new ExecutionLifecycleSignal(
                SignalId.generate(),
                ExecutionId.generate(),
                TaskId.of("task-1"),
                LifecycleAction.STOP,
                Map.of("taskName", "test-task"),
                Instant.now()
        );

        dispatcher.dispatch(signal);

        verify(localAgent).onLifecycleSignal(signal);
        verifyNoMoreInteractions(localAgent);
    }
}
