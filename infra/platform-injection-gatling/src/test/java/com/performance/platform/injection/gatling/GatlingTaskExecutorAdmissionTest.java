package com.performance.platform.injection.gatling;

import static org.assertj.core.api.Assertions.assertThat;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.domain.injection.WorkloadPlanCodec;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.injection.gatling.worker.RunnerBackedLauncher;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GatlingTaskExecutorAdmissionTest {

    private static final TaskId TASK_ID = TaskId.of("task-1");
    private static final ExecutionContext CTX =
            ExecutionContext.initial(ExecutionId.of("exec-1"), ScenarioId.of("s-1"));
    private static final InjectionResult RESULT = new InjectionResult(
            TASK_ID, "Sim", Duration.ofSeconds(1), 10, 10, 0, 0.0, 1.0,
            1, 1, 1, 1, 1, 1, 1, 1.0, Path.of("/tmp/x"), Map.of());

    private static StepDefinition step(LoadScope scope) {
        var plan = new WorkloadPlan(ArrivalModel.OPEN, scope, List.of(new Hold(5, Duration.ofSeconds(10), true)));
        return new StepDefinition(TASK_ID, "gatling", Phase.INJECTION,
                Map.of("simulation", "Sim", "workload", WorkloadPlanCodec.toMap(plan)), null, null, null, null);
    }

    @Test
    void globalScopeScaledToShareBeforeWorker() {
        var runs = new AtomicInteger();
        var ex = new GatlingTaskExecutor(RunnerBackedLauncher.of(c -> {
            runs.incrementAndGet();
            return Path.of("/tmp/r");
        }), (d, id) -> RESULT, List.of());
        var ctx = CTX.withShare(com.performance.platform.domain.execution.GrantShare.equal(1, 5));
        var result = ex.execute(ctx, step(LoadScope.GLOBAL));
        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(runs).hasValue(1);
        assertThat(result.outputs()).containsEntry("shareIndex", 1).containsEntry("shareCount", 5);
        @SuppressWarnings("unchecked")
        var scaled = WorkloadPlanCodec.fromMap((Map<String, Object>) result.outputs().get("workloadPlan"));
        assertThat(((Hold) scaled.stages().get(0)).level()).isEqualTo(1.0);
    }

    @Test
    void zeroShareRunsNoWorker() {
        var runs = new AtomicInteger();
        var ex = new GatlingTaskExecutor(RunnerBackedLauncher.of(c -> {
            runs.incrementAndGet();
            return Path.of("/tmp/r");
        }), (d, id) -> RESULT, List.of());
        var plan = new WorkloadPlan(ArrivalModel.OPEN, LoadScope.GLOBAL,
                List.of(new com.performance.platform.domain.injection.Burst(1, true)));
        var step = new StepDefinition(TASK_ID, "gatling", Phase.INJECTION,
                Map.of("simulation", "Sim", "workload", WorkloadPlanCodec.toMap(plan)), null, null, null, null);
        var ctx = CTX.withShare(com.performance.platform.domain.execution.GrantShare.equal(2, 3));
        var result = ex.execute(ctx, step);
        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(runs).hasValue(0);
        assertThat(result.outputs()).containsEntry("complete", true);
    }

    @Test
    void customSimulationIsOpaque() {
        var ex = new GatlingTaskExecutor(RunnerBackedLauncher.of(c -> Path.of("/tmp/r")),
                (d, id) -> RESULT, List.of());
        var result = ex.execute(CTX, step(LoadScope.PER_AGENT));
        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(result.outputs()).containsEntry("opaque", true);
    }

    @Test
    void declaresCapabilities() {
        var caps = new GatlingTaskExecutor(RunnerBackedLauncher.of(c -> Path.of("/tmp/r")),
                (d, id) -> RESULT, List.of()).injectionCapabilities();
        assertThat(caps.supportsGlobalScope()).isTrue();
        assertThat(caps.stageKinds()).containsExactlyInAnyOrder("ramp", "hold", "burst", "pause");
    }
}
