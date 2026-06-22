package com.performance.platform.agent.e2e;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.registration.TransportAgentRegistration;
import com.performance.platform.agent.runtime.DistributedAgentRuntime;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests E2E de coordination multi-agent.
 */
@DisplayName("Agent Coordination E2E")
class AgentCoordinationE2ETest {

    private InMemoryExecutionTransport transport;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        transport.connect();
    }

    @AfterEach
    void tearDown() {
        transport.disconnect();
    }

    @Nested
    @DisplayName("Single agent task execution")
    class SingleAgent {

        @Test
        @DisplayName("E2E-A-01: Single agent claims and completes supported task")
        @Timeout(value = 30)
        void singleAgentCompletesSupportedTask() {
            AgentDescriptor desc = buildDescriptor("agent-1", Set.of("database"));
            var filter = new DefaultTaskSpecializationFilter(
                    Set.of("database"), desc.id());
            var regPort = new TransportAgentRegistration(transport);

            var executor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    return TaskResult.success(step.id(), "database", Duration.ofMillis(5),
                            Map.of("rows", 100));
                }
                @Override
                public String getSupportedTaskName() { return "database"; }
            };

            var runtime = new DistributedAgentRuntime(
                    transport, filter, regPort, desc,
                    Duration.ofSeconds(5), Duration.ofSeconds(60),
                    List.of(executor), List.of());

            var events = captureExecutionEvents();
            runtime.start();

            var request = buildTaskRequest("database", ExecutionId.generate());
            transport.dispatchTask(request);

            await().atMost(15, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
            runtime.stop();
        }

        @Test
        @DisplayName("E2E-A-02: Unsupported task is ignored")
        @Timeout(value = 30)
        void unsupportedTaskIgnored() {
            AgentDescriptor desc = buildDescriptor("agent-db", Set.of("database"));
            var filter = new DefaultTaskSpecializationFilter(
                    Set.of("database"), desc.id());
            var regPort = new TransportAgentRegistration(transport);

            var runtime = new DistributedAgentRuntime(
                    transport, filter, regPort, desc,
                    Duration.ofSeconds(5), Duration.ofSeconds(60),
                    List.of(), List.of());

            var events = captureExecutionEvents();
            runtime.start();

            var request = buildTaskRequest("completely-unknown", ExecutionId.generate());
            transport.dispatchTask(request);

            await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
            long claims = events.stream()
                    .filter(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())).count();
            assertThat(claims).isZero();

            runtime.stop();
        }

        @Test
        @DisplayName("E2E-A-03: Task failure produces TASK_FAILED event")
        @Timeout(value = 30)
        void taskFailureProducesFailedEvent() {
            AgentDescriptor desc = buildDescriptor("fail-agent", Set.of("database"));
            var filter = new DefaultTaskSpecializationFilter(
                    Set.of("database"), desc.id());
            var regPort = new TransportAgentRegistration(transport);

            var executor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    return TaskResult.failed(step.id(), "database", Duration.ofMillis(5),
                            "Connection refused", new RuntimeException("Connection refused"));
                }
                @Override
                public String getSupportedTaskName() { return "database"; }
            };

            var runtime = new DistributedAgentRuntime(
                    transport, filter, regPort, desc,
                    Duration.ofSeconds(5), Duration.ofSeconds(60),
                    List.of(executor), List.of());

            var events = captureExecutionEvents();
            runtime.start();

            var request = buildTaskRequest("database", ExecutionId.generate());
            transport.dispatchTask(request);

            await().atMost(15, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_FAILED.equals(e.eventType())));

            var failed = events.stream()
                    .filter(e -> ExecutionEvent.TASK_FAILED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            assertThat(failed.payload().get("error").toString()).contains("Connection refused");

            runtime.stop();
        }
    }

    @Nested
    @DisplayName("Agent lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("E2E-A-10: Agent transitions OFFLINE -> IDLE on start")
        @Timeout(value = 30)
        void agentStartupTransition() {
            DistributedAgentRuntime runtime = buildRuntime("startup", Set.of("shell"), List.of());
            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);
            runtime.start();
            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
            runtime.stop();
        }

        @Test
        @DisplayName("E2E-A-11: Agent transitions IDLE -> OFFLINE on stop")
        @Timeout(value = 30)
        void agentShutdownTransition() {
            DistributedAgentRuntime runtime = buildRuntime("shutdown", Set.of("shell"), List.of());
            runtime.start();
            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
            runtime.stop();
            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);
        }
    }

    @Nested
    @DisplayName("Resource cleanup")
    class Cleanup {

        @Test
        @DisplayName("E2E-A-20: Cleaner invoked on ScenarioRestartSignal")
        @Timeout(value = 30)
        void cleanerInvokedOnRestart() {
            var cleanupCount = new AtomicInteger(0);
            StatefulResourceCleaner cleaner = (executionId) -> cleanupCount.incrementAndGet();

            DistributedAgentRuntime runtime = buildRuntime("cleaner", Set.of("shell"),
                    List.of(cleaner));
            runtime.start();

            var signal = new com.performance.platform.domain.event.ScenarioRestartSignal(
                    SignalId.generate(), null,
                    "test restart", Instant.now());
            transport.broadcastSignal(signal);

            await().atMost(5, TimeUnit.SECONDS).until(() -> cleanupCount.get() >= 1);
            assertThat(cleanupCount.get()).isEqualTo(1);

            runtime.stop();
        }
    }

    @Nested
    @DisplayName("Concurrent execution")
    class Concurrent {

        @Test
        @DisplayName("E2E-A-30: Multiple tasks dispatched are all executed")
        @Timeout(value = 60)
        void concurrentTaskDispatch() {
            var executor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    try { Thread.sleep(100); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return TaskResult.success(step.id(), "shell", Duration.ofMillis(100), Map.of());
                }
                @Override
                public String getSupportedTaskName() { return "shell"; }
            };

            DistributedAgentRuntime runtime = buildRuntimeWithExecutor(
                    "concurrent", Set.of("shell"), executor);
            var events = captureExecutionEvents();
            runtime.start();

            int taskCount = 5;
            for (int i = 0; i < taskCount; i++) {
                transport.dispatchTask(buildTaskRequest("shell", ExecutionId.generate()));
            }

            await().atMost(30, TimeUnit.SECONDS).until(() ->
                    events.stream().filter(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType()))
                            .count() >= taskCount);

            runtime.stop();
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private AgentDescriptor buildDescriptor(String name, Set<String> supportedTasks) {
        return new AgentDescriptor(
                AgentId.generate(), name, "localhost", 9090,
                "http://localhost:9090/callback",
                supportedTasks, new AgentCapabilities(5, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(),
                Duration.ofSeconds(30));
    }

    private DistributedAgentRuntime buildRuntime(String name, Set<String> supportedTasks,
                                                  List<StatefulResourceCleaner> cleaners) {
        AgentDescriptor desc = buildDescriptor(name, supportedTasks);
        var filter = new DefaultTaskSpecializationFilter(
                supportedTasks, desc.id());
        var regPort = new TransportAgentRegistration(transport);

        return new DistributedAgentRuntime(
                transport, filter, regPort, desc,
                Duration.ofSeconds(5), Duration.ofSeconds(60),
                List.of(), cleaners);
    }

    private DistributedAgentRuntime buildRuntimeWithExecutor(String name,
                                                              Set<String> supportedTasks,
                                                              TaskExecutor executor) {
        AgentDescriptor desc = buildDescriptor(name, supportedTasks);
        var filter = new DefaultTaskSpecializationFilter(
                supportedTasks, desc.id());
        var regPort = new TransportAgentRegistration(transport);

        return new DistributedAgentRuntime(
                transport, filter, regPort, desc,
                Duration.ofSeconds(5), Duration.ofSeconds(60),
                List.of(executor), List.of());
    }

    private TaskExecutionRequest buildTaskRequest(String taskName, ExecutionId executionId) {
        var step = new StepDefinition(
                TaskId.of("task-" + UUID.randomUUID().toString().substring(0, 8)),
                taskName, Phase.PREPARATION, Map.of("key", "value"),
                List.of(), List.of(), Duration.ofSeconds(30), null);
        return new TaskExecutionRequest(
                MessageId.generate(), executionId, step,
                PartialExecutionContext.empty(executionId, ScenarioId.of("test-scenario")),
                Instant.now(), RetryPolicy.defaults());
    }

    private List<ExecutionEvent> captureExecutionEvents() {
        var events = new CopyOnWriteArrayList<ExecutionEvent>();
        transport.subscribe(events::add);
        return events;
    }
}
