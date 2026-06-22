package com.performance.platform.injection.gatling.e2e;

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
import com.performance.platform.injection.gatling.GatlingTaskExecutor;
import com.performance.platform.injection.gatling.result.GatlingResultParser;
import com.performance.platform.injection.gatling.result.ResultParsingException;
import com.performance.platform.injection.gatling.runner.GatlingExecutionException;
import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import com.performance.platform.injection.gatling.runner.GatlingRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests E2E du pipeline d'injection Gatling complet.
 * <p>
 * Flux teste : parametres de step -> extraction LoadModel -> traduction
 * -> execution GatlingRunner -> parsing GatlingResultParser -> InjectionResult -> TaskResult.
 */
@DisplayName("Gatling Pipeline E2E")
class GatlingPipelineE2ETest {

    private static final String OUTPUT_RESULT_KEY = "result"; // GatlingTaskExecutor.OUTPUT_RESULT value
    private static final ExecutionId EXECUTION_ID = ExecutionId.of("exec-e2e");
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-e2e");
    private static final ExecutionContext CONTEXT =
            ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID);
    private static final Path RESULTS_DIR = Path.of("/tmp/gatling-e2e-results");

    // ========================================================================
    // Stubs
    // ========================================================================

    static class StubRunner implements GatlingRunner {
        private Path resultDir = Path.of("/tmp/gatling-e2e-stub");
        private RuntimeException exception;
        private final CopyOnWriteArrayList<GatlingRunConfig> configs = new CopyOnWriteArrayList<>();

        StubRunner returnsDir(Path dir) { this.resultDir = dir; return this; }
        StubRunner throwsException(RuntimeException e) { this.exception = e; return this; }
        List<GatlingRunConfig> getReceivedConfigs() { return configs; }

        @Override
        public Path run(GatlingRunConfig config) {
            configs.add(config);
            if (exception != null) throw exception;
            return resultDir;
        }
    }

    static class StubParser implements GatlingResultParser {
        private InjectionResult result;
        private RuntimeException exception;
        private final CopyOnWriteArrayList<Path> parsedDirs = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<TaskId> parsedTaskIds = new CopyOnWriteArrayList<>();

        StubParser returns(InjectionResult r) { this.result = r; return this; }
        StubParser throwsException(RuntimeException e) { this.exception = e; return this; }
        List<Path> getParsedDirs() { return parsedDirs; }
        List<TaskId> getParsedTaskIds() { return parsedTaskIds; }

        @Override
        public InjectionResult parse(Path dir, TaskId taskId) {
            parsedDirs.add(dir);
            parsedTaskIds.add(taskId);
            if (exception != null) throw exception;
            return result;
        }
    }

    // ========================================================================
    // E2E: Complete pipeline
    // ========================================================================

    @Nested
    @DisplayName("Complete injection pipeline")
    class CompletePipeline {

        @Test
        @DisplayName("E2E-G-01: Full pipeline with RAMP load model produces successful result")
        void rampLoadModelFullPipeline() {
            var runner = new StubRunner();
            runner.returnsDir(RESULTS_DIR);

            var taskId = TaskId.of("gatling-task-1");
            InjectionResult injectionResult = buildInjectionResult(taskId, "com.example.RampSimulation",
                    Duration.ofSeconds(60), 10000, 9500, 500, 5.0, 40.5);
            var parser = new StubParser();
            parser.returns(injectionResult);

            var executor = new GatlingTaskExecutor(runner, parser);

            StepDefinition step = buildGatlingStep(taskId,
                    Map.of("simulation", "com.example.RampSimulation",
                            "loadModel", Map.of("type", "RAMP",
                                    "stages", List.of(
                                            Map.of("duration", "2m", "usersPerSecond", 10),
                                            Map.of("duration", "5m", "usersPerSecond", 100)))));

            TaskResult result = executor.execute(CONTEXT, step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.taskName()).isEqualTo("gatling");
            assertThat(result.outputs()).containsKey(OUTPUT_RESULT_KEY);

            InjectionResult parsed = (InjectionResult) result.outputs().get(OUTPUT_RESULT_KEY);
            assertThat(parsed.totalRequests()).isEqualTo(10000);
            assertThat(parsed.successfulRequests()).isEqualTo(9500);
            assertThat(parsed.errorRate()).isEqualTo(5.0);

            // Verify runner received correct config
            assertThat(runner.getReceivedConfigs()).hasSize(1);
            assertThat(runner.getReceivedConfigs().get(0).simulationClass())
                    .isEqualTo("com.example.RampSimulation");
            assertThat(parser.getParsedDirs()).hasSize(1);
        }

        @Test
        @DisplayName("E2E-G-02: CONSTANT load model")
        void constantLoadModelProducesCorrectConfig() {
            var runner = new StubRunner();
            var parser = new StubParser()
                    .returns(buildInjectionResult(TaskId.of("const-test"),
                            "com.example.ConstantSimulation", Duration.ofSeconds(30),
                            5000, 5000, 0, 0.0, 20.0));

            var executor = new GatlingTaskExecutor(runner, parser);

            StepDefinition step = buildGatlingStep(TaskId.of("const-test"),
                    Map.of("simulation", "com.example.ConstantSimulation",
                            "loadModel", Map.of("type", "CONSTANT",
                                    "usersPerSecond", 200,
                                    "duration", "10m")));

            executor.execute(CONTEXT, step);

            assertThat(runner.getReceivedConfigs()).hasSize(1);
            assertThat(runner.getReceivedConfigs().get(0).simulationClass())
                    .isEqualTo("com.example.ConstantSimulation");
        }

        @Test
        @DisplayName("E2E-G-03: BURST load model passed as type reference")
        void burstLoadModelType() {
            var runner = new StubRunner();
            var parser = new StubParser()
                    .returns(buildInjectionResult(TaskId.of("burst-test"),
                            "com.example.BurstSimulation", Duration.ofSeconds(10),
                            2000, 1800, 200, 10.0, 20.0));

            var executor = new GatlingTaskExecutor(runner, parser);

            // Use a LoadModel record with BURST type
            var model = new LoadModel(LoadModelType.BURST, Map.of(
                    "usersPerSecond", 1000,
                    "duration", "1m",
                    "burstDuration", "10s"));
            StepDefinition step = buildGatlingStep(TaskId.of("burst-test"),
                    Map.of("simulation", "com.example.BurstSimulation",
                            "loadModel", model));

            TaskResult result = executor.execute(CONTEXT, step);
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }

        @Test
        @DisplayName("E2E-G-04: LoadModel passed as LoadModel record directly")
        void loadModelAsRecord() {
            var runner = new StubRunner();
            var parser = new StubParser()
                    .returns(buildInjectionResult(TaskId.of("record-test"),
                            "com.example.SpikeSimulation", Duration.ofSeconds(30),
                            2000, 1900, 100, 5.0, 15.0));

            var executor = new GatlingTaskExecutor(runner, parser);

            var model = new LoadModel(LoadModelType.SPIKE, Map.of(
                    "usersPerSecond", 500,
                    "duration", "2m"));

            StepDefinition step = buildGatlingStep(TaskId.of("record-test"),
                    Map.of("simulation", "com.example.SpikeSimulation",
                            "loadModel", model));

            TaskResult result = executor.execute(CONTEXT, step);
            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }
    }

    // ========================================================================
    // Error handling
    // ========================================================================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("E2E-G-10: GatlingExecutionException produces FAILED TaskResult")
        void runnerExceptionProducesFailedResult() {
            var runner = new StubRunner()
                    .throwsException(new GatlingExecutionException("Simulation crashed",
                            new RuntimeException("OOM")));
            var parser = new StubParser()
                    .returns(buildInjectionResult(TaskId.of("fail-test"),
                            "x", Duration.ofSeconds(1), 1, 1, 0, 0.0, 1.0));

            var executor = new GatlingTaskExecutor(runner, parser);

            StepDefinition step = buildGatlingStep(TaskId.of("fail-test"),
                    Map.of("simulation", "com.example.FailingSimulation",
                            "loadModel", Map.of("type", "RAMP", "stages", List.of(
                                    Map.of("duration", "1m", "usersPerSecond", 10)))));

            TaskResult result = executor.execute(CONTEXT, step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Simulation crashed");
        }

        @Test
        @DisplayName("E2E-G-11: ResultParsingException produces FAILED TaskResult")
        void parsingExceptionProducesFailedResult() {
            var runner = new StubRunner();
            var parser = new StubParser()
                    .throwsException(new ResultParsingException("stats.json corrupted",
                            new RuntimeException("Parse error")));

            var executor = new GatlingTaskExecutor(runner, parser);

            StepDefinition step = buildGatlingStep(TaskId.of("parse-fail"),
                    Map.of("simulation", "com.example.CorruptedSimulation",
                            "loadModel", Map.of("type", "CONSTANT",
                                    "usersPerSecond", 100, "duration", "1m")));

            TaskResult result = executor.execute(CONTEXT, step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("stats.json corrupted");
        }

        @Test
        @DisplayName("E2E-G-12: Missing simulation parameter produces FAILED")
        void missingSimulationParameterFails() {
            var runner = new StubRunner();
            var parser = new StubParser()
                    .returns(buildInjectionResult(TaskId.of("no-sim"),
                            "x", Duration.ofSeconds(1), 1, 1, 0, 0.0, 1.0));

            var executor = new GatlingTaskExecutor(runner, parser);

            var step = new StepDefinition(
                    TaskId.of("no-sim"), "gatling", Phase.INJECTION,
                    Map.of("loadModel", Map.of("type", "CONSTANT",
                            "usersPerSecond", 100, "duration", "1m")),
                    List.of(), List.of(), Duration.ofSeconds(300), null);

            TaskResult result = executor.execute(CONTEXT, step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("simulation");
        }
    }

    // ========================================================================
    // Resource cleanup
    // ========================================================================

    @Nested
    @DisplayName("Resource cleanup")
    class ResourceCleanup {

        @Test
        @DisplayName("E2E-G-20: Cleanup is idempotent and does not throw")
        void cleanupIsIdempotent() {
            var runner = new StubRunner();
            var parser = new StubParser()
                    .returns(buildInjectionResult(TaskId.of("cleanup-test"),
                            "x", Duration.ofSeconds(1), 1, 1, 0, 0.0, 1.0));

            var executor = new GatlingTaskExecutor(runner, parser);

            StepDefinition step = buildGatlingStep(TaskId.of("cleanup-test"),
                    Map.of("simulation", "com.example.CleanupSimulation",
                            "loadModel", Map.of("type", "CONSTANT",
                                    "usersPerSecond", 10, "duration", "30s")));
            executor.execute(CONTEXT, step);

            executor.cleanup(EXECUTION_ID);
            executor.cleanup(EXECUTION_ID); // Double cleanup
            executor.cleanup(null);         // Global cleanup
        }
    }

    // ========================================================================
    // Multi-step realistic scenario
    // ========================================================================

    @Nested
    @DisplayName("Multi-step realistic load testing scenario")
    class MultiStepScenario {

        @Test
        @DisplayName("E2E-G-30: Sequential simulations produce distinct results")
        void sequentialSimulations() {
            var runner = new StubRunner();
            runner.returnsDir(RESULTS_DIR);

            var callCount = new AtomicInteger(0);
            var parser = new StubParser() {
                @Override
                public InjectionResult parse(Path dir, TaskId taskId) {
                    int seq = callCount.incrementAndGet();
                    return buildInjectionResult(taskId, "com.example.SeqSimulation",
                            Duration.ofSeconds(10L * seq), 1000L * seq,
                            950L * seq, 50L * seq, 5.0, 30.0);
                }
            };

            var executor = new GatlingTaskExecutor(runner, parser);

            for (int i = 1; i <= 3; i++) {
                var taskId = TaskId.of("seq-" + i);
                StepDefinition step = buildGatlingStep(taskId,
                        Map.of("simulation", "com.example.SeqSimulation",
                                "loadModel", Map.of("type", "CONSTANT",
                                        "usersPerSecond", 100 * i,
                                        "duration", (i * 5) + "m")));

                TaskResult result = executor.execute(CONTEXT, step);
                assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);

                InjectionResult parsed = (InjectionResult) result.outputs().get(OUTPUT_RESULT_KEY);
                assertThat(parsed.totalRequests()).isEqualTo(1000L * i);
            }

            assertThat(callCount.get()).isEqualTo(3);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static StepDefinition buildGatlingStep(TaskId taskId,
                                                    Map<String, Object> params) {
        return new StepDefinition(taskId, "gatling", Phase.INJECTION, params,
                List.of(), List.of(), Duration.ofSeconds(300), null);
    }

    private static InjectionResult buildInjectionResult(TaskId taskId, String simClass,
                                                         Duration duration, long total,
                                                         long success, long failed,
                                                         double errorRate, double throughput) {
        return new InjectionResult(
                taskId, simClass, duration,
                total, success, failed, errorRate, throughput,
                10L, 20L, 40L, 50L, 80L, 100L, 5L, 20.0,
                RESULTS_DIR, Map.of());
    }
}
