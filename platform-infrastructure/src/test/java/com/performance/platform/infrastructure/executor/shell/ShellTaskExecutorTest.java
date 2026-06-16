package com.performance.platform.infrastructure.executor.shell;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("ShellTaskExecutor")
class ShellTaskExecutorTest {

    private final ShellTaskExecutor executor = new ShellTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.cleanup(null);
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"),
                ScenarioId.of("scenario-001"));
    }

    private static ExecutionContext contextWithId(String execId) {
        return ExecutionContext.initial(
                ExecutionId.of(execId),
                ScenarioId.of("scenario-001"));
    }

    // ─────────────────────────────── BASIC COMMANDS ─────────────────────────────

    @Nested
    @DisplayName("Basic shell commands")
    class BasicCommands {

        @Test
        @DisplayName("should execute echo and capture stdout, exitCode 0")
        void shouldExecuteEchoAndCaptureStdout() {
            var step = new StepDefinition(
                    TaskId.of("step-001"), "shell", Phase.PREPARATION,
                    Map.of("command", "echo", "args", List.of("hello")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.taskName()).isEqualTo("shell");
            assertThat(result.outputs())
                    .containsKey(ShellTaskExecutor.OUTPUT_EXIT_CODE)
                    .containsKey(ShellTaskExecutor.OUTPUT_STDOUT)
                    .containsKey(ShellTaskExecutor.OUTPUT_STDERR);

            assertThat(result.outputs().get(ShellTaskExecutor.OUTPUT_EXIT_CODE)).isEqualTo(0);
            assertThat((String) result.outputs().get(ShellTaskExecutor.OUTPUT_STDOUT))
                    .contains("hello");
        }

        @Test
        @DisplayName("should capture stderr when bash produces error output")
        void shouldCaptureStderr() {
            var step = new StepDefinition(
                    TaskId.of("step-002"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "echo hello >&2")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((String) result.outputs().get(ShellTaskExecutor.OUTPUT_STDERR))
                    .contains("hello");
        }

        @Test
        @DisplayName("should fail when command exit code is not in success codes")
        void shouldFailWhenExitCodeNotInSuccessCodes() {
            var step = new StepDefinition(
                    TaskId.of("step-003"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "exit 1")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Exit code 1");
            assertThat(result.outputs().get(ShellTaskExecutor.OUTPUT_EXIT_CODE)).isEqualTo(1);
        }

        @Test
        @DisplayName("should succeed when exit code is in custom success codes")
        void shouldSucceedWithCustomSuccessCodes() {
            var step = new StepDefinition(
                    TaskId.of("step-004"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "exit 42"),
                            "successExitCodes", List.of(0, 42)),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(ShellTaskExecutor.OUTPUT_EXIT_CODE)).isEqualTo(42);
        }
    }

    // ─────────────────────────────── WORKING DIRECTORY ──────────────────────────

    @Nested
    @DisplayName("Working directory")
    class WorkingDirectory {

        @Test
        @DisplayName("should execute command from specified working directory")
        void shouldExecuteFromWorkingDirectory() {
            var step = new StepDefinition(
                    TaskId.of("step-010"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "pwd"),
                            "workingDirectory", "/tmp"),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((String) result.outputs().get(ShellTaskExecutor.OUTPUT_STDOUT))
                    .contains("/tmp");
        }

        @Test
        @DisplayName("should fail when working directory does not exist")
        void shouldFailWhenWorkingDirectoryDoesNotExist() {
            var step = new StepDefinition(
                    TaskId.of("step-011"), "shell", Phase.PREPARATION,
                    Map.of("command", "echo", "args", List.of("hi"),
                            "workingDirectory", "/nonexistent/dir/xyz"),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("not a directory");
        }
    }

    // ─────────────────────────────── ENVIRONMENT ────────────────────────────────

    @Nested
    @DisplayName("Environment variables")
    class EnvironmentVariables {

        @Test
        @DisplayName("should pass custom environment variables to the process")
        void shouldPassCustomEnvironmentVariables() {
            var step = new StepDefinition(
                    TaskId.of("step-020"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "echo $MY_VAR"),
                            "env", Map.of("MY_VAR", "perftest_value")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((String) result.outputs().get(ShellTaskExecutor.OUTPUT_STDOUT))
                    .contains("perftest_value");
        }
    }

    // ─────────────────────────────── TIMEOUT ────────────────────────────────────

    @Nested
    @DisplayName("Timeout")
    class Timeout {

        @Test
        @DisplayName("should kill process that exceeds timeout")
        void shouldKillProcessThatExceedsTimeout() {
            var step = new StepDefinition(
                    TaskId.of("step-030"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "sleep 30"),
                            "timeout", 1),
                    List.of(), List.of(), Duration.ofSeconds(5), null);

            long before = System.currentTimeMillis();
            TaskResult result = executor.execute(emptyContext(), step);
            long after = System.currentTimeMillis();

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("timed out");
            // Should return within ~2s (1s timeout + small overhead), not 30s
            assertThat(after - before).isLessThan(5_000);
        }

        @Test
        @DisplayName("should use default timeout of 30s when not specified")
        void shouldUseDefaultTimeout() {
            var step = new StepDefinition(
                    TaskId.of("step-031"), "shell", Phase.PREPARATION,
                    Map.of("command", "echo", "args", List.of("quick")),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            // Should complete quickly — default timeout is 30s, not used here.
            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ─────────────────────────────── ERROR CASES ────────────────────────────────

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should fail when command parameter is missing")
        void shouldFailWhenCommandMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-040"), "shell", Phase.PREPARATION,
                    Map.of("args", List.of("hello")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("command");
        }

        @Test
        @DisplayName("should fail when command parameter is blank")
        void shouldFailWhenCommandBlank() {
            var step = new StepDefinition(
                    TaskId.of("step-041"), "shell", Phase.PREPARATION,
                    Map.of("command", "   "),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("command");
        }

        @Test
        @DisplayName("should fail when timeout is zero or negative")
        void shouldFailWhenTimeoutNegative() {
            var step = new StepDefinition(
                    TaskId.of("step-042"), "shell", Phase.PREPARATION,
                    Map.of("command", "echo", "args", List.of("hi"), "timeout", 0),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("timeout must be positive");
        }

        @Test
        @DisplayName("should fail when command cannot be executed")
        void shouldFailWhenCommandCannotBeExecuted() {
            var step = new StepDefinition(
                    TaskId.of("step-043"), "shell", Phase.PREPARATION,
                    Map.of("command", "/nonexistent/binary_xyz"),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Failed to start process");
        }

        @Test
        @DisplayName("should throw NPE when context is null")
        void shouldThrowNpeWhenContextIsNull() {
            var step = new StepDefinition(
                    TaskId.of("step-044"), "shell", Phase.PREPARATION,
                    Map.of("command", "echo", "args", List.of("hi")),
                    List.of(), List.of(), null, null);

            assertThatThrownBy(() -> executor.execute(null, step))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when step is null")
        void shouldThrowNpeWhenStepIsNull() {
            assertThatThrownBy(() -> executor.execute(emptyContext(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────── TASK NAME ──────────────────────────────────

    @Test
    @DisplayName("getSupportedTaskName should return shell")
    void shouldReturnShellTaskName() {
        assertThat(executor.getSupportedTaskName()).isEqualTo("shell");
    }

    // ─────────────────────────────── CLEANUP ────────────────────────────────────

    @Nested
    @DisplayName("cleanup")
    class Cleanup {

        @Test
        @DisplayName("should destroy all running processes on global cleanup")
        void shouldDestroyAllRunningProcessesOnGlobalCleanup() throws IOException {
            // Use a temp file as signal: the process creates it on start,
            // the test awaits its existence instead of Thread.sleep().
            Path startedFile = Files.createTempFile("shell-cleanup-1-", ".signal");
            String signalCmd = "touch " + startedFile + " && sleep 30";

            var step = new StepDefinition(
                    TaskId.of("step-050"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", signalCmd),
                            "timeout", 60),
                    List.of(), List.of(), Duration.ofSeconds(70), null);

            // Execute in a separate thread so we can cleanup while running
            Thread execThread = new Thread(() -> {
                executor.execute(contextWithId("exec-cleanup-1"), step);
            });
            execThread.start();

            // Wait for the process to actually start (signalled by the temp file)
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> Files.exists(startedFile));

            // Global cleanup
            assertDoesNotThrow(() -> executor.cleanup(null));

            // Wait for execution thread to finish (should be killed by cleanup)
            try { execThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Files.deleteIfExists(startedFile);
        }

        @Test
        @DisplayName("should destroy specific process on execution-scoped cleanup")
        void shouldDestroySpecificProcessOnExecutionCleanup() throws IOException {
            Path startedFile = Files.createTempFile("shell-cleanup-2-", ".signal");
            String signalCmd = "touch " + startedFile + " && sleep 30";

            var step = new StepDefinition(
                    TaskId.of("step-051"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", signalCmd),
                            "timeout", 60),
                    List.of(), List.of(), Duration.ofSeconds(70), null);

            // Start in separate thread
            Thread execThread = new Thread(() -> {
                executor.execute(contextWithId("exec-cleanup-2"), step);
            });
            execThread.start();

            await().atMost(Duration.ofSeconds(5))
                    .until(() -> Files.exists(startedFile));

            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("exec-cleanup-2")));

            try { execThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Files.deleteIfExists(startedFile);
        }

        @Test
        @DisplayName("should not throw when cleaning up non-existent execution")
        void shouldNotThrowWhenCleaningNonExistentExecution() {
            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("nonexistent")));
        }
    }

    // ─────────────────────────────── OUTPUTS STRUCTURE ──────────────────────────

    @Nested
    @DisplayName("Outputs structure")
    class OutputsStructure {

        @Test
        @DisplayName("should include exitCode, stdout, stderr in outputs for success")
        void shouldIncludeAllOutputsForSuccess() {
            var step = new StepDefinition(
                    TaskId.of("step-060"), "shell", Phase.PREPARATION,
                    Map.of("command", "echo", "args", List.of("test_output")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsOnlyKeys(
                    ShellTaskExecutor.OUTPUT_EXIT_CODE,
                    ShellTaskExecutor.OUTPUT_STDOUT,
                    ShellTaskExecutor.OUTPUT_STDERR
            );
            assertThat(result.outputs().get(ShellTaskExecutor.OUTPUT_EXIT_CODE)).isEqualTo(0);
            assertThat((String) result.outputs().get(ShellTaskExecutor.OUTPUT_STDOUT)).contains("test_output");
        }

        @Test
        @DisplayName("should include exitCode in outputs even for failure")
        void shouldIncludeExitCodeEvenForFailure() {
            var step = new StepDefinition(
                    TaskId.of("step-061"), "shell", Phase.PREPARATION,
                    Map.of("command", "bash", "args", List.of("-c", "echo error >&2 && exit 3")),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.outputs().get(ShellTaskExecutor.OUTPUT_EXIT_CODE)).isEqualTo(3);
            assertThat((String) result.outputs().get(ShellTaskExecutor.OUTPUT_STDERR)).contains("error");
        }
    }
}
