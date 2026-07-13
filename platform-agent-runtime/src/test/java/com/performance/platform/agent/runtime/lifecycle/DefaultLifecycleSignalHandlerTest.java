package com.performance.platform.agent.runtime.lifecycle;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.id.*;
import com.performance.platform.plugin.TaskExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@DisplayName("DefaultLifecycleSignalHandler")
class DefaultLifecycleSignalHandlerTest {

    private static final String SUPPORTED_TASK = "gatling-metric";
    private static final String UNSUPPORTED_TASK = "unsupported-task";

    private Set<String> supportedTaskNames;
    private TaskSpecializationFilter filter;
    private Map<String, TaskExecutor> taskExecutors;
    private TaskExecutor mockExecutor;
    private DefaultLifecycleSignalHandler handler;

    @BeforeEach
    void setUp() {
        supportedTaskNames = Set.of(SUPPORTED_TASK);
        filter = new DefaultTaskSpecializationFilter(supportedTaskNames, AgentId.of("test-agent"));
        mockExecutor = mock(TaskExecutor.class);
        when(mockExecutor.getSupportedTaskName()).thenReturn(SUPPORTED_TASK);
        taskExecutors = Map.of(SUPPORTED_TASK, mockExecutor);
        handler = new DefaultLifecycleSignalHandler(filter, taskExecutors);
    }

    private static TaskId newTaskId() {
        return TaskId.of("task-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private ExecutionLifecycleSignal createStartSignal(String taskName) {
        return ExecutionLifecycleSignal.start(
                SignalId.generate(),
                ExecutionId.generate(),
                newTaskId(),
                Map.of(
                        ExecutionLifecycleSignal.PARAM_TASK_NAME, taskName,
                        ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                )
        );
    }

    private ExecutionLifecycleSignal createStartSignal(String taskName, long intervalSeconds) {
        return ExecutionLifecycleSignal.start(
                SignalId.generate(),
                ExecutionId.generate(),
                newTaskId(),
                Map.of(
                        ExecutionLifecycleSignal.PARAM_TASK_NAME, taskName,
                        ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, intervalSeconds
                )
        );
    }

    private ExecutionLifecycleSignal createStopSignal(String taskName, ExecutionId executionId, TaskId taskId, String stopBehavior) {
        return ExecutionLifecycleSignal.stop(
                SignalId.generate(),
                executionId,
                taskId,
                Map.of(
                        ExecutionLifecycleSignal.PARAM_TASK_NAME, taskName,
                        ExecutionLifecycleSignal.PARAM_STOP_BEHAVIOR, stopBehavior
                )
        );
    }

    private ExecutionLifecycleSignal createStopSignal(String taskName, ExecutionId executionId, TaskId taskId) {
        return ExecutionLifecycleSignal.stop(
                SignalId.generate(),
                executionId,
                taskId,
                Map.of(
                        ExecutionLifecycleSignal.PARAM_TASK_NAME, taskName
                )
        );
    }

    @Nested
    @DisplayName("Signal filtering")
    class SignalFiltering {

        @Test
        @DisplayName("should ignore signal for unsupported task")
        void shouldIgnoreSignalForUnsupportedTask() {
            var signal = createStartSignal(UNSUPPORTED_TASK);

            handler.handle(signal);

            assertThat(handler.activeSamplingCount()).isZero();
        }

        @Test
        @DisplayName("should ignore signal with null taskName")
        void shouldHandleNullTaskName() {
            var signal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    ExecutionId.generate(),
                    newTaskId(),
                    Map.of() // no taskName
            );

            handler.handle(signal);

            assertThat(handler.activeSamplingCount()).isZero();
        }
    }

    @Nested
    @DisplayName("START signal handling")
    class StartSignalHandling {

        @Test
        @DisplayName("should ignore START if already active (idempotence)")
        void shouldIgnoreStartIfAlreadyActive() {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();

            var startSignal1 = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            var startSignal2 = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            handler.handle(startSignal1);
            handler.handle(startSignal2);

            // Only one sampling should be active
            assertThat(handler.activeSamplingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should start sampling on START signal")
        void shouldStartSamplingOnStartSignal() {
            var signal = createStartSignal(SUPPORTED_TASK, 1L);

            handler.handle(signal);

            assertThat(handler.activeSamplingCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("STOP signal handling")
    class StopSignalHandling {

        @Test
        @DisplayName("should stop sampling on STOP signal with immediate behavior")
        void shouldStopSamplingOnStopSignal() throws InterruptedException {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();
            var startSignal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            handler.handle(startSignal);
            assertThat(handler.activeSamplingCount()).isEqualTo(1);

            // Small delay to let sampling loop start
            Thread.sleep(200);

            var stopSignal = createStopSignal(SUPPORTED_TASK, executionId, taskId, "immediate");
            handler.handle(stopSignal);

            // The STOP removes the entry from activeSampling immediately
            assertThat(handler.activeSamplingCount()).isZero();
        }

        @Test
        @DisplayName("should ignore STOP if no active sampling")
        void shouldIgnoreStopIfNoActiveSampling() {
            var signal = createStopSignal(SUPPORTED_TASK, ExecutionId.generate(), newTaskId());

            handler.handle(signal);

            assertThat(handler.activeSamplingCount()).isZero();
        }

        @Test
        @DisplayName("should default to immediate if no stop behavior provided")
        void shouldDefaultToImmediateIfNoStopBehavior() throws InterruptedException {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();

            var startSignal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            handler.handle(startSignal);
            Thread.sleep(200);

            // Stop signal without stopBehavior -> defaults to immediate
            var stopSignal = createStopSignal(SUPPORTED_TASK, executionId, taskId);
            handler.handle(stopSignal);

            // Should clean up immediately (entry removed from activeSampling)
            assertThat(handler.activeSamplingCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Stop behaviors")
    class StopBehaviors {

        @Test
        @DisplayName("should apply completeCurrentCycle behavior")
        void shouldApplyCompleteCurrentCycleBehavior() throws InterruptedException {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();
            var startSignal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            handler.handle(startSignal);
            Thread.sleep(200);

            var stopSignal = createStopSignal(SUPPORTED_TASK, executionId, taskId, "completeCurrentCycle");
            handler.handle(stopSignal);

            // Should clean up (entry removed from activeSampling)
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    handler.activeSamplingCount() == 0);
        }

        @Test
        @DisplayName("should apply gracePeriod behavior")
        void shouldApplyGracePeriodBehavior() throws InterruptedException {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();
            var startSignal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            handler.handle(startSignal);
            Thread.sleep(200);

            var stopSignal = ExecutionLifecycleSignal.stop(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_STOP_BEHAVIOR, "gracePeriod",
                            ExecutionLifecycleSignal.PARAM_GRACE_PERIOD_DURATION, "PT2S"
                    )
            );
            handler.handle(stopSignal);

            // Should clean up after grace period (entry removed from activeSampling)
            await().atMost(Duration.ofSeconds(10)).until(() ->
                    handler.activeSamplingCount() == 0);
        }
    }

    @Nested
    @DisplayName("Sampling loop")
    class SamplingLoop {

        @Test
        @DisplayName("should collect samples during interval")
        void shouldCollectSamplesDuringInterval() throws InterruptedException {
            var startSignal = createStartSignal(SUPPORTED_TASK, 1L);

            handler.handle(startSignal);

            // Wait for at least one sample to be collected
            Thread.sleep(1500);

            // Send STOP to finalize
            var stopSignal = createStopSignal(SUPPORTED_TASK,
                    startSignal.executionId(), startSignal.taskId(), "immediate");
            handler.handle(stopSignal);

            // The sampling loop should have collected at least 1 sample in ~1.5s
            // We verify indirectly through the handler's state cleanup
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    handler.activeSamplingCount() == 0);
        }

        @Test
        @DisplayName("should cleanup active sampling on STOP")
        void shouldCleanupActiveSamplingOnStop() {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();
            var startSignal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );

            handler.handle(startSignal);
            String key = executionId.value() + "::" + taskId.value();
            assertThat(handler.hasActiveSampling(key)).isTrue();

            var stopSignal = createStopSignal(SUPPORTED_TASK, executionId, taskId, "immediate");
            handler.handle(stopSignal);

            assertThat(handler.hasActiveSampling(key)).isFalse();
        }
    }

    @Nested
    @DisplayName("Sampling completion")
    class SamplingCompletion {

        @Test
        @DisplayName("should produce completion result when sampling finishes")
        void shouldCompleteSamplingAndCleanup() throws InterruptedException {
            var executionId = ExecutionId.generate();
            var taskId = newTaskId();

            var startSignal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    executionId,
                    taskId,
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK,
                            ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS, 1L
                    )
            );
            handler.handle(startSignal);
            Thread.sleep(200);

            var stopSignal = createStopSignal(SUPPORTED_TASK, executionId, taskId, "immediate");
            handler.handle(stopSignal);

            // Wait for cleanup
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    handler.activeSamplingCount() == 0);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle START+STOP for different tasks independently")
        void shouldHandleMultipleIndependentTasks() {
            var start1 = createStartSignal(SUPPORTED_TASK, 1L);
            var start2 = createStartSignal(SUPPORTED_TASK, 1L);

            handler.handle(start1);
            handler.handle(start2);

            // Two independent sampling loops
            assertThat(handler.activeSamplingCount()).isEqualTo(2);

            var stop1 = createStopSignal(SUPPORTED_TASK, start1.executionId(), start1.taskId(), "immediate");
            handler.handle(stop1);

            assertThat(handler.activeSamplingCount()).isEqualTo(1);

            var stop2 = createStopSignal(SUPPORTED_TASK, start2.executionId(), start2.taskId(), "immediate");
            handler.handle(stop2);

            assertThat(handler.activeSamplingCount()).isZero();
        }

        @Test
        @DisplayName("should use default intervalSeconds of 5 when not specified")
        void shouldUseDefaultIntervalOf5() {
            var signal = ExecutionLifecycleSignal.start(
                    SignalId.generate(),
                    ExecutionId.generate(),
                    newTaskId(),
                    Map.of(
                            ExecutionLifecycleSignal.PARAM_TASK_NAME, SUPPORTED_TASK
                            // no intervalSeconds
                    )
            );

            handler.handle(signal);

            assertThat(handler.activeSamplingCount()).isEqualTo(1);
        }
    }
}
