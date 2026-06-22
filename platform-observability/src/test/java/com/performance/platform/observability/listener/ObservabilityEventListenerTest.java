package com.performance.platform.observability.listener;

import com.performance.platform.domain.event.PhaseCompleted;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskFailed;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.observability.metrics.ExecutionMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ObservabilityEventListener")
class ObservabilityEventListenerTest {

    private ObservabilityEventListener listener;
    private FakeExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new FakeExecutionMetrics();
        listener = new ObservabilityEventListener(metrics);
    }

    // ---- on(TaskCompleted) ----

    @Nested
    @DisplayName("on(TaskCompleted)")
    class OnTaskCompletedTests {

        @Test
        @DisplayName("should record task duration with taskName from result")
        void shouldRecordTaskDuration() {
            var executionId = new ExecutionId("exec-001");
            var taskId = new TaskId("task-001");
            var agentId = new AgentId("agent-001");
            var duration = Duration.ofMillis(500);
            var result = TaskResult.success(taskId, "gatling", duration,
                    Map.of("requests", 100));

            var event = new TaskCompleted(executionId, taskId, agentId, result,
                    duration, Instant.now());

            listener.on(event);

            assertThat(metrics.recordTaskDurationCalled).isTrue();
            assertThat(metrics.capturedTaskId).isEqualTo(taskId);
            assertThat(metrics.capturedTaskName).isEqualTo("gatling");
            assertThat(metrics.capturedTaskDuration).isEqualTo(duration);
        }

        @Test
        @DisplayName("should handle task with different taskNames")
        void shouldRecordDifferentTaskNames() {
            var executionId = new ExecutionId("exec-001");
            var t1 = new TaskId("task-001");
            var t2 = new TaskId("task-002");
            var duration = Duration.ofMillis(100);
            var r1 = TaskResult.success(t1, "shell", duration, Map.of());
            var r2 = TaskResult.success(t2, "kafka-consumer", duration, Map.of());

            var event1 = new TaskCompleted(executionId, t1, new AgentId("agent-001"),
                    r1, duration, Instant.now());
            var event2 = new TaskCompleted(executionId, t2, new AgentId("agent-001"),
                    r2, duration, Instant.now());

            listener.on(event1);
            assertThat(metrics.capturedTaskName).isEqualTo("shell");

            listener.on(event2);
            assertThat(metrics.capturedTaskName).isEqualTo("kafka-consumer");
        }

        @Test
        @DisplayName("should throw NPE on null event")
        void shouldThrowOnNullEvent() {
            assertThatThrownBy(() -> listener.on((TaskCompleted) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- on(TaskFailed) ----

    @Nested
    @DisplayName("on(TaskFailed)")
    class OnTaskFailedTests {

        @Test
        @DisplayName("should increment failure counter with unknown taskName")
        void shouldIncrementFailureCounter() {
            var executionId = new ExecutionId("exec-001");
            var taskId = new TaskId("task-001");
            var agentId = new AgentId("agent-001");

            var event = new TaskFailed(executionId, taskId, agentId,
                    "Connection timeout", 3, Instant.now());

            listener.on(event);

            assertThat(metrics.incrementTaskFailureCalled).isTrue();
            assertThat(metrics.capturedTaskId).isEqualTo(taskId);
            assertThat(metrics.capturedTaskName).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should handle multiple failures for same task")
        void shouldHandleMultipleFailures() {
            var executionId = new ExecutionId("exec-001");
            var taskId = new TaskId("task-001");
            var agentId = new AgentId("agent-001");

            var event1 = new TaskFailed(executionId, taskId, agentId,
                    "Timeout", 1, Instant.now());
            var event2 = new TaskFailed(executionId, taskId, agentId,
                    "Timeout", 2, Instant.now());

            listener.on(event1);
            listener.on(event2);

            assertThat(metrics.incrementTaskFailureCalled).isTrue();
            assertThat(metrics.capturedTaskId).isEqualTo(taskId);
            assertThat(metrics.capturedTaskName).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should throw NPE on null event")
        void shouldThrowOnNullEvent() {
            assertThatThrownBy(() -> listener.on((TaskFailed) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- on(PhaseCompleted) ----

    @Nested
    @DisplayName("on(PhaseCompleted)")
    class OnPhaseCompletedTests {

        @Test
        @DisplayName("should record phase duration with ZERO for PREPARATION")
        void shouldRecordPreparationPhase() {
            var executionId = new ExecutionId("exec-001");

            var event = new PhaseCompleted(executionId, Phase.PREPARATION,
                    PhaseStatus.COMPLETED, Instant.now());

            listener.on(event);

            assertThat(metrics.recordPhaseDurationCalled).isTrue();
            assertThat(metrics.capturedPhase).isEqualTo(Phase.PREPARATION);
            assertThat(metrics.capturedPhaseDuration).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should record phase duration with ZERO for INJECTION")
        void shouldRecordInjectionPhase() {
            var executionId = new ExecutionId("exec-001");

            var event = new PhaseCompleted(executionId, Phase.INJECTION,
                    PhaseStatus.COMPLETED, Instant.now());

            listener.on(event);

            assertThat(metrics.recordPhaseDurationCalled).isTrue();
            assertThat(metrics.capturedPhase).isEqualTo(Phase.INJECTION);
            assertThat(metrics.capturedPhaseDuration).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should record phase duration with ZERO for ASSERTION")
        void shouldRecordAssertionPhase() {
            var executionId = new ExecutionId("exec-001");

            var event = new PhaseCompleted(executionId, Phase.ASSERTION,
                    PhaseStatus.FAILED, Instant.now());

            listener.on(event);

            assertThat(metrics.recordPhaseDurationCalled).isTrue();
            assertThat(metrics.capturedPhase).isEqualTo(Phase.ASSERTION);
            assertThat(metrics.capturedPhaseDuration).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("should throw NPE on null event")
        void shouldThrowOnNullEvent() {
            assertThatThrownBy(() -> listener.on((PhaseCompleted) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- on(ScenarioFinished) ----

    @Nested
    @DisplayName("on(ScenarioFinished)")
    class OnScenarioFinishedTests {

        @Test
        @DisplayName("should record execution duration")
        void shouldRecordExecutionDuration() {
            var executionId = new ExecutionId("exec-001");
            var scenarioId = new ScenarioId("scenario-001");
            var duration = Duration.ofSeconds(120);

            var event = new ScenarioFinished(executionId, scenarioId,
                    Verdict.SUCCESS, duration, Instant.now());

            listener.on(event);

            assertThat(metrics.recordExecutionDurationCalled).isTrue();
            assertThat(metrics.capturedExecutionId).isEqualTo(executionId);
            assertThat(metrics.capturedExecutionDuration).isEqualTo(duration);
        }

        @Test
        @DisplayName("should record execution duration for FAILED verdict")
        void shouldRecordForFailedVerdict() {
            var executionId = new ExecutionId("exec-002");
            var scenarioId = new ScenarioId("scenario-002");
            var duration = Duration.ofSeconds(30);

            var event = new ScenarioFinished(executionId, scenarioId,
                    Verdict.FAILED, duration, Instant.now());

            listener.on(event);

            assertThat(metrics.recordExecutionDurationCalled).isTrue();
            assertThat(metrics.capturedExecutionId).isEqualTo(executionId);
            assertThat(metrics.capturedExecutionDuration).isEqualTo(duration);
        }

        @Test
        @DisplayName("should throw NPE on null event")
        void shouldThrowOnNullEvent() {
            assertThatThrownBy(() -> listener.on((ScenarioFinished) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- Constructor ----

    @Test
    @DisplayName("should throw NPE on null metrics")
    void shouldThrowOnNullMetrics() {
        assertThatThrownBy(() -> new ObservabilityEventListener(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- @Component present ----

    @Test
    @DisplayName("should be annotated with @Component")
    void shouldBeAnnotatedWithComponent() {
        var annotation = ObservabilityEventListener.class.getAnnotation(
                org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    // ---- Fake ExecutionMetrics for test verification ----

    /**
     * Fake implementation that records method calls for test verification.
     * Package-private for use by nested test classes.
     */
    static class FakeExecutionMetrics implements ExecutionMetrics {

        boolean recordExecutionDurationCalled;
        ExecutionId capturedExecutionId;
        Duration capturedExecutionDuration;

        boolean recordTaskDurationCalled;
        TaskId capturedTaskId;
        String capturedTaskName;
        Duration capturedTaskDuration;

        boolean incrementTaskFailureCalled;

        boolean recordPhaseDurationCalled;
        Phase capturedPhase;
        Duration capturedPhaseDuration;

        @Override
        public void recordExecutionDuration(ExecutionId executionId, Duration duration) {
            this.recordExecutionDurationCalled = true;
            this.capturedExecutionId = executionId;
            this.capturedExecutionDuration = duration;
        }

        @Override
        public void recordTaskDuration(TaskId taskId, String taskName, Duration duration) {
            this.recordTaskDurationCalled = true;
            this.capturedTaskId = taskId;
            this.capturedTaskName = taskName;
            this.capturedTaskDuration = duration;
        }

        @Override
        public void incrementTaskFailure(TaskId taskId, String taskName) {
            this.incrementTaskFailureCalled = true;
            this.capturedTaskId = taskId;
            this.capturedTaskName = taskName;
        }

        @Override
        public void recordPhaseDuration(Phase phase, Duration duration) {
            this.recordPhaseDurationCalled = true;
            this.capturedPhase = phase;
            this.capturedPhaseDuration = duration;
        }
    }
}
