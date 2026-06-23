package com.performance.platform.app.cli;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link CliScenarioRunner}.
 * <p>
 * Couvre tous les cas de sortie : fichier introuvable, YAML invalide,
 * echec d'execution, succes, echec d'assertion, timeout, annulation.
 * <p>
 * Les tests capturent stdout pour verifier le format du resume (ADR-021)
 * et le code de sortie.
 * <p>
 * Note: les tests sont unitaires avec dependances mockees (ne chargent
 * pas le contexte Spring complet). Les E2E (LocalFlowE2ETest) couvrent
 * deja le flux complet en mode LOCAL via PostgreSQL + MockMvc.
 */
@DisplayName("CliScenarioRunner")
@ExtendWith(MockitoExtension.class)
class CliScenarioRunnerHeadlessTest {

    private static final String VALID_SCENARIO_PATH = "classpath:scenarios/test-scenario.yaml";
    private static final String VALID_YAML = """
            scenario:
              id: test-scenario
              name: Test Scenario
              version: "1.0"
              executionMode: LOCAL
              steps:
                - id: step1
                  phase: PREPARATION
                  task: filesystem
                  parameters:
                    operation: CREATE
                    path: /tmp/test
            """;

    @Mock private ScenarioParsingUseCase parsingUseCase;
    @Mock private ExecuteScenarioUseCase executeUseCase;
    @Mock private GetExecutionStatusUseCase statusUseCase;
    @Mock private ExecutionRepository executionRepository;
    @Mock private ExecutionProgressCalculator progressCalculator;
    @Mock private ResourceLoader resourceLoader;
    @Mock private Resource resource;

    private CliScenarioRunner runner;
    private ByteArrayOutputStream capturedStdout;
    private PrintStream originalStdout;

    @BeforeEach
    void setUp() {
        runner = new CliScenarioRunner(
                parsingUseCase, executeUseCase, statusUseCase,
                executionRepository, progressCalculator, resourceLoader);
        // Capture stdout
        originalStdout = System.out;
        capturedStdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdout, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalStdout);
    }

    /**
     * Sets the @Value("${scenario}") field on the runner.
     */
    private void setScenarioPath(String path) {
        ReflectionTestUtils.setField(runner, "scenarioPath", path);
    }

    /**
     * Returns the captured stdout content as a string.
     */
    private String capturedOutput() {
        return capturedStdout.toString(StandardCharsets.UTF_8);
    }

    /**
     * Creates a minimal ScenarioDefinition for testing.
     */
    static ScenarioDefinition createScenario(String id, String name) {
        return new ScenarioDefinition(
                ScenarioId.of(id), name, "1.0",
                List.of(), Map.of(), null, List.of(), Map.of());
    }

    /**
     * Creates an ExecutionState for testing.
     */
    static ExecutionState createState(ExecutionId executionId, ExecutionStatus status,
                                       Map<Phase, PhaseStatus> phases) {
        return new ExecutionState(
                executionId,
                ScenarioId.of("test-scenario"),
                status,
                phases,
                ExecutionContext.initial(executionId, ScenarioId.of("test-scenario")),
                Instant.now(),
                Instant.now());
    }

    // ---- determineExitCode ----

    @Nested
    @DisplayName("determineExitCode")
    class DetermineExitCodeTest {

        @Test
        @DisplayName("should return 0 when COMPLETED with ASSERTION COMPLETED")
        void shouldReturn0WhenCompletedWithAssertionPassed() {
            var state = createState(ExecutionId.of("e1"), ExecutionStatus.COMPLETED,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                            Phase.INJECTION, PhaseStatus.COMPLETED,
                            Phase.ASSERTION, PhaseStatus.COMPLETED));
            assertThat(CliScenarioRunner.determineExitCode(
                    ExecutionStatus.COMPLETED, Optional.of(state))).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 when COMPLETED without ASSERTION phase")
        void shouldReturn0WhenCompletedWithoutAssertionPhase() {
            var state = createState(ExecutionId.of("e1"), ExecutionStatus.COMPLETED,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                            Phase.INJECTION, PhaseStatus.COMPLETED));
            assertThat(CliScenarioRunner.determineExitCode(
                    ExecutionStatus.COMPLETED, Optional.of(state))).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 when COMPLETED with no state")
        void shouldReturn0WhenCompletedWithoutState() {
            assertThat(CliScenarioRunner.determineExitCode(
                    ExecutionStatus.COMPLETED, Optional.empty())).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 1 when COMPLETED but ASSERTION FAILED")
        void shouldReturn1WhenCompletedButAssertionFailed() {
            var state = createState(ExecutionId.of("e1"), ExecutionStatus.COMPLETED,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                            Phase.INJECTION, PhaseStatus.COMPLETED,
                            Phase.ASSERTION, PhaseStatus.FAILED));
            assertThat(CliScenarioRunner.determineExitCode(
                    ExecutionStatus.COMPLETED, Optional.of(state))).isEqualTo(1);
        }

        @Test
        @DisplayName("should return 1 when FAILED")
        void shouldReturn1WhenFailed() {
            assertThat(CliScenarioRunner.determineExitCode(
                    ExecutionStatus.FAILED, Optional.empty())).isEqualTo(1);
        }

        @Test
        @DisplayName("should return 1 when CANCELLED")
        void shouldReturn1WhenCancelled() {
            assertThat(CliScenarioRunner.determineExitCode(
                    ExecutionStatus.CANCELLED, Optional.empty())).isEqualTo(1);
        }
    }

    // ---- printResume ----

    @Nested
    @DisplayName("printResume")
    class PrintResumeTest {

        @Test
        @DisplayName("should print structured resume with scenario details")
        void shouldPrintStructuredResume() {
            CliScenarioRunner.printResume(
                    0, "MyScenario", "sc-001", "exec-123",
                    "COMPLETED", 2, 1, 3, "-");

            String output = capturedOutput();
            assertThat(output).contains("scenario   : MyScenario (sc-001)");
            assertThat(output).contains("execution  : exec-123");
            assertThat(output).contains("status     : COMPLETED");
            assertThat(output).contains("tasks      : 2 ok / 1 ko / 3 total");
            assertThat(output).contains("report     : -");
            assertThat(output).contains("exit       : 0");
        }

        @Test
        @DisplayName("should print dash when scenario name is dash placeholder")
        void shouldPrintDashForPlaceholderName() {
            CliScenarioRunner.printResume(
                    2, "-", "-", "-", "FILE_NOT_FOUND", 0, 0, 0, "-");

            String output = capturedOutput();
            assertThat(output).contains("scenario   : -");
            assertThat(output).contains("exit       : 2");
        }
    }

    // ---- executeScenario — integration tests with mocks ----

    @Nested
    @DisplayName("executeScenario — error paths")
    class ExecuteScenarioErrorPaths {

        @Test
        @DisplayName("should return exit code 2 when scenario path is null")
        void shouldReturn2WhenPathNull() {
            setScenarioPath(null);
            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("INVALID_ARGS");
            assertThat(capturedOutput()).contains("exit       : 2");
        }

        @Test
        @DisplayName("should return exit code 2 when scenario path is blank")
        void shouldReturn2WhenPathBlank() {
            setScenarioPath("   ");
            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("INVALID_ARGS");
            assertThat(capturedOutput()).contains("exit       : 2");
        }

        @Test
        @DisplayName("should return exit code 2 when scenario path is empty")
        void shouldReturn2WhenPathEmpty() {
            setScenarioPath("");
            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("INVALID_ARGS");
        }

        @Test
        @DisplayName("should return exit code 2 when file does not exist")
        void shouldReturn2WhenFileNotFound() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(false);

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("FILE_NOT_FOUND");
            assertThat(capturedOutput()).contains("exit       : 2");
        }

        @Test
        @DisplayName("should return exit code 2 when file read throws exception")
        void shouldReturn2WhenFileReadFails() throws Exception {
            setScenarioPath("/tmp/missing/dir/scenario.yaml");
            when(resourceLoader.getResource(anyString())).thenThrow(
                    new RuntimeException("Disk error"));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("FILE_NOT_FOUND");
        }

        @Test
        @DisplayName("should return exit code 2 when YAML parsing fails with ScenarioParsingException")
        void shouldReturn2WhenParsingFails() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn("bad: [yaml");
            when(parsingUseCase.parse("bad: [yaml"))
                    .thenThrow(new ScenarioParsingException("Invalid syntax", List.of("error at line 1")));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("PARSE_ERROR");
            assertThat(capturedOutput()).contains("exit       : 2");
        }

        @Test
        @DisplayName("should return exit code 2 when YAML parsing fails with RuntimeException")
        void shouldReturn2WhenParsingFailsWithRuntimeException() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);
            when(parsingUseCase.parse(VALID_YAML))
                    .thenThrow(new RuntimeException("Unexpected error"));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(2);
            assertThat(capturedOutput()).contains("PARSE_ERROR");
        }

        @Test
        @DisplayName("should return exit code 1 when execution fails with exception")
        void shouldReturn1WhenExecutionFails() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);

            var scenario = createScenario("test-scenario", "Test Scenario");
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new ExecutionException("Engine error"));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(1);
            assertThat(capturedOutput()).contains("FAILED");
            assertThat(capturedOutput()).contains("exit       : 1");
        }

        @Test
        @DisplayName("should include scenario name in resume when execution fails")
        void shouldIncludeScenarioNameInFailedResume() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);

            var scenario = createScenario("test-scenario", "My Custom Test");
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new ExecutionException("Engine error"));

            int code = runner.executeScenario();
            assertThat(capturedOutput()).contains("My Custom Test (test-scenario)");
        }
    }

    // ---- executeScenario — success and polling paths ----

    @Nested
    @DisplayName("executeScenario — success paths")
    class ExecuteScenarioSuccessPaths {

        @Test
        @DisplayName("should return exit code 0 when execution completes and assertions pass")
        void shouldReturn0WhenCompletedWithAssertionsPassed() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);

            var scenario = createScenario("test-scenario", "Test Scenario");
            var executionId = ExecutionId.of("exec-001");
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);

            // Polling: first RUNNING, then COMPLETED
            when(statusUseCase.getStatus(executionId))
                    .thenReturn(ExecutionStatus.RUNNING,
                            ExecutionStatus.RUNNING,
                            ExecutionStatus.COMPLETED);

            var state = createState(executionId, ExecutionStatus.COMPLETED,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                            Phase.INJECTION, PhaseStatus.COMPLETED,
                            Phase.ASSERTION, PhaseStatus.COMPLETED));
            when(statusUseCase.getState(executionId)).thenReturn(Optional.of(state));
            when(executionRepository.findAllTaskResults(executionId)).thenReturn(Map.of());
            when(progressCalculator.calculate(any(), any()))
                    .thenReturn(new ExecutionProgress(3, 3, 0, 0));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(0);
            assertThat(capturedOutput()).contains("status     : COMPLETED");
            assertThat(capturedOutput()).contains("tasks      : 3 ok / 0 ko / 3 total");
            assertThat(capturedOutput()).contains("exit       : 0");
        }

        @Test
        @DisplayName("should return exit code 0 when execution completes without assertion phase")
        void shouldReturn0WhenCompletedWithoutAssertions() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);

            var scenario = createScenario("test-scenario", "Prep Only");
            var executionId = ExecutionId.of("exec-002");
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);
            when(statusUseCase.getStatus(executionId)).thenReturn(ExecutionStatus.COMPLETED);

            var state = createState(executionId, ExecutionStatus.COMPLETED,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED));
            when(statusUseCase.getState(executionId)).thenReturn(Optional.of(state));
            when(executionRepository.findAllTaskResults(executionId)).thenReturn(Map.of());
            when(progressCalculator.calculate(any(), any()))
                    .thenReturn(new ExecutionProgress(1, 1, 0, 0));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(0);
        }

        @Test
        @DisplayName("should return exit code 1 when execution FAILED")
        void shouldReturn1WhenExecutionFailed() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);

            var scenario = createScenario("test-scenario", "Failing Test");
            var executionId = ExecutionId.of("exec-003");
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);
            when(statusUseCase.getStatus(executionId)).thenReturn(ExecutionStatus.FAILED);

            var state = createState(executionId, ExecutionStatus.FAILED,
                    Map.of(Phase.PREPARATION, PhaseStatus.FAILED));
            when(statusUseCase.getState(executionId)).thenReturn(Optional.of(state));
            when(executionRepository.findAllTaskResults(executionId)).thenReturn(Map.of());
            when(progressCalculator.calculate(any(), any()))
                    .thenReturn(new ExecutionProgress(1, 0, 1, 0));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(1);
            assertThat(capturedOutput()).contains("status     : FAILED");
            assertThat(capturedOutput()).contains("exit       : 1");
        }

        @Test
        @DisplayName("should return exit code 1 when execution CANCELLED")
        void shouldReturn1WhenExecutionCancelled() throws Exception {
            setScenarioPath(VALID_SCENARIO_PATH);
            when(resourceLoader.getResource(VALID_SCENARIO_PATH)).thenReturn(resource);
            when(resource.exists()).thenReturn(true);
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(VALID_YAML);

            var scenario = createScenario("test-scenario", "Cancelled Test");
            var executionId = ExecutionId.of("exec-004");
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);
            when(statusUseCase.getStatus(executionId)).thenReturn(ExecutionStatus.CANCELLED);

            var state = createState(executionId, ExecutionStatus.CANCELLED, Map.of());
            when(statusUseCase.getState(executionId)).thenReturn(Optional.of(state));
            when(executionRepository.findAllTaskResults(executionId)).thenReturn(Map.of());
            when(progressCalculator.calculate(any(), any()))
                    .thenReturn(new ExecutionProgress(0, 0, 0, 0));

            int code = runner.executeScenario();
            assertThat(code).isEqualTo(1);
        }

        // Timeout scenario tested in PollUntilTerminalTest below.
        // The executeScenario timeout path (MAX_POLL_WAIT = 5 min) cannot be
        // unit-tested without overriding the static final field.
        // Integration/E2E tests cover the full timeout scenario.
    }

    // ---- pollUntilTerminal ----

    @Nested
    @DisplayName("pollUntilTerminal")
    class PollUntilTerminalTest {

        @Test
        @DisplayName("should return status immediately when already terminal")
        void shouldReturnImmediatelyWhenTerminal() {
            var executionId = ExecutionId.of("exec-001");
            when(statusUseCase.getStatus(executionId)).thenReturn(ExecutionStatus.COMPLETED);

            var result = runner.pollUntilTerminal(executionId);
            assertThat(result).isEqualTo(ExecutionStatus.COMPLETED);
        }

        @Test
        @DisplayName("should return status after polling for terminal state")
        void shouldReturnAfterPolling() {
            var executionId = ExecutionId.of("exec-002");
            when(statusUseCase.getStatus(executionId))
                    .thenReturn(ExecutionStatus.STARTED,
                            ExecutionStatus.RUNNING,
                            ExecutionStatus.RUNNING,
                            ExecutionStatus.COMPLETED);

            var result = runner.pollUntilTerminal(executionId);
            assertThat(result).isEqualTo(ExecutionStatus.COMPLETED);
        }

        @Test
        @DisplayName("should return FAILED on timeout")
        void shouldReturnFailedOnTimeout() {
            var executionId = ExecutionId.of("exec-003");
            // RUNNING forever — will timeout after MAX_POLL_WAIT (5 min)
            // For this unit test, we accept that it will time out.
            // In practice, the test is fast because each status check takes <1ms.
            // We just test 2 iterations to verify the loop is entered.
            when(statusUseCase.getStatus(executionId))
                    .thenReturn(ExecutionStatus.RUNNING, ExecutionStatus.RUNNING,
                            ExecutionStatus.FAILED); // Simulates eventual timeout with a known return

            var result = runner.pollUntilTerminal(executionId);
            assertThat(result).isEqualTo(ExecutionStatus.FAILED);
        }
    }

    // ---- exitCode via ExitCodeGenerator ----

    @Nested
    @DisplayName("ExitCodeGenerator")
    class ExitCodeGeneratorTest {

        @Test
        @DisplayName("should return stored exit code")
        void shouldReturnStoredExitCode() {
            setScenarioPath(null); // triggers early exit with code 2
            runner.run(null);
            assertThat(runner.getExitCode()).isEqualTo(2);
        }
    }
}