package com.performance.platform.assertion.performance;

import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.performance.platform.domain.injection.GeneratorHealth;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.LoadWindow;

import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerformanceThresholdsAssertionExecutor")
class PerformanceThresholdsAssertionExecutorTest {

    private static final TaskId LOAD = TaskId.of("load");
    private static final ExecutionId EXEC = ExecutionId.of("exec-1");
    private static final ScenarioId SCENARIO = ScenarioId.of("scenario-1");

    private final PerformanceThresholdsAssertionExecutor executor =
            new PerformanceThresholdsAssertionExecutor();

    @Test
    @DisplayName("expands latency, error rate and throughput into normalised checks")
    void expandsThresholds() {
        var step = step("api-slo", Map.of(
                "refTaskId", "load",
                "latency", Map.of("p50", 200, "p95", 500, "p99", 900,
                        "unit", "ms", "severity", "WARNING"),
                "errorRate", Map.of("max", 1.0, "severity", "CRITICAL"),
                "throughput", Map.of("min", 100, "severity", "WARNING"),
                "description", Map.of("title", "API latency",
                        "sli", "p95 of POST /orders",
                        "slo", "p95 <= 500 ms", "rationale", "customer contract")));

        TaskResult result = executor.execute(context(), step);
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(summary.checks()).hasSize(5);
        assertThat(summary.checks()).extracting(check -> check.metric().toString())
                .contains("injection.latency.p50", "injection.latency.p95",
                        "injection.latency.p99", "injection.error.rate",
                        "injection.throughput");
        assertThat(summary.checks()).allMatch(check -> check.status() == AssertionStatus.PASSED);
        assertThat(summary.slo().slo()).contains("500");
        assertThat(summary.family())
                .isEqualTo(com.performance.platform.domain.assertion.AssertionFamily.PERFORMANCE_THRESHOLDS);
    }

    @Test
    @DisplayName("a violated critical threshold fails the execution result")
    void failingThreshold() {
        var step = step("api-slo", Map.of(
                "refTaskId", "load",
                "latency", Map.of("p95", 100, "severity", "CRITICAL")));

        TaskResult result = executor.execute(context(), step);
        AssertionSummary summary = (AssertionSummary) result.outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(summary.verdict()).isEqualTo(AssertionStatus.FAILED);
        assertThat(summary.checks()).anyMatch(check -> check.status() == AssertionStatus.FAILED);
    }

    @Test
    @DisplayName("unconstrained percentiles are SKIPPED and documented")
    void unconstrainedPercentilesAreSkipped() {
        var step = step("api-slo", Map.of(
                "refTaskId", "load",
                "latency", Map.of("p95", 500)));

        AssertionSummary summary = (AssertionSummary) executor.execute(context(), step)
                .outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(summary.checks()).filteredOn(check -> check.status() == AssertionStatus.SKIPPED)
                .hasSize(2);
    }

    @Test
    @DisplayName("EQ on a quantile without tolerance is rejected as an error check")
    void equalityRequiresTolerance() {
        var step = step("api-slo", Map.of(
                "refTaskId", "load",
                "checks", List.of(Map.of("metric", "p95", "operator", "EQ", "threshold", 500))));

        AssertionSummary summary = (AssertionSummary) executor.execute(context(), step)
                .outputs().get(AssertionSummary.OUTPUT_KEY);

        assertThat(summary.checks()).anyMatch(check -> check.status() == AssertionStatus.ERROR);
    }

    @Test
    @DisplayName("multi-agent: p95 comes from merged histogram, not from the worst agent")
    void multiAgentUsesMerge() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        ExecutionContext ctx = ExecutionContext.initial(EXEC, SCENARIO);
        LatencyHistogram all = LatencyHistogram.empty();
        long[] lat = {10, 20, 300};
        for (int i = 0; i < lat.length; i++) {
            LatencyHistogram h = LatencyHistogram.empty();
            for (int n = 0; n < 100; n++) {
                h = h.with(lat[i], true);
            }
            all = all.merge(h);
            InjectionResult r = new InjectionResult(LOAD, "Sim", Duration.ofSeconds(10), 100, 100, 0, 0.0,
                    10.0, lat[i], lat[i], lat[i], lat[i], lat[i], lat[i], 1, 1.0, Path.of("."), Map.of(),
                    t0, t0.plusSeconds(10), h,
                    List.of(new LoadWindow(t0, Duration.ofSeconds(10), 100, 100, 100, 0, true)),
                    new GeneratorHealth(0.5, Duration.ofMillis(10), 1), true);
            ctx = ctx.with("load", "agent-" + i,
                    TaskResult.success(LOAD, "gatling", Duration.ofSeconds(10),
                            Map.of(InjectionResult.OUTPUT_KEY, r)));
        }
        var step = step("slo", Map.of("refTaskId", "load",
                "checks", List.of(Map.of("metric", "injection.latency.p95", "operator", "EQ",
                        "threshold", (double) all.quantile(0.95), "tolerance", 0.001),
                        Map.of("metric", "injection.throughput", "operator", "EQ",
                                "threshold", 30.0, "tolerance", 0.001))));
        AssertionSummary summary = (AssertionSummary) executor.execute(ctx, step)
                .outputs().get(AssertionSummary.OUTPUT_KEY);
        assertThat(summary.checks()).hasSize(2);
        assertThat(summary.checks()).allMatch(c -> c.status() == AssertionStatus.PASSED);
    }

    @Test
    @DisplayName("single agent: results unchanged (non-regression)")
    void singleAgentUnchanged() {
        var step = step("slo", Map.of("refTaskId", "load",
                "checks", List.of(Map.of("metric", "injection.latency.p95", "operator", "EQ",
                        "threshold", 412, "tolerance", 0.001))));
        AssertionSummary summary = (AssertionSummary) executor.execute(context(), step)
                .outputs().get(AssertionSummary.OUTPUT_KEY);
        assertThat(summary.checks()).singleElement()
                .matches(c -> c.status() == AssertionStatus.PASSED);
    }

    // ------------------------------------------------------------------

    private static ExecutionContext context() {
        InjectionResult injection = new InjectionResult(
                LOAD, "sim.Simulation", Duration.ofMinutes(2),
                1_200, 1_196, 4, 0.33, 118.4,
                120, 180, 320, 412, 884, 1_200, 40, 210.5,
                Path.of("reports/gatling"), Map.of());
        TaskResult loadResult = TaskResult.success(LOAD, "gatling", Duration.ofMinutes(2),
                Map.of(InjectionResult.OUTPUT_KEY, injection));
        return ExecutionContext.initial(EXEC, SCENARIO).with("load", "agent-1", loadResult);
    }

    private static StepDefinition step(String id, Map<String, Object> parameters) {
        return new StepDefinition(TaskId.of(id), "performance-thresholds", Phase.ASSERTION,
                parameters, List.of(), List.of("load"), Duration.ofSeconds(30), null);
    }
}
