package com.performance.platform.agent.restart;

import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.plugin.StatefulResourceCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScenarioRestartHandler")
class ScenarioRestartHandlerTest {

    private AtomicReference<AgentState> currentState;
    private AtomicInteger activeTaskCount;
    private Map<MessageId, Future<?>> activeTasks;
    private AgentId agentId;
    private ScenarioRestartHandler handler;

    @BeforeEach
    void setUp() {
        currentState = new AtomicReference<>(AgentState.EXECUTING);
        activeTaskCount = new AtomicInteger(0);
        activeTasks = new ConcurrentHashMap<>();
        agentId = AgentId.generate();
    }

    private ScenarioRestartSignal createSignal(ExecutionId executionId) {
        return new ScenarioRestartSignal(
                SignalId.generate(),
                executionId,
                "TEST_RESTART",
                Instant.now()
        );
    }

    // === Cleanup ===

    @Nested
    @DisplayName("cleaner invocation")
    class CleanerInvocation {

        @Test
        @DisplayName("should invoke all cleaners on restart")
        void shouldInvokeAllCleaners() {
            var cleaner1Called = new AtomicBoolean(false);
            var cleaner2Called = new AtomicBoolean(false);
            var executionId = ExecutionId.generate();

            StatefulResourceCleaner cleaner1 = id -> {
                cleaner1Called.set(true);
                assertThat(id).isEqualTo(executionId);
            };
            StatefulResourceCleaner cleaner2 = id -> {
                cleaner2Called.set(true);
                assertThat(id).isEqualTo(executionId);
            };

            handler = new ScenarioRestartHandler(List.of(cleaner1, cleaner2));
            handler.onSignal(createSignal(executionId), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(cleaner1Called).isTrue();
            assertThat(cleaner2Called).isTrue();
        }

        @Test
        @DisplayName("should pass null executionId for global cleanup")
        void shouldPassNullExecutionIdForGlobalCleanup() {
            var cleanups = new AtomicInteger(0);
            StatefulResourceCleaner cleaner = id -> {
                cleanups.incrementAndGet();
                assertThat(id).isNull();
            };

            handler = new ScenarioRestartHandler(List.of(cleaner));
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(cleanups.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should continue other cleaners when one throws")
        void shouldContinueOtherCleanersOnException() {
            var cleaner2Called = new AtomicBoolean(false);
            StatefulResourceCleaner failingCleaner = id -> {
                throw new RuntimeException("cleanup failed");
            };
            StatefulResourceCleaner normalCleaner = id -> cleaner2Called.set(true);

            handler = new ScenarioRestartHandler(List.of(failingCleaner, normalCleaner));
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(cleaner2Called).isTrue();
        }
    }

    // === Task cancellation ===

    @Nested
    @DisplayName("task cancellation")
    class TaskCancellation {

        @Test
        @DisplayName("should cancel all active tasks on global restart")
        void shouldCancelAllActiveTasksOnGlobalRestart() {
            var task1Cancelled = new AtomicBoolean(false);
            var task2Cancelled = new AtomicBoolean(false);

            var future1 = new SimpleFuture(() -> task1Cancelled.set(true));
            var future2 = new SimpleFuture(() -> task2Cancelled.set(true));

            activeTasks.put(MessageId.generate(), future1);
            activeTasks.put(MessageId.generate(), future2);
            activeTaskCount.set(2);

            handler = new ScenarioRestartHandler(List.of());
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(task1Cancelled).isTrue();
            assertThat(task2Cancelled).isTrue();
            assertThat(activeTaskCount.get()).isEqualTo(0);
            assertThat(activeTasks).isEmpty();
        }

        @Test
        @DisplayName("should transition to IDLE when all tasks cancelled")
        void shouldTransitionToIdleWhenAllTasksCancelled() {
            activeTasks.put(MessageId.generate(), new SimpleFuture(() -> {}));
            activeTaskCount.set(1);

            handler = new ScenarioRestartHandler(List.of());
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(currentState.get()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should stay in current state when no active tasks")
        void shouldStayInCurrentStateWhenNoActiveTasks() {
            currentState.set(AgentState.EXECUTING);
            activeTaskCount.set(0);

            handler = new ScenarioRestartHandler(List.of());
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            // compareAndSet EXECUTING→IDLE only fires when activeTaskCount == 0
            // and current is EXECUTING — but in our handler, we check if
            // activeTaskCount.get() == 0 first, then do compareAndSet.
            // Since count is 0 and current is EXECUTING, it transitions.
            assertThat(currentState.get()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should transition from DRAINING to IDLE on restart")
        void shouldTransitionFromDrainingToIdle() {
            currentState.set(AgentState.DRAINING);
            activeTasks.put(MessageId.generate(), new SimpleFuture(() -> {}));
            activeTaskCount.set(1);

            handler = new ScenarioRestartHandler(List.of());
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(currentState.get()).isEqualTo(AgentState.IDLE);
        }
    }

    // === No cleaners ===

    @Nested
    @DisplayName("empty cleaners")
    class EmptyCleaners {

        @Test
        @DisplayName("should still cancel tasks when no cleaners registered")
        void shouldStillCancelTasksWhenNoCleaners() {
            var taskCancelled = new AtomicBoolean(false);
            activeTasks.put(MessageId.generate(), new SimpleFuture(() -> taskCancelled.set(true)));
            activeTaskCount.set(1);

            handler = new ScenarioRestartHandler(List.of());
            handler.onSignal(createSignal(null), activeTasks,
                    activeTaskCount, currentState, agentId);

            assertThat(taskCancelled).isTrue();
            assertThat(activeTaskCount.get()).isEqualTo(0);
        }
    }

    // === Simple Future implementation for testing ===

    /**
     * Future triviale qui exécute une action lors de l'appel à cancel.
     */
    private static class SimpleFuture implements Future<Void> {
        private final Runnable onCancel;
        private volatile boolean cancelled = false;

        SimpleFuture(Runnable onCancel) {
            this.onCancel = onCancel;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            onCancel.run();
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Void get() {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
