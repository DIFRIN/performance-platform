package com.performance.platform.injection.gatling;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.injection.gatling.result.GatlingResultParser;
import com.performance.platform.injection.gatling.result.ResultParsingException;
import com.performance.platform.injection.gatling.runner.GatlingExecutionException;
import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import com.performance.platform.injection.gatling.runner.GatlingRunner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatlingTaskExecutor")
class GatlingTaskExecutorTest {

    // ── Stubs (manuels — pas de Mockito pour compatibilité Java 25) ───────────

    /** Stub runner : retourne un Path fixe ou lève une exception configurable. */
    private static final class StubRunner implements GatlingRunner {
        private Path resultDir = Path.of("/tmp/gatling-stub");
        private RuntimeException exception;
        private GatlingRunConfig lastConfig;
        private java.util.function.Consumer<GatlingRunConfig> onRun = c -> { };

        StubRunner returnsDir(Path dir) { this.resultDir = dir; return this; }
        StubRunner throwsException(RuntimeException e) { this.exception = e; return this; }

        @Override public Path run(GatlingRunConfig config) {
            lastConfig = config;
            onRun.accept(config);
            if (exception != null) throw exception;
            return resultDir;
        }
    }

    /** Stub parser : retourne un InjectionResult fixe ou lève une exception. */
    private static final class StubParser implements GatlingResultParser {
        private InjectionResult result;
        private RuntimeException exception;

        StubParser returns(InjectionResult r) { this.result = r; return this; }
        StubParser throwsException(RuntimeException e) { this.exception = e; return this; }

        @Override public InjectionResult parse(Path dir, TaskId taskId) {
            if (exception != null) throw exception;
            return result;
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final ExecutionId EXECUTION_ID = ExecutionId.of("exec-1");
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-1");
    private static final TaskId TASK_ID = TaskId.of("task-1");

    private static final ExecutionContext CONTEXT =
            ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID);

    private static final Path RESULTS_DIR = Path.of("/tmp/gatling-test");

    private static final InjectionResult SUCCESS_RESULT = new InjectionResult(
            TASK_ID, "com.example.Simulation", Duration.ofSeconds(30),
            1000, 950, 50, 5.0, 33.3,
            22, 38, 66, 75, 120,
            450, 5, 45.2,
            RESULTS_DIR, Map.of()
    );

    private StubRunner stubRunner;
    private StubParser stubParser;
    private GatlingTaskExecutor executor;

    private static StepDefinition createStep(Map<String, Object> params) {
        return new StepDefinition(
                TASK_ID, "gatling", Phase.INJECTION, params,
                null, null, null, null);
    }

    private static Map<String, Object> validParams() {
        return Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_WORKLOAD, TestPlans.holdMap()
        );
    }

    @BeforeEach
    void setUp() {
        stubRunner = new StubRunner();
        stubParser = new StubParser();
        executor = new GatlingTaskExecutor(com.performance.platform.injection.gatling.worker.RunnerBackedLauncher.of(stubRunner), stubParser);
    }

    // ── Generic HTTP injection (no simulation) ───────────────────────────────

    private GatlingTaskExecutor httpExecutor() {
        var target = new com.performance.platform.plugin.resource.Endpoint(
                "http://localhost:9999", null, null,
                Map.of("X-Env", "perf"), Map.of("devices", "/api/devices/{deviceId}"));
        com.performance.platform.plugin.resource.EndpointResolver resolver =
                name -> "api".equals(name) ? target : null;
        return new GatlingTaskExecutor(com.performance.platform.injection.gatling.worker.RunnerBackedLauncher.of(stubRunner), stubParser, java.util.List.of(
                new com.performance.platform.injection.gatling.http.HttpInjectionProtocol(resolver)));
    }

    private static Map<String, Object> httpParams(Map<String, Object> extra) {
        var params = new java.util.HashMap<String, Object>(extra);
        params.put(GatlingTaskExecutor.PARAM_WORKLOAD, TestPlans.holdMap());
        return params;
    }

    @Test
    @DisplayName("should run the built-in HTTP simulation and publish the request spec when simulation is absent")
    void shouldRunBuiltInHttpSimulation() {
        stubParser.returns(SUCCESS_RESULT);
        var seen = new java.util.concurrent.atomic.AtomicReference<
                com.performance.platform.injection.gatling.http.HttpRequestSpec>();
        stubRunner.onRun = config -> seen.set(
                com.performance.platform.injection.gatling.http.HttpRequestSpecHolder.get(config.simulationId()));

        TaskResult result = httpExecutor().execute(CONTEXT, createStep(httpParams(Map.of(
                "target", "api", "method", "post", "path", "devices",
                "pathParams", Map.of("deviceId", "device-{index:4}"),
                "queryParams", Map.of("run", "{executionId}"),
                "headers", Map.of("X-Request-Id", "{uuid}"),
                "body", Map.of("id", "{index}")))));

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(stubRunner.lastConfig.simulationClass()).endsWith("HttpScenarioSimulation");
        var spec = seen.get();
        assertThat(spec.method()).isEqualTo("POST");
        assertThat(spec.baseUrl()).isEqualTo("http://localhost:9999");
        assertThat(spec.path()).isEqualTo("/api/devices/{deviceId}");
        assertThat(spec.headers()).containsEntry("X-Env", "perf").containsKey("Content-Type");
        assertThat(spec.body()).isEqualTo("{\"id\":\"{index}\"}");
        assertThat(com.performance.platform.injection.gatling.http.HttpRequestSpecHolder
                .get(stubRunner.lastConfig.simulationId())).isNull();
    }

    @Test
    @DisplayName("should fail when HTTP injection has neither target nor baseUrl")
    void shouldFailWithoutTarget() {
        TaskResult result = executor.execute(CONTEXT, createStep(httpParams(Map.of("path", "/x"))));

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("target");
    }

    @Test
    @DisplayName("should fail when queryParams is not a map")
    void shouldFailWhenQueryParamsNotMap() {
        TaskResult result = executor.execute(CONTEXT, createStep(httpParams(Map.of(
                "baseUrl", "http://localhost:9", "path", "/x", "queryParams", "device_id=x"))));

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("queryParams");
    }
    @Test
    @DisplayName("should fail with the available protocols when protocol is unknown")
    void shouldFailOnUnknownProtocol() {
        TaskResult result = executor.execute(CONTEXT, createStep(httpParams(Map.of("protocol", "amqp"))));

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("amqp").contains("http");
    }

    // ── Sorties santé / histogramme / fenêtres ───────────────────────────────

    private static InjectionResult richResult() {
        var start = java.time.Instant.parse("2026-01-01T10:00:00Z");
        var end = start.plusSeconds(30);
        var histogram = new com.performance.platform.domain.injection.LatencyHistogram(
                Map.of(20L, 900L, 40L, 50L), Map.of(100L, 50L));
        var windows = java.util.List.of(
                new com.performance.platform.domain.injection.LoadWindow(
                        start, Duration.ofSeconds(1), 100, 98, 100, 2, true));
        return new InjectionResult(TASK_ID, "com.example.Simulation", Duration.ofSeconds(30),
                1000, 950, 50, 5.0, 33.3, 22, 38, 66, 75, 120, 450, 5, 45.2,
                RESULTS_DIR, Map.of(), start, end, histogram, windows,
                com.performance.platform.domain.injection.GeneratorHealth.unknown(), true);
    }

    @Test
    @DisplayName("should expose histogram, windows, instants, health, complete and arrivalKnown outputs")
    void shouldExposeResultOutputs() {
        stubParser.returns(richResult());

        TaskResult result = httpExecutor().execute(CONTEXT, createStep(httpParams(Map.of(
                "baseUrl", "http://localhost:9", "path", "/x"))));

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        var out = result.outputs();
        assertThat(out).containsKeys(GatlingTaskExecutor.OUTPUT_HISTOGRAM, GatlingTaskExecutor.OUTPUT_WINDOWS,
                GatlingTaskExecutor.OUTPUT_STARTED_AT, GatlingTaskExecutor.OUTPUT_ENDED_AT,
                GatlingTaskExecutor.OUTPUT_GENERATOR_HEALTH, GatlingTaskExecutor.OUTPUT_COMPLETE,
                GatlingTaskExecutor.OUTPUT_ARRIVAL_KNOWN);
        assertThat((Map<?, ?>) out.get(GatlingTaskExecutor.OUTPUT_HISTOGRAM)).isNotEmpty();
        assertThat((java.util.List<?>) out.get(GatlingTaskExecutor.OUTPUT_WINDOWS)).hasSize(1);
        assertThat(out.get(GatlingTaskExecutor.OUTPUT_STARTED_AT)).isEqualTo("2026-01-01T10:00:00Z");
        assertThat(out.get(GatlingTaskExecutor.OUTPUT_ENDED_AT)).isEqualTo("2026-01-01T10:00:30Z");
        assertThat(out.get(GatlingTaskExecutor.OUTPUT_GENERATOR_HEALTH)).isInstanceOf(Map.class);
        assertThat(out.get(GatlingTaskExecutor.OUTPUT_COMPLETE)).isEqualTo(true);
        assertThat(out.get(GatlingTaskExecutor.OUTPUT_ARRIVAL_KNOWN)).isEqualTo(true);
        var window = (Map<?, ?>) ((java.util.List<?>) out.get(GatlingTaskExecutor.OUTPUT_WINDOWS)).get(0);
        assertThat(((Number) window.get("plannedArrivals")).longValue()).isEqualTo(100L);
    }

    @Test
    @DisplayName("should zero planned arrivals and flag arrivalKnown=false for an opaque simulation")
    void shouldFlagOpaqueSimulationArrivalsUnknown() {
        stubParser.returns(richResult());

        TaskResult result = executor.execute(CONTEXT, createStep(validParams()));

        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        var out = result.outputs();
        assertThat(out.get(GatlingTaskExecutor.OUTPUT_ARRIVAL_KNOWN)).isEqualTo(false);
        var window = (Map<?, ?>) ((java.util.List<?>) out.get(GatlingTaskExecutor.OUTPUT_WINDOWS)).get(0);
        assertThat(((Number) window.get("plannedArrivals")).longValue()).isZero();
        assertThat(((Number) window.get("requests")).longValue()).isEqualTo(100L);
        var injection = (InjectionResult) out.get(GatlingTaskExecutor.OUTPUT_RESULT);
        assertThat(injection.windows().get(0).plannedArrivals()).isZero();
    }

    @Test
    @DisplayName("should declare histogram and windows capabilities")
    void shouldDeclareHistogramAndWindowsCapabilities() {
        assertThat(executor.injectionCapabilities().providesHistogram()).isTrue();
        assertThat(executor.injectionCapabilities().providesWindows()).isTrue();
    }

    // ── Contract ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 'gatling' as supported task name")
    void shouldReturnGatlingAsSupportedTaskName() {
        assertThat(executor.getSupportedTaskName()).isEqualTo("gatling");
    }

    // ── Success cases ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should execute successfully and return InjectionResult in outputs")
    void shouldExecuteSuccessfully() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.taskId()).isEqualTo(TASK_ID);
        assertThat(result.taskName()).isEqualTo("gatling");
        assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(result.outputs()).containsKey(GatlingTaskExecutor.OUTPUT_RESULT);
        assertThat(result.outputs().get(GatlingTaskExecutor.OUTPUT_RESULT))
                .isInstanceOf(InjectionResult.class)
                .isEqualTo(SUCCESS_RESULT);
        assertThat(result.outputs()).containsKeys(GatlingTaskExecutor.OUTPUT_WORKLOAD_PLAN,
                GatlingTaskExecutor.OUTPUT_WORKLOAD_PLAN_HASH);
    }

    @Test
    @DisplayName("should extract workload from parameters")
    void shouldExtractWorkloadFromParameters() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should accept WorkloadPlan instance directly in parameters")
    void shouldAcceptPlanInstanceDirectly() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_WORKLOAD, TestPlans.hold(10, 60)
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should set correct duration on success")
    void shouldSetCorrectDuration() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.duration()).isPositive();
        assertThat(result.completedAt()).isNotNull();
    }

    // ── Error: missing parameters ─────────────────────────────────────────────

    @Test
    @DisplayName("should return failed when simulation parameter is missing")
    void shouldFailWhenSimulationMissing() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_WORKLOAD, TestPlans.holdMap()
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("simulation");
    }

    @Test
    @DisplayName("should return failed when simulation parameter is blank")
    void shouldFailWhenSimulationBlank() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "   ",
                GatlingTaskExecutor.PARAM_WORKLOAD, TestPlans.holdMap()
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("simulation");
    }

    @Test
    @DisplayName("should return failed when workload parameter is missing")
    void shouldFailWhenWorkloadMissing() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation"
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("workload");
    }

    @Test
    @DisplayName("should return failed when workload stages are missing")
    void shouldFailWhenWorkloadStagesMissing() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_WORKLOAD, Map.of("model", "open")
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("workload");
    }

    @Test
    @DisplayName("should return failed when workload model is invalid")
    void shouldFailWhenWorkloadModelInvalid() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_WORKLOAD, Map.of("model", "bogus", "stages", java.util.List.of())
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("workload");
    }

    @Test
    @DisplayName("should return failed when timeout is negative")
    void shouldFailWhenTimeoutNegative() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_WORKLOAD, TestPlans.holdMap(),
                GatlingTaskExecutor.PARAM_TIMEOUT, -1
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("timeout");
    }

    // ── Error: runner/parser exceptions → TaskResult.failed (no propagate) ───

    @Test
    @DisplayName("should return failed when runner throws GatlingExecutionException")
    void shouldFailWhenRunnerThrows() {
        stubRunner.throwsException(new GatlingExecutionException("Simulation crashed"));
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("Simulation crashed");
    }

    @Test
    @DisplayName("should return failed when parser throws ResultParsingException")
    void shouldFailWhenParserThrows() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.throwsException(new ResultParsingException("stats.json not found"));
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("Result parsing failed");
    }

    @Test
    @DisplayName("should return failed for unexpected runtime exception")
    void shouldFailOnUnexpectedException() {
        stubRunner.throwsException(new RuntimeException("Unexpected I/O error"));
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("Gatling worker failed").contains("Unexpected I/O error");
    }

    @Test
    @DisplayName("should never propagate exception — all errors become TaskResult.failed")
    void shouldNeverPropagateException() {
        stubRunner.throwsException(new RuntimeException("Boom!"));
        StepDefinition step = createStep(validParams());

        // This must not throw
        TaskResult result = executor.execute(CONTEXT, step);
        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
    }

    // ── StatefulResourceCleaner ───────────────────────────────────────────────

    @Nested
    @DisplayName("StatefulResourceCleaner")
    class CleanupTests {

        @Test
        @DisplayName("should clear all active executions on global cleanup")
        void shouldClearAllOnGlobalCleanup() {
            executor.cleanup(null);
            // No exception = success. Best-effort cleanup.
        }

        @Test
        @DisplayName("should remove specific execution on targeted cleanup")
        void shouldRemoveSpecificExecution() {
            executor.cleanup(EXECUTION_ID);
            // No exception = success.
        }
    }
}
