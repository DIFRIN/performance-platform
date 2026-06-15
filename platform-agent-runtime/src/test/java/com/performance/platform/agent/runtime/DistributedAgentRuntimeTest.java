package com.performance.platform.agent.runtime;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskFilterResult;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.registration.RegistrationException;
import com.performance.platform.agent.restart.StatefulResourceCleaner;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.Subscription;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DisplayName("DistributedAgentRuntime")
class DistributedAgentRuntimeTest {

    // === Shared fixtures ===

    private InMemoryExecutionTransport transport;
    private AgentRegistrationPort registrationPort;
    private AgentId agentId;
    private AgentDescriptor descriptor;
    private Set<String> supportedTaskNames;
    private TaskSpecializationFilter filter;
    private Duration heartbeatInterval = Duration.ofSeconds(3);
    private Duration taskExecutionTimeout = Duration.ofSeconds(30);

    private DistributedAgentRuntime runtime;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        agentId = AgentId.generate();
        supportedTaskNames = Set.of("http-get", "kafka-produce");
        filter = new DefaultTaskSpecializationFilter(supportedTaskNames, agentId);
        registrationPort = new com.performance.platform.agent.registration.TransportAgentRegistration(transport);
        descriptor = new AgentDescriptor(
                agentId,
                "test-agent",
                "localhost",
                9090,
                "http://localhost:9090/callback",
                supportedTaskNames,
                new AgentCapabilities(5, "1.0.0"),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofSeconds(30)
        );
    }

    @AfterEach
    void tearDown() {
        if (runtime != null && runtime.isStarted() && !runtime.isStopped()) {
            runtime.stop();
        }
    }

    // === Factory methods ===

    private DistributedAgentRuntime createRuntime(List<TaskExecutor> taskExecutors) {
        return createRuntime(taskExecutors, List.of());
    }

    private DistributedAgentRuntime createRuntime(List<TaskExecutor> taskExecutors,
                                                  List<StatefulResourceCleaner> cleaners) {
        return new DistributedAgentRuntime(
                transport, filter, registrationPort, descriptor,
                heartbeatInterval, taskExecutionTimeout,
                taskExecutors != null ? taskExecutors : List.of(),
                cleaners != null ? cleaners : List.of()
        );
    }

    private DistributedAgentRuntime createRuntime() {
        return createRuntime(List.of(), List.of());
    }

    private TaskExecutionRequest createRequest(String taskName, ExecutionId executionId) {
        var step = new StepDefinition(
                TaskId.of("task-" + UUID.randomUUID().toString().substring(0, 8)),
                taskName,
                Phase.PREPARATION,
                Map.of("url", "http://example.com"),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                null
        );
        return new TaskExecutionRequest(
                MessageId.generate(),
                executionId,
                step,
                PartialExecutionContext.empty(executionId, ScenarioId.of("scenario-" + UUID.randomUUID().toString().substring(0, 8))),
                Instant.now(),
                com.performance.platform.domain.execution.RetryPolicy.defaults()
        );
    }

    private List<ExecutionEvent> captureExecutionEvents() {
        var events = new CopyOnWriteArrayList<ExecutionEvent>();
        transport.subscribe(events::add);
        return events;
    }

    // === Lifecycle tests ===

    @Nested
    @DisplayName("start/stop lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should transition OFFLINE → REGISTERING → IDLE on start")
        void shouldTransitionToIdleOnStart() {
            runtime = createRuntime();
            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);

            runtime.start();

            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
            assertThat(runtime.isStarted()).isTrue();
        }

        @Test
        @DisplayName("should transition IDLE → DRAINING → OFFLINE on stop")
        void shouldTransitionToOfflineOnStop() {
            runtime = createRuntime();
            runtime.start();
            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);

            runtime.stop();

            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);
            assertThat(runtime.isStopped()).isTrue();
        }

        @Test
        @DisplayName("should be idempotent on double start")
        void shouldBeIdempotentOnDoubleStart() {
            runtime = createRuntime();
            runtime.start();
            runtime.start(); // second start should be ignored

            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should be idempotent on double stop")
        void shouldBeIdempotentOnDoubleStop() {
            runtime = createRuntime();
            runtime.start();
            runtime.stop();
            runtime.stop(); // second stop should be ignored

            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);
        }

        @Test
        @DisplayName("should return correct descriptor after start")
        void shouldReturnCorrectDescriptorAfterStart() {
            runtime = createRuntime();
            runtime.start();

            var desc = runtime.getDescriptor();
            assertThat(desc.id()).isEqualTo(agentId);
            assertThat(desc.name()).isEqualTo("test-agent");
            assertThat(desc.state()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should throw RegistrationException if start fails")
        void shouldThrowIfStartFails() {
            var failingRegPort = new AgentRegistrationPort() {
                @Override
                public void register(AgentDescriptor d) {
                    throw new RegistrationException("forced failure");
                }
                @Override
                public void deregister(AgentId id) { /* no-op */ }
                @Override
                public void sendHeartbeat(AgentId id, AgentHeartbeat h) { /* no-op */ }
            };
            runtime = new DistributedAgentRuntime(
                    transport, filter, failingRegPort, descriptor,
                    heartbeatInterval, taskExecutionTimeout, List.of(), List.of()
            );

            assertThatThrownBy(() -> runtime.start())
                    .isInstanceOf(RegistrationException.class)
                    .hasMessageContaining("Failed to start agent");
            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);
        }
    }

    // === Task reception & filtering ===

    @Nested
    @DisplayName("task reception")
    class TaskReception {

        @Test
        @DisplayName("should claim task when filter returns Responsible")
        void shouldClaimTaskWhenResponsible() {
            var events = captureExecutionEvents();
            runtime = createRuntime();
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())));
            assertThat(runtime.processedMessageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should ignore task when filter returns NotResponsible")
        void shouldIgnoreTaskWhenNotResponsible() {
            var events = captureExecutionEvents();
            runtime = createRuntime();
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("unsupported-task", executionId);
            transport.dispatchTask(request);

            // No events should be published for unsupported tasks
            await().pollDelay(200, TimeUnit.MILLISECONDS).until(() -> true);
            assertThat(events).noneMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()));
            assertThat(runtime.processedMessageCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should ignore duplicate messageId (idempotence)")
        void shouldIgnoreDuplicateMessageId() {
            var events = captureExecutionEvents();
            runtime = createRuntime();
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            var messageId = request.id();

            // Première réception
            transport.dispatchTask(request);

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())
                            && messageId.equals(e.correlationId())));

            long claimCount1 = events.stream()
                    .filter(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())
                            && messageId.equals(e.correlationId()))
                    .count();

            // Deuxième réception — même messageId
            transport.dispatchTask(request);

            await().pollDelay(300, TimeUnit.MILLISECONDS).until(() -> true);

            long claimCount2 = events.stream()
                    .filter(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())
                            && messageId.equals(e.correlationId()))
                    .count();

            assertThat(claimCount2).isEqualTo(claimCount1); // pas de double claim
        }
    }

    // === Task execution ===

    @Nested
    @DisplayName("task execution")
    class TaskExecution {

        @Test
        @DisplayName("should execute supported task and publish TASK_COMPLETED")
        void shouldExecuteSupportedTaskAndPublishCompleted() {
            var events = captureExecutionEvents();

            var successExecutor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    return TaskResult.success(
                            step.id(), step.taskName(), Duration.ofMillis(10),
                            Map.of("result", "ok")
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = createRuntime(List.of(successExecutor));
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            var completedEvent = events.stream()
                    .filter(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            assertThat(completedEvent.executionId()).isEqualTo(executionId);
            assertThat(completedEvent.agentId()).isEqualTo(agentId);
            assertThat(completedEvent.payload()).containsEntry("status", "SUCCESS");
        }

        @Test
        @DisplayName("should publish TASK_FAILED when executor returns failed result")
        void shouldPublishTaskFailedWhenExecutorFails() {
            var events = captureExecutionEvents();

            var failingExecutor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    return TaskResult.failed(
                            step.id(), step.taskName(), Duration.ofMillis(5),
                            "execution error", null
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = createRuntime(List.of(failingExecutor));
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_FAILED.equals(e.eventType())));

            var failedEvent = events.stream()
                    .filter(e -> ExecutionEvent.TASK_FAILED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            assertThat(failedEvent.payload()).containsEntry("status", "FAILED");
            assertThat(failedEvent.payload()).containsEntry("error", "execution error");
        }

        @Test
        @DisplayName("should publish TASK_FAILED when no executor registered for taskName")
        void shouldPublishTaskFailedWhenNoExecutor() {
            var events = captureExecutionEvents();
            runtime = createRuntime(); // no executors
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_FAILED.equals(e.eventType())));

            var failedEvent = events.stream()
                    .filter(e -> ExecutionEvent.TASK_FAILED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            assertThat(failedEvent.payload()).containsKey("error");
        }

        @Test
        @DisplayName("should publish TASK_WORK_IN_PROGRESS during execution")
        void shouldPublishWorkInProgress() {
            var events = captureExecutionEvents();
            var latch = new CountDownLatch(1);
            // Use short timeout so progress reporting fires quickly (every 1s)
            var shortTimeout = Duration.ofSeconds(3);

            var slowExecutor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    try {
                        latch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return TaskResult.success(
                            step.id(), step.taskName(), Duration.ofSeconds(1),
                            Map.of("result", "slow-ok")
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = new DistributedAgentRuntime(
                    transport, filter, registrationPort, descriptor,
                    heartbeatInterval, shortTimeout, List.of(slowExecutor), List.of());
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            // Wait for work-in-progress events (progress every 1s with 3s timeout)
            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_WORK_IN_PROGRESS.equals(e.eventType())));

            latch.countDown(); // release the slow executor

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            assertThat(events.stream().anyMatch(e -> ExecutionEvent.TASK_WORK_IN_PROGRESS.equals(e.eventType())))
                    .isTrue();
            assertThat(events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())))
                    .isTrue();
        }

        @Test
        @DisplayName("should transition to IDLE after all tasks complete")
        void shouldTransitionToIdleAfterAllTasksComplete() {
            var events = captureExecutionEvents();

            var quickExecutor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    return TaskResult.success(
                            step.id(), step.taskName(), Duration.ofMillis(5),
                            Map.of("result", "ok")
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = createRuntime(List.of(quickExecutor));
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            // After task completion, agent should be back to IDLE
            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    runtime.getState() == AgentState.IDLE);
            assertThat(runtime.activeTaskCount()).isZero();
        }
    }

    // === Concurrent tasks ===

    @Nested
    @DisplayName("concurrent execution")
    class ConcurrentExecution {

        @Test
        @DisplayName("should handle multiple concurrent tasks")
        void shouldHandleMultipleConcurrentTasks() {
            var events = captureExecutionEvents();
            var taskCount = 3;
            var completedLatch = new CountDownLatch(taskCount);

            var executor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return TaskResult.success(
                            step.id(), step.taskName(), Duration.ofMillis(50),
                            Map.of("result", "ok")
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = createRuntime(List.of(executor));
            runtime.start();

            // Dispatch multiple tasks
            for (int i = 0; i < taskCount; i++) {
                var executionId = ExecutionId.generate();
                var request = createRequest("http-get", executionId);
                transport.dispatchTask(request);
            }

            await().atMost(15, TimeUnit.SECONDS).until(() ->
                    events.stream().filter(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())).count()
                            >= taskCount);

            long completedCount = events.stream()
                    .filter(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType()))
                    .count();
            assertThat(completedCount).isEqualTo(taskCount);
            assertThat(runtime.activeTaskCount()).isZero();
        }
    }

    // === Scenario restart ===

    @Nested
    @DisplayName("scenario restart")
    class ScenarioRestart {

        @Test
        @DisplayName("should cancel active tasks and return to IDLE on restart signal")
        void shouldCancelActiveTasksOnRestart() {
            var events = captureExecutionEvents();
            var blockLatch = new CountDownLatch(1);
            var interrupted = new AtomicReference<>(false);

            var blockingExecutor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    try {
                        blockLatch.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        return TaskResult.failed(
                                step.id(), step.taskName(), Duration.ZERO,
                                "interrupted", e
                        );
                    }
                    return TaskResult.success(
                            step.id(), step.taskName(), Duration.ofSeconds(1),
                            Map.of()
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = createRuntime(List.of(blockingExecutor));
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            // Wait for the task to be claimed and registered in activeTasks
            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    runtime.activeTaskCount() > 0);

            assertThat(runtime.activeTaskCount()).isEqualTo(1);

            // Send restart signal
            var restartSignal = new ScenarioRestartSignal(
                    SignalId.generate(), null, "ORCHESTRATOR_RESTART", Instant.now()
            );
            runtime.onScenarioRestart(restartSignal);

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    runtime.activeTaskCount() == 0);

            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should stay IDLE on restart when no active tasks")
        void shouldStayIdleOnRestartWhenNoActiveTasks() {
            runtime = createRuntime();
            runtime.start();
            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);

            var restartSignal = new ScenarioRestartSignal(
                    SignalId.generate(), null, "ORCHESTRATOR_RESTART", Instant.now()
            );
            runtime.onScenarioRestart(restartSignal);

            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
        }
    }

    // === canExecute ===

    @Nested
    @DisplayName("canExecute")
    class CanExecute {

        @Test
        @DisplayName("should return true for supported task names")
        void shouldReturnTrueForSupportedTasks() {
            runtime = createRuntime();
            assertThat(runtime.canExecute("http-get")).isTrue();
            assertThat(runtime.canExecute("kafka-produce")).isTrue();
        }

        @Test
        @DisplayName("should return false for unsupported task names")
        void shouldReturnFalseForUnsupportedTasks() {
            runtime = createRuntime();
            assertThat(runtime.canExecute("unsupported-task")).isFalse();
        }
    }

    // === Constructor validation ===

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null transport")
        void shouldRejectNullTransport() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    null, filter, registrationPort, descriptor,
                    heartbeatInterval, taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("transport");
        }

        @Test
        @DisplayName("should reject null filter")
        void shouldRejectNullFilter() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, null, registrationPort, descriptor,
                    heartbeatInterval, taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("filter");
        }

        @Test
        @DisplayName("should reject null registration port")
        void shouldRejectNullRegistrationPort() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, filter, null, descriptor,
                    heartbeatInterval, taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("registrationPort");
        }

        @Test
        @DisplayName("should reject null descriptor")
        void shouldRejectNullDescriptor() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, filter, registrationPort, null,
                    heartbeatInterval, taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("descriptor");
        }

        @Test
        @DisplayName("should reject null task execution timeout")
        void shouldRejectNullTaskTimeout() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, filter, registrationPort, descriptor,
                    heartbeatInterval, null, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("taskExecutionTimeout");
        }

        @Test
        @DisplayName("should reject non-positive task execution timeout")
        void shouldRejectNonPositiveTimeout() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, filter, registrationPort, descriptor,
                    heartbeatInterval, Duration.ZERO, List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskExecutionTimeout");
        }

        @Test
        @DisplayName("should reject heartbeat interval < 1s")
        void shouldRejectShortHeartbeatInterval() {
            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, filter, registrationPort, descriptor,
                    Duration.ofMillis(500), taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("heartbeatInterval");
        }

        @Test
        @DisplayName("should reject duplicate task executors")
        void shouldRejectDuplicateTaskExecutors() {
            var exec1 = new TaskExecutor() {
                @Override public TaskResult execute(ExecutionContext c, StepDefinition s) { return TaskResult.success(s.id(), s.taskName(), Duration.ZERO, Map.of()); }
                @Override public String getSupportedTaskName() { return "dup"; }
            };
            var exec2 = new TaskExecutor() {
                @Override public TaskResult execute(ExecutionContext c, StepDefinition s) { return TaskResult.success(s.id(), s.taskName(), Duration.ZERO, Map.of()); }
                @Override public String getSupportedTaskName() { return "dup"; }
            };

            assertThatThrownBy(() -> new DistributedAgentRuntime(
                    transport, filter, registrationPort, descriptor,
                    heartbeatInterval, taskExecutionTimeout, List.of(exec1, exec2), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate TaskExecutor");
        }

        @Test
        @DisplayName("should build executor map from list")
        void shouldBuildExecutorMapFromList() {
            var executor = new TaskExecutor() {
                @Override public TaskResult execute(ExecutionContext c, StepDefinition s) { return TaskResult.success(s.id(), s.taskName(), Duration.ZERO, Map.of()); }
                @Override public String getSupportedTaskName() { return "http-get"; }
            };

            runtime = createRuntime(List.of(executor));
            assertThat(runtime.executorCount()).isEqualTo(1);
        }
    }

    // === State during execution ===

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("should reflect executing state when tasks are active")
        void shouldReflectExecutingState() {
            var events = captureExecutionEvents();
            var startedLatch = new CountDownLatch(1);
            var releaseLatch = new CountDownLatch(1);

            var blockingExecutor = new TaskExecutor() {
                @Override
                public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                    startedLatch.countDown();
                    try {
                        releaseLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return TaskResult.success(
                            step.id(), step.taskName(), Duration.ofMillis(1), Map.of()
                    );
                }
                @Override
                public String getSupportedTaskName() {
                    return "http-get";
                }
            };

            runtime = createRuntime(List.of(blockingExecutor));
            runtime.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            // Wait for task to start
            try { startedLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertThat(runtime.getState()).isEqualTo(AgentState.EXECUTING);
            assertThat(runtime.activeTaskCount()).isEqualTo(1);

            releaseLatch.countDown();

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    runtime.getState() == AgentState.IDLE);
        }
    }

    // === Task ignored when DRAINING or OFFLINE ===

    @Nested
    @DisplayName("task ignored in non-active states")
    class TaskIgnored {

        @Test
        @DisplayName("should reflect OFFLINE state before start and after stop")
        void shouldReflectOfflineState() {
            runtime = createRuntime();
            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);

            runtime.start();
            assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);

            runtime.stop();
            assertThat(runtime.getState()).isEqualTo(AgentState.OFFLINE);
        }

        @Test
        @DisplayName("should return OFFLINE state in descriptor after stop")
        void shouldReturnOfflineDescriptorAfterStop() {
            runtime = createRuntime();
            runtime.start();
            runtime.stop();

            assertThat(runtime.getDescriptor().state()).isEqualTo(AgentState.OFFLINE);
        }
    }
}
