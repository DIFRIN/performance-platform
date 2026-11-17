package com.performance.platform.injection.gatling;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.injection.gatling.result.GatlingResultParser;
import com.performance.platform.injection.gatling.runner.GatlingExecutionException;
import com.performance.platform.injection.gatling.runner.GatlingRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatlingTaskExecutor — availability")
class GatlingTaskExecutorAvailabilityTest {

    private static final TaskId TASK_ID = TaskId.of("task-1");
    private static final ExecutionContext CTX =
            ExecutionContext.initial(ExecutionId.of("exec-1"), ScenarioId.of("s-1"));
    private static final InjectionResult RESULT = new InjectionResult(
            TASK_ID, "Sim", Duration.ofSeconds(1), 10, 10, 0, 0.0, 1.0,
            1, 1, 1, 1, 1, 1, 1, 1.0, Path.of("/tmp/x"), Map.of());

    private static StepDefinition step() {
        return new StepDefinition(TASK_ID, "gatling", Phase.INJECTION, Map.of(
                "simulation", "Sim",
                "workload", TestPlans.holdMap()),
                null, null, null, null);
    }

    private static GatlingTaskExecutor executor(GatlingRunner runner, int max) {
        GatlingResultParser parser = (dir, id) -> RESULT;
        return new GatlingTaskExecutor(com.performance.platform.injection.gatling.worker.RunnerBackedLauncher.of(runner), parser, List.of(), max);
    }

    @Test
    @DisplayName("second concurrent injection is refused with max-concurrent=1, counter returns to 0")
    void refusesSecondConcurrent() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        GatlingRunner blocking = config -> {
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Path.of("/tmp/r");
        };
        var ex = executor(blocking, 1);
        assertThat(ex.canAcceptNewTask()).isTrue();

        var first = Thread.ofVirtual().start(() -> ex.execute(CTX, step()));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(ex.canAcceptNewTask()).isFalse();
        assertThat(ex.execute(CTX, step()).status()).isEqualTo(TaskStatus.FAILED);

        release.countDown();
        first.join();
        assertThat(ex.canAcceptNewTask()).isTrue();
    }

    @Test
    @DisplayName("counter returns to 0 after success and after failure")
    void counterResetAfterSuccessAndFailure() {
        var ok = executor(c -> Path.of("/tmp/r"), 1);
        assertThat(ok.execute(CTX, step()).status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(ok.canAcceptNewTask()).isTrue();

        var ko = executor(c -> { throw new GatlingExecutionException("timeout"); }, 1);
        assertThat(ko.execute(CTX, step()).status()).isEqualTo(TaskStatus.FAILED);
        assertThat(ko.canAcceptNewTask()).isTrue();
    }
}
