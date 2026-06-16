package com.performance.platform.infrastructure.executor.docker;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("DockerTaskExecutor")
class DockerTaskExecutorTest {

    /**
     * Fake DockerClient for unit testing — avoids Mockito/ByteBuddy
     * incompatibilities with Java 25.
     */
    static class FakeDockerClient implements DockerClient {

        private final List<String> pulledImages = new ArrayList<>();
        private final List<String> runResults = new ArrayList<>();
        private final List<String> stoppedContainers = new ArrayList<>();

        // Configurable behavior
        private boolean running = true;
        private int runIndex = 0;

        /** Next call to runContainer returns this ID. */
        void setNextContainerId(String containerId) {
            runResults.add(containerId);
        }

        /** Make isRunning() return this value. */
        void setRunning(boolean running) {
            this.running = running;
        }

        // Counts for assertions
        int pullCount() { return pulledImages.size(); }
        int runCount() { return runResults.size() - runIndex + 1; }
        int stopCount() { return stoppedContainers.size(); }
        String lastStopped() {
            return stoppedContainers.isEmpty() ? null : stoppedContainers.get(stoppedContainers.size() - 1);
        }
        List<String> allStopped() { return List.copyOf(stoppedContainers); }
        String pulledImage(int index) { return pulledImages.get(index); }

        // DockerClient implementation

        @Override
        public void pullImage(String image) {
            pulledImages.add(image);
        }

        @Override
        public String runContainer(String image, String containerName,
                                   Map<String, String> ports, Map<String, String> env) {
            if (runIndex >= runResults.size()) {
                throw new DockerException("No container ID configured (runResults exhausted)");
            }
            String id = runResults.get(runIndex++);
            if (id.startsWith("FAIL:")) {
                throw new DockerException(id.substring(5));
            }
            return id;
        }

        @Override
        public void stopContainer(String containerId) {
            if (containerId.startsWith("FAIL_STOP:")) {
                throw new DockerException(containerId.substring(10));
            }
            stoppedContainers.add(containerId);
        }

        @Override
        public boolean isRunning(String containerId) {
            return running;
        }
    }

    private FakeDockerClient fakeClient;
    private DockerTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.cleanup(null);
        }
    }

    private void initExecutor() {
        fakeClient = new FakeDockerClient();
        executor = new DockerTaskExecutor(fakeClient);
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

    // ─────────────────────────────── START ──────────────────────────────────

    @Nested
    @DisplayName("START action")
    class StartAction {

        @Test
        @DisplayName("should start container and return containerId and status")
        void shouldStartContainerAndReturnContainerId() {
            initExecutor();
            fakeClient.setNextContainerId("abc123def");

            var step = new StepDefinition(
                    TaskId.of("step-001"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "nginx:latest",
                            "containerName", "my-nginx"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.taskName()).isEqualTo("docker");
            assertThat(result.outputs())
                    .containsEntry(DockerTaskExecutor.OUTPUT_CONTAINER_ID, "abc123def")
                    .containsEntry(DockerTaskExecutor.OUTPUT_STATUS, "running");
        }

        @Test
        @DisplayName("should fail when image parameter is missing")
        void shouldFailWhenImageMissing() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-003"), "docker", Phase.PREPARATION,
                    Map.of("action", "START"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("image");
        }

        @Test
        @DisplayName("should fail when image parameter is blank")
        void shouldFailWhenImageBlank() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-004"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "   "),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("image");
        }

        @Test
        @DisplayName("should fail when DockerClient throws DockerException")
        void shouldFailWhenDockerClientThrows() {
            initExecutor();
            fakeClient.setNextContainerId("FAIL:Connection refused");

            var step = new StepDefinition(
                    TaskId.of("step-005"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "nginx:latest"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Connection refused");
        }
    }

    // ─────────────────────────────── HEALTH CHECK ────────────────────────────

    @Nested
    @DisplayName("Health check")
    class HealthCheck {

        @Test
        @DisplayName("should succeed when container becomes healthy within timeout")
        void shouldSucceedWhenContainerBecomesHealthy() {
            initExecutor();
            fakeClient.setNextContainerId("abc123");
            fakeClient.setRunning(true);

            var step = new StepDefinition(
                    TaskId.of("step-010"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "nginx:latest",
                            "waitForHealthCheck", true, "healthCheckTimeout", 5),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(DockerTaskExecutor.OUTPUT_STATUS))
                    .isEqualTo("running");
        }

        @Test
        @DisplayName("should fail when container does not become healthy within timeout")
        void shouldFailWhenContainerNotHealthyWithinTimeout() {
            initExecutor();
            fakeClient.setNextContainerId("abc456");
            fakeClient.setRunning(false); // never healthy

            var step = new StepDefinition(
                    TaskId.of("step-011"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "nginx:latest",
                            "waitForHealthCheck", true, "healthCheckTimeout", 1),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("healthy");
        }

        @Test
        @DisplayName("should not check health when waitForHealthCheck is false")
        void shouldNotCheckHealthWhenWaitForHealthCheckFalse() {
            initExecutor();
            fakeClient.setNextContainerId("abc789");

            var step = new StepDefinition(
                    TaskId.of("step-012"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "nginx:latest",
                            "waitForHealthCheck", false),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(DockerTaskExecutor.OUTPUT_CONTAINER_ID))
                    .isEqualTo("abc789");
        }
    }

    // ─────────────────────────────── STOP ────────────────────────────────────

    @Nested
    @DisplayName("STOP action")
    class StopAction {

        @Test
        @DisplayName("should stop specific container by containerId param")
        void shouldStopSpecificContainer() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-020"), "docker", Phase.PREPARATION,
                    Map.of("action", "STOP", "containerId", "abc123"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(DockerTaskExecutor.OUTPUT_STATUS))
                    .isEqualTo("stopped");
            assertThat(fakeClient.lastStopped()).isEqualTo("abc123");
        }

        @Test
        @DisplayName("should stop all containers for execution when no containerId given")
        void shouldStopAllContainersForExecution() {
            initExecutor();
            fakeClient.setNextContainerId("c1");
            fakeClient.setNextContainerId("c2");

            var startStep = new StepDefinition(
                    TaskId.of("step-021a"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "busybox"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            ExecutionContext ctx = contextWithId("exec-stop-all");
            executor.execute(ctx, startStep);
            executor.execute(ctx, startStep);

            var stopStep = new StepDefinition(
                    TaskId.of("step-021b"), "docker", Phase.PREPARATION,
                    Map.of("action", "STOP"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(ctx, stopStep);

            assertThat(result.isSuccess()).isTrue();
            assertThat(fakeClient.allStopped()).containsExactly("c1", "c2");
        }

        @Test
        @DisplayName("should succeed when no containers to stop")
        void shouldSucceedWhenNoContainersToStop() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-022"), "docker", Phase.PREPARATION,
                    Map.of("action", "STOP"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(fakeClient.stopCount()).isZero();
        }
    }

    // ─────────────────────────────── PULL ────────────────────────────────────

    @Nested
    @DisplayName("PULL action")
    class PullAction {

        @Test
        @DisplayName("should pull image successfully")
        void shouldPullImageSuccessfully() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-030"), "docker", Phase.PREPARATION,
                    Map.of("action", "PULL", "image", "nginx:latest"),
                    List.of(), List.of(), Duration.ofSeconds(120), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs())
                    .containsEntry(DockerTaskExecutor.OUTPUT_IMAGE, "nginx:latest")
                    .containsEntry(DockerTaskExecutor.OUTPUT_STATUS, "pulled");
            assertThat(fakeClient.pullCount()).isEqualTo(1);
            assertThat(fakeClient.pulledImage(0)).isEqualTo("nginx:latest");
        }

        @Test
        @DisplayName("should fail when image is missing for PULL")
        void shouldFailWhenImageMissingForPull() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-031"), "docker", Phase.PREPARATION,
                    Map.of("action", "PULL"),
                    List.of(), List.of(), Duration.ofSeconds(120), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("image");
        }

        @Test
        @DisplayName("should fail when DockerClient throws on pull")
        void shouldFailWhenDockerClientThrowsOnPull() {
            // Use a custom fake that throws on pullImage
            var throwingClient = new DockerClient() {
                @Override
                public void pullImage(String image) {
                    throw new DockerException("Registry unreachable");
                }

                @Override
                public String runContainer(String image, String containerName,
                                           Map<String, String> ports, Map<String, String> env) {
                    return "unused";
                }

                @Override
                public void stopContainer(String containerId) {
                    // no-op
                }

                @Override
                public boolean isRunning(String containerId) {
                    return true;
                }
            };
            executor = new DockerTaskExecutor(throwingClient);

            var step = new StepDefinition(
                    TaskId.of("step-032"), "docker", Phase.PREPARATION,
                    Map.of("action", "PULL", "image", "broken:latest"),
                    List.of(), List.of(), Duration.ofSeconds(120), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Registry unreachable");
        }
    }

    // ─────────────────────────────── ERROR CASES ──────────────────────────────

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should fail for unknown action")
        void shouldFailForUnknownAction() {
            initExecutor();

            var step = new StepDefinition(
                    TaskId.of("step-040"), "docker", Phase.PREPARATION,
                    Map.of("action", "RESTART", "image", "nginx:latest"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown action")
                    .contains("RESTART");
        }

        @Test
        @DisplayName("should default action to START when not specified")
        void shouldDefaultActionToStart() {
            initExecutor();
            fakeClient.setNextContainerId("def789");

            var step = new StepDefinition(
                    TaskId.of("step-041"), "docker", Phase.PREPARATION,
                    Map.of("image", "busybox"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(DockerTaskExecutor.OUTPUT_CONTAINER_ID))
                    .isEqualTo("def789");
        }

        @Test
        @DisplayName("should throw NPE when context is null")
        void shouldThrowNpeWhenContextIsNull() {
            initExecutor();
            var step = new StepDefinition(
                    TaskId.of("step-042"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "nginx"),
                    List.of(), List.of(), null, null);

            assertThatThrownBy(() -> executor.execute(null, step))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when step is null")
        void shouldThrowNpeWhenStepIsNull() {
            initExecutor();
            assertThatThrownBy(() -> executor.execute(emptyContext(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when dockerClient is null")
        void shouldThrowNpeWhenDockerClientIsNull() {
            assertThatThrownBy(() -> new DockerTaskExecutor(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────── TASK NAME ────────────────────────────────

    @Test
    @DisplayName("getSupportedTaskName should return docker")
    void shouldReturnDockerTaskName() {
        initExecutor();
        assertThat(executor.getSupportedTaskName()).isEqualTo("docker");
    }

    // ─────────────────────────────── CLEANUP ──────────────────────────────────

    @Nested
    @DisplayName("cleanup")
    class CleanupTests {

        @Test
        @DisplayName("should stop all containers on global cleanup")
        void shouldStopAllContainersOnGlobalCleanup() {
            initExecutor();
            fakeClient.setNextContainerId("c-a");
            fakeClient.setNextContainerId("c-b");

            var startStep = new StepDefinition(
                    TaskId.of("step-050"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "busybox"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            executor.execute(contextWithId("exec-A"), startStep);
            executor.execute(contextWithId("exec-B"), startStep);

            assertDoesNotThrow(() -> executor.cleanup(null));

            assertThat(fakeClient.allStopped()).contains("c-a", "c-b");
        }

        @Test
        @DisplayName("should stop only containers for specific execution")
        void shouldStopContainersForSpecificExecution() {
            initExecutor();
            fakeClient.setNextContainerId("c-aa");
            fakeClient.setNextContainerId("c-bb");

            var startStep = new StepDefinition(
                    TaskId.of("step-051"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "busybox"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            executor.execute(contextWithId("exec-X"), startStep);
            executor.execute(contextWithId("exec-Y"), startStep);

            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("exec-X")));

            assertThat(fakeClient.allStopped()).containsExactly("c-aa");
        }

        @Test
        @DisplayName("should not throw when cleaning up non-existent execution")
        void shouldNotThrowWhenCleaningNonExistentExecution() {
            initExecutor();
            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("nonexistent")));
        }

        @Test
        @DisplayName("should swallow DockerException during cleanup stop")
        void shouldSwallowDockerExceptionDuringCleanup() {
            initExecutor();
            // Use a client that throws on stopContainer
            var throwingClient = new DockerClient() {
                @Override
                public void pullImage(String image) { /* no-op */ }

                @Override
                public String runContainer(String image, String containerName,
                                           Map<String, String> ports, Map<String, String> env) {
                    return "c-err";
                }

                @Override
                public void stopContainer(String containerId) {
                    throw new DockerException("already stopped");
                }

                @Override
                public boolean isRunning(String containerId) {
                    return false;
                }
            };
            executor = new DockerTaskExecutor(throwingClient);

            var startStep = new StepDefinition(
                    TaskId.of("step-053"), "docker", Phase.PREPARATION,
                    Map.of("action", "START", "image", "busybox"),
                    List.of(), List.of(), Duration.ofSeconds(60), null);

            executor.execute(contextWithId("exec-err"), startStep);

            // Should not throw despite stopContainer throwing
            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("exec-err")));
        }
    }
}
