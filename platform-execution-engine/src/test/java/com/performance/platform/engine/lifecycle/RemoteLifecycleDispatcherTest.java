package com.performance.platform.engine.lifecycle;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.LifecycleAction;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.transport.ExecutionTransport;
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

@DisplayName("RemoteLifecycleDispatcher")
@ExtendWith(MockitoExtension.class)
class RemoteLifecycleDispatcherTest {

    @Mock
    private ExecutionTransport transport;

    @InjectMocks
    private RemoteLifecycleDispatcher dispatcher;

    @Test
    @DisplayName("should broadcast START signal via transport")
    void shouldBroadcastStartSignal() {
        var signal = new ExecutionLifecycleSignal(
                SignalId.generate(),
                ExecutionId.generate(),
                TaskId.of("task-1"),
                LifecycleAction.START,
                Map.of("taskName", "test-task"),
                Instant.now()
        );

        dispatcher.dispatch(signal);

        verify(transport).broadcastSignal(signal);
        verifyNoMoreInteractions(transport);
    }

    @Test
    @DisplayName("should broadcast STOP signal via transport")
    void shouldBroadcastStopSignal() {
        var signal = new ExecutionLifecycleSignal(
                SignalId.generate(),
                ExecutionId.generate(),
                TaskId.of("task-1"),
                LifecycleAction.STOP,
                Map.of("taskName", "test-task"),
                Instant.now()
        );

        dispatcher.dispatch(signal);

        verify(transport).broadcastSignal(signal);
        verifyNoMoreInteractions(transport);
    }
}
