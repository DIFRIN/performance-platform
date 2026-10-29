package com.performance.platform.observability.metrics;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MicrometerExecutionMetrics")
class MicrometerExecutionMetricsTest {

    private MeterRegistry registry;
    private MicrometerExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerExecutionMetrics(registry);
    }

    // ---- recordExecutionDuration ----

    @Nested
    @DisplayName("recordExecutionDuration")
    class RecordExecutionDurationTests {

        @Test
        @DisplayName("should record execution duration timer")
        void shouldRecordExecutionDuration() {
            var execId = new ExecutionId("exec-001");

            metrics.recordExecutionDuration(execId, Duration.ofSeconds(30));

            Timer timer = registry.find(MicrometerExecutionMetrics.METRIC_EXECUTION_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_EXECUTION_ID, "exec-001")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isGreaterThanOrEqualTo(30_000);
        }

        @Test
        @DisplayName("should allow multiple recordings on same execution")
        void shouldAllowMultipleRecordings() {
            var execId = new ExecutionId("exec-001");

            metrics.recordExecutionDuration(execId, Duration.ofSeconds(10));
            metrics.recordExecutionDuration(execId, Duration.ofSeconds(20));

            Timer timer = registry.find(MicrometerExecutionMetrics.METRIC_EXECUTION_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_EXECUTION_ID, "exec-001")
                    .timer();
            assertThat(timer.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw NPE on null executionId")
        void shouldThrowOnNullExecutionId() {
            assertThatThrownBy(() -> metrics.recordExecutionDuration(null, Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE on null duration")
        void shouldThrowOnNullDuration() {
            assertThatThrownBy(() -> metrics.recordExecutionDuration(
                    new ExecutionId("exec-001"), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- recordTaskDuration ----

    @Nested
    @DisplayName("recordTaskDuration")
    class RecordTaskDurationTests {

        @Test
        @DisplayName("should record task duration with taskName tag")
        void shouldRecordTaskDuration() {
            var taskId = new TaskId("task-001");

            metrics.recordTaskDuration(taskId, "gatling", Duration.ofMillis(500));

            Timer timer = registry.find(MicrometerExecutionMetrics.METRIC_TASK_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_TASK_NAME, "gatling")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should differentiate tasks by taskName tag")
        void shouldDifferentiateByTaskName() {
            var t1 = new TaskId("task-001");
            var t2 = new TaskId("task-002");

            metrics.recordTaskDuration(t1, "gatling", Duration.ofMillis(100));
            metrics.recordTaskDuration(t2, "shell", Duration.ofMillis(200));

            Timer gatlingTimer = registry.find(MicrometerExecutionMetrics.METRIC_TASK_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_TASK_NAME, "gatling")
                    .timer();
            Timer shellTimer = registry.find(MicrometerExecutionMetrics.METRIC_TASK_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_TASK_NAME, "shell")
                    .timer();

            assertThat(gatlingTimer.count()).isEqualTo(1);
            assertThat(shellTimer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw NPE on null taskId")
        void shouldThrowOnNullTaskId() {
            assertThatThrownBy(() -> metrics.recordTaskDuration(
                    null, "gatling", Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE on null taskName")
        void shouldThrowOnNullTaskName() {
            assertThatThrownBy(() -> metrics.recordTaskDuration(
                    new TaskId("task-001"), null, Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE on null duration")
        void shouldThrowOnNullDurationInTask() {
            assertThatThrownBy(() -> metrics.recordTaskDuration(
                    new TaskId("task-001"), "gatling", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- incrementTaskFailure ----

    @Nested
    @DisplayName("incrementTaskFailure")
    class IncrementTaskFailureTests {

        @Test
        @DisplayName("should increment failure counter")
        void shouldIncrementFailureCounter() {
            var taskId = new TaskId("task-001");

            metrics.incrementTaskFailure(taskId, "gatling");
            metrics.incrementTaskFailure(taskId, "gatling");
            metrics.incrementTaskFailure(taskId, "gatling");

            double count = registry.find(MicrometerExecutionMetrics.METRIC_TASK_FAILURES_TOTAL)
                    .tag(MicrometerExecutionMetrics.TAG_TASK_NAME, "gatling")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(3.0);
        }

        @Test
        @DisplayName("should differentiate failure counters by taskName")
        void shouldDifferentiateFailuresByTaskName() {
            metrics.incrementTaskFailure(new TaskId("task-001"), "gatling");
            metrics.incrementTaskFailure(new TaskId("task-002"), "shell");
            metrics.incrementTaskFailure(new TaskId("task-003"), "shell");

            double gatlingCount = registry.find(MicrometerExecutionMetrics.METRIC_TASK_FAILURES_TOTAL)
                    .tag(MicrometerExecutionMetrics.TAG_TASK_NAME, "gatling")
                    .counter()
                    .count();
            double shellCount = registry.find(MicrometerExecutionMetrics.METRIC_TASK_FAILURES_TOTAL)
                    .tag(MicrometerExecutionMetrics.TAG_TASK_NAME, "shell")
                    .counter()
                    .count();

            assertThat(gatlingCount).isEqualTo(1.0);
            assertThat(shellCount).isEqualTo(2.0);
        }

        @Test
        @DisplayName("should throw NPE on null taskId")
        void shouldThrowOnNullTaskIdForFailure() {
            assertThatThrownBy(() -> metrics.incrementTaskFailure(null, "gatling"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE on null taskName")
        void shouldThrowOnNullTaskNameForFailure() {
            assertThatThrownBy(() -> metrics.incrementTaskFailure(
                    new TaskId("task-001"), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- recordPhaseDuration ----

    @Nested
    @DisplayName("recordPhaseDuration")
    class RecordPhaseDurationTests {

        @Test
        @DisplayName("should record PREPARATION phase duration")
        void shouldRecordPreparationPhase() {
            metrics.recordPhaseDuration(Phase.PREPARATION, Duration.ofSeconds(5));

            Timer timer = registry.find(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_PHASE, "PREPARATION")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record INJECTION phase duration")
        void shouldRecordInjectionPhase() {
            metrics.recordPhaseDuration(Phase.INJECTION, Duration.ofSeconds(60));

            Timer timer = registry.find(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_PHASE, "INJECTION")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record ASSERTION phase duration")
        void shouldRecordAssertionPhase() {
            metrics.recordPhaseDuration(Phase.ASSERTION, Duration.ofSeconds(10));

            Timer timer = registry.find(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_PHASE, "ASSERTION")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should differentiate phases by tag")
        void shouldDifferentiatePhases() {
            metrics.recordPhaseDuration(Phase.PREPARATION, Duration.ofSeconds(5));
            metrics.recordPhaseDuration(Phase.INJECTION, Duration.ofSeconds(30));
            metrics.recordPhaseDuration(Phase.ASSERTION, Duration.ofSeconds(10));

            Timer prepTimer = registry.find(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_PHASE, "PREPARATION").timer();
            Timer injTimer = registry.find(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_PHASE, "INJECTION").timer();
            Timer assertTimer = registry.find(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                    .tag(MicrometerExecutionMetrics.TAG_PHASE, "ASSERTION").timer();

            assertThat(prepTimer.count()).isEqualTo(1);
            assertThat(injTimer.count()).isEqualTo(1);
            assertThat(assertTimer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw NPE on null phase")
        void shouldThrowOnNullPhase() {
            assertThatThrownBy(() -> metrics.recordPhaseDuration(null, Duration.ofSeconds(1)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE on null duration")
        void shouldThrowOnNullPhaseDuration() {
            assertThatThrownBy(() -> metrics.recordPhaseDuration(
                    Phase.INJECTION, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- Constructor ----

    @Test
    @DisplayName("should throw NPE on null MeterRegistry")
    void shouldThrowOnNullRegistry() {
        assertThatThrownBy(() -> new MicrometerExecutionMetrics(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- @Component present ----

    @Test
    @DisplayName("should be annotated with @Component")
    void shouldBeAnnotatedWithComponent() {
        var annotation = MicrometerExecutionMetrics.class.getAnnotation(
                org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    // ---- Constants values ----

    @Test
    @DisplayName("should use correct metric names")
    void shouldHaveCorrectMetricNames() {
        assertThat(MicrometerExecutionMetrics.METRIC_EXECUTION_DURATION)
                .isEqualTo("execution_duration");
        assertThat(MicrometerExecutionMetrics.METRIC_TASK_DURATION)
                .isEqualTo("task_duration");
        assertThat(MicrometerExecutionMetrics.METRIC_TASK_FAILURES_TOTAL)
                .isEqualTo("task_failures_total");
        assertThat(MicrometerExecutionMetrics.METRIC_PHASE_DURATION)
                .isEqualTo("phase_duration");
    }

    // ---- No-op safety ----

    @Test
    @DisplayName("should not throw when recording zero duration")
    void shouldHandleZeroDuration() {
        assertThatCode(() -> metrics.recordExecutionDuration(
                new ExecutionId("exec-001"), Duration.ZERO))
                .doesNotThrowAnyException();
    }
}
