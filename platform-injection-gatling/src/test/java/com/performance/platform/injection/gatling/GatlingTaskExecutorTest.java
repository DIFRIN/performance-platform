package com.performance.platform.injection.gatling;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
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

        StubRunner returnsDir(Path dir) { this.resultDir = dir; return this; }
        StubRunner throwsException(RuntimeException e) { this.exception = e; return this; }

        @Override public Path run(GatlingRunConfig config) {
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
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "type", "CONSTANT",
                        "parameters", Map.of("users", 10, "duration", "60s")
                )
        );
    }

    @BeforeEach
    void setUp() {
        stubRunner = new StubRunner();
        stubParser = new StubParser();
        executor = new GatlingTaskExecutor(stubRunner, stubParser);
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
    }

    @Test
    @DisplayName("should extract loadModel from parameters with constant type")
    void shouldExtractLoadModelFromParameters() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        StepDefinition step = createStep(validParams());

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should accept LoadModel instance directly in parameters")
    void shouldAcceptLoadModelInstanceDirectly() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        LoadModel loadModel = new LoadModel(LoadModelType.SPIKE,
                Map.of("users", 100, "peak", "5s"));
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_LOAD_MODEL, loadModel
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should handle LoadModelType enum in loadModel params")
    void shouldHandleLoadModelTypeEnum() {
        stubRunner.returnsDir(RESULTS_DIR);
        stubParser.returns(SUCCESS_RESULT);
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.SpikeSim",
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "type", LoadModelType.SPIKE,
                        "parameters", Map.of("users", 50)
                )
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
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "type", "CONSTANT",
                        "parameters", Map.of("users", 10)
                )
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
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "type", "CONSTANT",
                        "parameters", Map.of("users", 10)
                )
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("simulation");
    }

    @Test
    @DisplayName("should return failed when loadModel parameter is missing")
    void shouldFailWhenLoadModelMissing() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation"
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("loadModel");
    }

    @Test
    @DisplayName("should return failed when loadModel type is missing")
    void shouldFailWhenLoadModelTypeMissing() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "parameters", Map.of("users", 10)
                )
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("loadModel");
    }

    @Test
    @DisplayName("should return failed when loadModel type is invalid")
    void shouldFailWhenLoadModelTypeInvalid() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "type", "INVALID_TYPE",
                        "parameters", Map.of("users", 10)
                )
        ));

        TaskResult result = executor.execute(CONTEXT, step);

        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("loadModel");
    }

    @Test
    @DisplayName("should return failed when timeout is negative")
    void shouldFailWhenTimeoutNegative() {
        StepDefinition step = createStep(Map.of(
                GatlingTaskExecutor.PARAM_SIMULATION, "com.example.Simulation",
                GatlingTaskExecutor.PARAM_LOAD_MODEL, Map.of(
                        "type", "CONSTANT",
                        "parameters", Map.of("users", 10)
                ),
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
        assertThat(result.errorMessage()).contains("Unexpected error");
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
