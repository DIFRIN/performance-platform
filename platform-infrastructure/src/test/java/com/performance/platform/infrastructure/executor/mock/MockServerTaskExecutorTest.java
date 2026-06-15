package com.performance.platform.infrastructure.executor.mock;

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

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("MockServerTaskExecutor")
class MockServerTaskExecutorTest {

    private final MockServerTaskExecutor executor = new MockServerTaskExecutor();

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

    // ─────────────────────────────── EMBEDDED ───────────────────────────────

    @Nested
    @DisplayName("EMBEDDED START")
    class EmbeddedStart {

        @Test
        @DisplayName("should start WireMock and return port and url in outputs")
        void shouldStartWireMockAndReturnPortAndUrl() {
            var step = new StepDefinition(
                    TaskId.of("step-001"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.taskName()).isEqualTo("mock-server");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_PORT);
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_URL);
            assertThat(result.outputs().get(MockServerTaskExecutor.OUTPUT_PORT)).isInstanceOf(Integer.class);
            String url = (String) result.outputs().get(MockServerTaskExecutor.OUTPUT_URL);
            assertThat(url).startsWith("http://localhost:");
        }

        @Test
        @DisplayName("should start on custom port when port parameter is provided")
        void shouldStartOnCustomPort() {
            var step = new StepDefinition(
                    TaskId.of("step-002"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START", "port", 8765),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((Integer) result.outputs().get(MockServerTaskExecutor.OUTPUT_PORT)).isEqualTo(8765);
            assertThat((String) result.outputs().get(MockServerTaskExecutor.OUTPUT_URL))
                    .isEqualTo("http://localhost:8765");
        }

        @Test
        @DisplayName("should use default port 8090 when port parameter is omitted")
        void shouldUseDefaultPort() {
            var step = new StepDefinition(
                    TaskId.of("step-003"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((Integer) result.outputs().get(MockServerTaskExecutor.OUTPUT_PORT)).isEqualTo(8090);
        }

        @Test
        @DisplayName("should default deployment to EMBEDDED when not specified")
        void shouldDefaultDeploymentToEmbedded() {
            var step = new StepDefinition(
                    TaskId.of("step-004"), "mock-server", Phase.PREPARATION,
                    Map.of("action", "START"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_PORT);
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_URL);
        }
    }

    @Nested
    @DisplayName("EMBEDDED STOP")
    class EmbeddedStop {

        @Test
        @DisplayName("should stop running server successfully")
        void shouldStopRunningServer() {
            // Start first
            var startStep = new StepDefinition(
                    TaskId.of("step-010"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);
            executor.execute(emptyContext(), startStep);

            // Then stop
            var step = new StepDefinition(
                    TaskId.of("step-011"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "STOP"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should succeed even when no server is running (no-op)")
        void shouldSucceedWhenNoServerRunning() {
            var step = new StepDefinition(
                    TaskId.of("step-012"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "STOP"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("EMBEDDED RESET")
    class EmbeddedReset {

        @Test
        @DisplayName("should reset running server successfully")
        void shouldResetRunningServer() {
            // Start first
            var startStep = new StepDefinition(
                    TaskId.of("step-020"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);
            executor.execute(emptyContext(), startStep);

            // Then reset
            var step = new StepDefinition(
                    TaskId.of("step-021"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "RESET"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should fail when no server is running")
        void shouldFailWhenNoServerRunning() {
            var step = new StepDefinition(
                    TaskId.of("step-022"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "RESET"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("No running embedded WireMock");
        }
    }

    @Nested
    @DisplayName("EMBEDDED VERIFY")
    class EmbeddedVerify {

        @Test
        @DisplayName("should return total requests count for running server")
        void shouldReturnTotalRequestCount() {
            // Start first
            var startStep = new StepDefinition(
                    TaskId.of("step-030"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);
            executor.execute(emptyContext(), startStep);

            // Then verify
            var step = new StepDefinition(
                    TaskId.of("step-031"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "VERIFY"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_TOTAL_REQUESTS);
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_PORT);
            assertThat(result.outputs()).containsKey(MockServerTaskExecutor.OUTPUT_URL);
            assertThat(result.outputs().get(MockServerTaskExecutor.OUTPUT_TOTAL_REQUESTS)).isEqualTo(0L);
        }

        @Test
        @DisplayName("should fail when no server is running")
        void shouldFailWhenNoServerRunning() {
            var step = new StepDefinition(
                    TaskId.of("step-032"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "VERIFY"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("No running embedded WireMock");
        }
    }

    // ─────────────────────────────── EXTERNAL ───────────────────────────────

    @Nested
    @DisplayName("EXTERNAL")
    class ExternalMode {

        @Test
        @DisplayName("should fail START when externalUrl is missing")
        void shouldFailStartWhenExternalUrlMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-040"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EXTERNAL", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("externalUrl");
        }

        @Test
        @DisplayName("should fail START when external WireMock is unreachable")
        void shouldFailStartWhenUnreachable() {
            var step = new StepDefinition(
                    TaskId.of("step-041"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EXTERNAL", "action", "START",
                            "externalUrl", "http://127.0.0.1:19999"),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("not reachable");
        }

        @Test
        @DisplayName("should succeed STOP as no-op for external")
        void shouldSucceedStopAsNoop() {
            var step = new StepDefinition(
                    TaskId.of("step-042"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EXTERNAL", "action", "STOP",
                            "externalUrl", "http://localhost:8090"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should fail VERIFY when external WireMock is unreachable")
        void shouldFailVerifyWhenUnreachable() {
            var step = new StepDefinition(
                    TaskId.of("step-043"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EXTERNAL", "action", "VERIFY",
                            "externalUrl", "http://127.0.0.1:19999"),
                    null, null, Duration.ofSeconds(2), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        }
    }

    // ─────────────────────────────── ERROR CASES ───────────────────────────────

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should fail with invalid deployment mode")
        void shouldFailWithInvalidDeployment() {
            var step = new StepDefinition(
                    TaskId.of("step-050"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "INVALID", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Invalid deployment mode");
        }

        @Test
        @DisplayName("should fail with unknown action")
        void shouldFailWithUnknownAction() {
            var step = new StepDefinition(
                    TaskId.of("step-051"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "RESTART"),
                    null, null, Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown action");
        }

        @Test
        @DisplayName("should throw NPE when context is null")
        void shouldThrowNpeWhenContextIsNull() {
            var step = new StepDefinition(
                    TaskId.of("step-052"), "mock-server", Phase.PREPARATION,
                    Map.of("action", "START"),
                    null, null, null, null);

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

    // ─────────────────────────────── GET SUPPORTED TASK NAME ───────────────────────────────

    @Test
    @DisplayName("getSupportedTaskName should return mock-server")
    void shouldReturnMockServerTaskName() {
        assertThat(executor.getSupportedTaskName()).isEqualTo("mock-server");
    }

    // ─────────────────────────────── CLEANUP ───────────────────────────────

    @Nested
    @DisplayName("cleanup")
    class Cleanup {

        @Test
        @DisplayName("should stop all running servers on global cleanup")
        void shouldStopAllRunningServersOnGlobalCleanup() {
            // Start two servers
            var step1 = new StepDefinition(
                    TaskId.of("step-060"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);
            executor.execute(contextWithId("exec-cleanup-1"), step1);

            var step2 = new StepDefinition(
                    TaskId.of("step-061"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);
            executor.execute(contextWithId("exec-cleanup-2"), step2);

            // Global cleanup (null executionId)
            assertDoesNotThrow(() -> executor.cleanup(null));
        }

        @Test
        @DisplayName("should stop specific server on execution-scoped cleanup")
        void shouldStopSpecificServerOnExecutionCleanup() {
            // Start a server
            var step = new StepDefinition(
                    TaskId.of("step-062"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START"),
                    null, null, Duration.ofSeconds(10), null);
            executor.execute(contextWithId("exec-cleanup-3"), step);

            // Scoped cleanup
            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("exec-cleanup-3")));
        }

        @Test
        @DisplayName("should not throw when cleaning up non-existent execution")
        void shouldNotThrowWhenCleaningNonExistentExecution() {
            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("nonexistent")));
        }
    }

    // ─────────────────────────────── MULTIPLE EXECUTIONS ───────────────────────────────

    @Nested
    @DisplayName("Multiple execution contexts")
    class MultipleExecutions {

        @Test
        @DisplayName("should isolate servers by execution context")
        void shouldIsolateServersByExecutionContext() {
            // Start with exec-001 on port 8092
            var step1 = new StepDefinition(
                    TaskId.of("step-070"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START", "port", 8092),
                    null, null, Duration.ofSeconds(10), null);
            TaskResult result1 = executor.execute(
                    contextWithId("exec-multi-1"), step1);
            assertThat(result1.isSuccess()).isTrue();

            // Start with exec-002 on port 8093 (different port)
            var step2 = new StepDefinition(
                    TaskId.of("step-071"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "START", "port", 8093),
                    null, null, Duration.ofSeconds(10), null);
            TaskResult result2 = executor.execute(
                    contextWithId("exec-multi-2"), step2);
            assertThat(result2.isSuccess()).isTrue();

            // Cleanup exec-001 only
            executor.cleanup(ExecutionId.of("exec-multi-1"));

            // exec-002 server should still be reachable via VERIFY
            var verifyStep = new StepDefinition(
                    TaskId.of("step-072"), "mock-server", Phase.PREPARATION,
                    Map.of("deployment", "EMBEDDED", "action", "VERIFY"),
                    null, null, Duration.ofSeconds(10), null);
            TaskResult verifyResult = executor.execute(
                    contextWithId("exec-multi-2"), verifyStep);
            assertThat(verifyResult.isSuccess()).isTrue();
        }
    }
}
