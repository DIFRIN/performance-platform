package com.performance.platform.infrastructure.executor.mock;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.scenario.ServiceScope;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.plugin.resource.Endpoint;
import com.performance.platform.plugin.resource.EndpointResolver;
import com.performance.platform.plugin.ServiceControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Le mock est une service step (ADR-054) : il demarre, reste vivant jusqu'au
 * STOP, puis publie port, URL et compteurs.
 */
@DisplayName("MockServerTaskExecutor")
class MockServerTaskExecutorTest {

    private final EndpointResolver noTargets = name -> null;
    private final MockServerTaskExecutor executor = new MockServerTaskExecutor(noTargets);

    @AfterEach
    void tearDown() {
        executor.cleanup(null);
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(ExecutionId.of("exec-001"), ScenarioId.of("scenario-001"));
    }

    private static StepDefinition serviceStep(TaskId id, Map<String, Object> parameters) {
        return new StepDefinition(id, "mock-server", null, parameters, null, null,
                ServiceScope.INJECTION, Duration.ZERO, FanOut.ONE, null,
                Duration.ofSeconds(10), null);
    }

    private static boolean portInUse(int port) {
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Controle de test : STOP immediat ou differe.
     */
    static final class TestControl implements ServiceControl {
        private final CountDownLatch stop = new CountDownLatch(1);
        private final AtomicBoolean requested = new AtomicBoolean(false);

        void stopNow() {
            requested.set(true);
            stop.countDown();
        }

        @Override
        public boolean awaitStop(Duration timeout) throws InterruptedException {
            return stop.await(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        @Override
        public void awaitStop() throws InterruptedException {
            stop.await();
        }

        @Override
        public boolean isStopRequested() {
            return requested.get();
        }
    }

    private TaskResult runService(StepDefinition step, ExecutionContext context) {
        var control = new TestControl();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            control.stopNow();
        });
        return executor.executeService(context, step, control);
    }

    @Nested
    @DisplayName("EMBEDDED")
    class Embedded {

        @Test
        @DisplayName("exposes port, url and received calls at STOP")
        void exposesOutputsAtStop() {
            var step = serviceStep(TaskId.of("step-1"),
                    Map.of("deployment", "EMBEDDED", "port", 0));

            TaskResult result = runService(step, emptyContext());

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get("port")).isNotNull();
            assertThat(result.outputs().get("url")).isNotNull();
            assertThat(result.outputs().get("totalRequests")).isEqualTo(0L);
            assertThat(result.outputs().get("receivedCalls")).isEqualTo(0L);
        }

        @Test
        @DisplayName("defaults to EMBEDDED deployment")
        void defaultsToEmbedded() {
            var step = serviceStep(TaskId.of("step-2"), Map.of("port", 0));

            TaskResult result = runService(step, emptyContext());

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs()).containsKey("url");
        }

        @Test
        @DisplayName("fails on invalid deployment mode")
        void invalidDeployment() {
            var step = serviceStep(TaskId.of("step-3"), Map.of("deployment", "INVALID"));

            TaskResult result = executor.executeService(emptyContext(), step, new TestControl());

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Invalid deployment mode");
        }

        @Test
        @DisplayName("stops the server when STOP is received")
        void stopsServerAtStop() {
            var step = serviceStep(TaskId.of("step-4"), Map.of("port", 0));

            TaskResult result = runService(step, emptyContext());
            int port = (Integer) result.outputs().get("port");

            assertThat(portInUse(port)).isFalse();
        }
    }

    @Nested
    @DisplayName("EXTERNAL")
    class External {

        private final EndpointResolver registryWithTarget = name -> "wiremock".equals(name)
                ? new Endpoint("http://127.0.0.1:19999",
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Map.of(), Map.of())
                : null;
        private final MockServerTaskExecutor externalExecutor = new MockServerTaskExecutor(registryWithTarget);

        @AfterEach
        void tearDown() {
            externalExecutor.cleanup(null);
        }

        @Test
        @DisplayName("fails when target parameter is missing")
        void missingTarget() {
            var step = serviceStep(TaskId.of("step-10"), Map.of("deployment", "EXTERNAL"));

            TaskResult result = externalExecutor.executeService(emptyContext(), step, new TestControl());

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("target");
        }

        @Test
        @DisplayName("fails when target is unknown")
        void unknownTarget() {
            var step = serviceStep(TaskId.of("step-11"),
                    Map.of("deployment", "EXTERNAL", "target", "unknown"));

            TaskResult result = externalExecutor.executeService(emptyContext(), step, new TestControl());

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown http-target");
        }

        @Test
        @DisplayName("fails gracefully when the external mock is unreachable")
        void unreachableTarget() {
            var step = serviceStep(TaskId.of("step-12"),
                    Map.of("deployment", "EXTERNAL", "target", "wiremock"));

            TaskResult result = externalExecutor.executeService(emptyContext(), step, new TestControl());

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("not reachable");
        }
    }

    @Nested
    @DisplayName("ACTION STEPS")
    class ActionSteps {

        @Test
        @DisplayName("execute() rejects an action step with a service-step message")
        void executeRejectsActionStep() {
            var step = new StepDefinition(TaskId.of("step-20"), "mock-server",
                    com.performance.platform.domain.scenario.Phase.PREPARATION,
                    Map.of("port", 0), null, null, Duration.ofSeconds(5), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("service step");
        }

        @Test
        @DisplayName("null context is rejected")
        void nullContext() {
            var step = serviceStep(TaskId.of("step-21"), Map.of());
            assertThrows(NullPointerException.class,
                    () -> executor.execute(null, step));
        }
    }

    @Test
    @DisplayName("supported task name")
    void supportedTaskName() {
        assertThat(executor.getSupportedTaskName()).isEqualTo("mock-server");
    }

    @Test
    @DisplayName("cleanup is safe with no running server")
    void cleanupIsSafe() {
        assertDoesNotThrow(() -> executor.cleanup(null));
    }
}
