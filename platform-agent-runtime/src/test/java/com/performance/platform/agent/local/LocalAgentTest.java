package com.performance.platform.agent.local;

import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.ScenarioRestartSignal;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DisplayName("LocalAgent")
class LocalAgentTest {

    // === Shared fixtures ===

    private InMemoryExecutionTransport transport;
    private AgentId agentId;
    private AgentDescriptor descriptor;
    private Set<String> supportedTaskNames;
    private Duration taskExecutionTimeout = Duration.ofSeconds(30);

    private LocalAgent agent;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        agentId = AgentId.generate();
        supportedTaskNames = Set.of("http-get", "kafka-produce");
        descriptor = new AgentDescriptor(
                agentId,
                "local-agent",
                "localhost",
                0,
                null,
                supportedTaskNames,
                new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofDays(1)
        );
    }

    @AfterEach
    void tearDown() {
        if (agent != null && agent.isStarted() && !agent.isStopped()) {
            agent.stop();
        }
    }

    // === Factory methods ===

    private LocalAgent createAgent(List<TaskExecutor> taskExecutors) {
        return createAgent(taskExecutors, List.of());
    }

    private LocalAgent createAgent(List<TaskExecutor> taskExecutors,
                                    List<StatefulResourceCleaner> cleaners) {
        return new LocalAgent(
                transport, descriptor, taskExecutionTimeout,
                taskExecutors != null ? taskExecutors : List.of(),
                cleaners != null ? cleaners : List.of()
        );
    }

    private LocalAgent createAgent() {
        return createAgent(List.of(), List.of());
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
                RetryPolicy.defaults()
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
        @DisplayName("should transition OFFLINE → IDLE on start")
        void shouldTransitionToIdleOnStart() {
            agent = createAgent();
            assertThat(agent.getState()).isEqualTo(AgentState.OFFLINE);

            agent.start();

            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);
            assertThat(agent.isStarted()).isTrue();
        }

        @Test
        @DisplayName("should transition IDLE → DRAINING → OFFLINE on stop")
        void shouldTransitionToOfflineOnStop() {
            agent = createAgent();
            agent.start();
            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);

            agent.stop();

            assertThat(agent.getState()).isEqualTo(AgentState.OFFLINE);
            assertThat(agent.isStopped()).isTrue();
        }

        @Test
        @DisplayName("should be idempotent on double start")
        void shouldBeIdempotentOnDoubleStart() {
            agent = createAgent();
            agent.start();
            agent.start(); // second start should be ignored

            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should be idempotent on double stop")
        void shouldBeIdempotentOnDoubleStop() {
            agent = createAgent();
            agent.start();
            agent.stop();
            agent.stop(); // second stop should be ignored

            assertThat(agent.getState()).isEqualTo(AgentState.OFFLINE);
        }

        @Test
        @DisplayName("should return correct descriptor after start")
        void shouldReturnCorrectDescriptorAfterStart() {
            agent = createAgent();
            agent.start();

            var desc = agent.getDescriptor();
            assertThat(desc.id()).isEqualTo(agentId);
            assertThat(desc.name()).isEqualTo("local-agent");
            assertThat(desc.state()).isEqualTo(AgentState.IDLE);
            assertThat(desc.supportedTaskNames()).containsAll(supportedTaskNames);
        }

        @Test
        @DisplayName("should throw TransportException if start fails (transport connect fails)")
        void shouldThrowIfStartFails() {
            // Use a transport that fails on connect
            var failingTransport = new InMemoryExecutionTransport() {
                @Override
                public void connect() throws TransportException {
                    throw new TransportException("forced failure");
                }
            };
            agent = new LocalAgent(
                    failingTransport, descriptor, taskExecutionTimeout, List.of(), List.of()
            );

            assertThatThrownBy(() -> agent.start())
                    .isInstanceOf(TransportException.class)
                    .hasMessageContaining("Failed to start local agent");
            assertThat(agent.getState()).isEqualTo(AgentState.OFFLINE);
        }
    }

    // === Task reception & filtering ===

    @Nested
    @DisplayName("task reception")
    class TaskReception {

        @Test
        @DisplayName("should claim task for supported taskName")
        void shouldClaimTaskForSupportedTaskName() {
            var events = captureExecutionEvents();
            agent = createAgent();
            agent.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())));
            assertThat(agent.processedMessageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should ignore task for unsupported taskName")
        void shouldIgnoreTaskForUnsupportedTaskName() {
            var events = captureExecutionEvents();
            agent = createAgent();
            agent.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("unsupported-task", executionId);
            transport.dispatchTask(request);

            // No events should be published for unsupported tasks
            await().pollDelay(200, TimeUnit.MILLISECONDS).until(() -> true);
            assertThat(events).noneMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()));
            assertThat(agent.processedMessageCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should ignore duplicate messageId (idempotence)")
        void shouldIgnoreDuplicateMessageId() {
            var events = captureExecutionEvents();
            agent = createAgent();
            agent.start();

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

        @Test
        @DisplayName("should ignore task when agent is OFFLINE")
        void shouldIgnoreTaskWhenOffline() {
            var events = captureExecutionEvents();
            agent = createAgent();
            // agent is not started — OFFLINE, but transport must be connected for dispatch
            transport.connect();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().pollDelay(200, TimeUnit.MILLISECONDS).until(() -> true);
            assertThat(events).noneMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()));
            transport.disconnect();
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

            agent = createAgent(List.of(successExecutor));
            agent.start();

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

            agent = createAgent(List.of(failingExecutor));
            agent.start();

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
            agent = createAgent(); // no executors
            agent.start();

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

            agent = new LocalAgent(
                    transport, descriptor, shortTimeout, List.of(slowExecutor), List.of());
            agent.start();

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

            agent = createAgent(List.of(quickExecutor));
            agent.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            // After task completion, agent should be back to IDLE
            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    agent.getState() == AgentState.IDLE);
            assertThat(agent.activeTaskCount()).isZero();
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

            agent = createAgent(List.of(executor));
            agent.start();

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
            assertThat(agent.activeTaskCount()).isZero();
        }
    }

    // === Scenario restart ===

    @Nested
    @DisplayName("scenario restart")
    class ScenarioRestart {

        @Test
        @DisplayName("should cancel active tasks and return to IDLE on restart signal")
        void shouldCancelActiveTasksOnRestart() {
            captureExecutionEvents();
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

            agent = createAgent(List.of(blockingExecutor));
            agent.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            // Wait for the task to be claimed and registered in activeTasks
            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    agent.activeTaskCount() > 0);

            assertThat(agent.activeTaskCount()).isEqualTo(1);

            // Send restart signal
            var restartSignal = new ScenarioRestartSignal(
                    SignalId.generate(), null, "ORCHESTRATOR_RESTART", Instant.now()
            );
            agent.onScenarioRestart(restartSignal);

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    agent.activeTaskCount() == 0);

            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);
        }

        @Test
        @DisplayName("should stay IDLE on restart when no active tasks")
        void shouldStayIdleOnRestartWhenNoActiveTasks() {
            agent = createAgent();
            agent.start();
            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);

            var restartSignal = new ScenarioRestartSignal(
                    SignalId.generate(), null, "ORCHESTRATOR_RESTART", Instant.now()
            );
            agent.onScenarioRestart(restartSignal);

            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);
        }
    }

    // === canExecute ===

    @Nested
    @DisplayName("canExecute")
    class CanExecute {

        @Test
        @DisplayName("should return true for supported task names")
        void shouldReturnTrueForSupportedTasks() {
            agent = createAgent();
            assertThat(agent.canExecute("http-get")).isTrue();
            assertThat(agent.canExecute("kafka-produce")).isTrue();
        }

        @Test
        @DisplayName("should return false for unsupported task names")
        void shouldReturnFalseForUnsupportedTasks() {
            agent = createAgent();
            assertThat(agent.canExecute("unsupported-task")).isFalse();
        }

        @Test
        @DisplayName("should support all task names from descriptor (derived from registered executors)")
        void shouldSupportAllRegisteredTaskNames() {
            var allTaskNames = Set.of("task-a", "task-b", "task-c");
            var customDescriptor = new AgentDescriptor(
                    agentId,
                    "custom-agent",
                    "localhost",
                    0,
                    null,
                    allTaskNames,
                    new AgentCapabilities(10, "1.0.0"),
                    AgentState.OFFLINE,
                    Instant.now(),
                    Instant.now(),
                    Duration.ofDays(1)
            );

            var executor1 = new TaskExecutor() {
                @Override public TaskResult execute(ExecutionContext c, StepDefinition s) { return TaskResult.success(s.id(), s.taskName(), Duration.ZERO, Map.of()); }
                @Override public String getSupportedTaskName() { return "task-a"; }
            };
            var executor2 = new TaskExecutor() {
                @Override public TaskResult execute(ExecutionContext c, StepDefinition s) { return TaskResult.success(s.id(), s.taskName(), Duration.ZERO, Map.of()); }
                @Override public String getSupportedTaskName() { return "task-b"; }
            };
            var executor3 = new TaskExecutor() {
                @Override public TaskResult execute(ExecutionContext c, StepDefinition s) { return TaskResult.success(s.id(), s.taskName(), Duration.ZERO, Map.of()); }
                @Override public String getSupportedTaskName() { return "task-c"; }
            };

            agent = new LocalAgent(
                    transport, customDescriptor, taskExecutionTimeout,
                    List.of(executor1, executor2, executor3), List.of());
            assertThat(agent.canExecute("task-a")).isTrue();
            assertThat(agent.canExecute("task-b")).isTrue();
            assertThat(agent.canExecute("task-c")).isTrue();
            assertThat(agent.canExecute("unsupported")).isFalse();
            assertThat(agent.executorCount()).isEqualTo(3);
        }
    }

    // === Constructor validation ===

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null transport")
        void shouldRejectNullTransport() {
            assertThatThrownBy(() -> new LocalAgent(
                    null, descriptor, taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("transport");
        }

        @Test
        @DisplayName("should reject null descriptor")
        void shouldRejectNullDescriptor() {
            assertThatThrownBy(() -> new LocalAgent(
                    transport, null, taskExecutionTimeout, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("descriptor");
        }

        @Test
        @DisplayName("should reject null task execution timeout")
        void shouldRejectNullTaskTimeout() {
            assertThatThrownBy(() -> new LocalAgent(
                    transport, descriptor, null, List.of(), List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("taskExecutionTimeout");
        }

        @Test
        @DisplayName("should reject non-positive task execution timeout")
        void shouldRejectNonPositiveTimeout() {
            assertThatThrownBy(() -> new LocalAgent(
                    transport, descriptor, Duration.ZERO, List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskExecutionTimeout");
        }

        @Test
        @DisplayName("should reject null task executors")
        void shouldRejectNullTaskExecutors() {
            assertThatThrownBy(() -> new LocalAgent(
                    transport, descriptor, taskExecutionTimeout, null, List.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("taskExecutors");
        }

        @Test
        @DisplayName("should reject null cleaners")
        void shouldRejectNullCleaners() {
            assertThatThrownBy(() -> new LocalAgent(
                    transport, descriptor, taskExecutionTimeout, List.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("cleaners");
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

            assertThatThrownBy(() -> new LocalAgent(
                    transport, descriptor, taskExecutionTimeout, List.of(exec1, exec2), List.of()))
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

            agent = createAgent(List.of(executor));
            assertThat(agent.executorCount()).isEqualTo(1);
        }
    }

    // === State during execution ===

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("should reflect executing state when tasks are active")
        void shouldReflectExecutingState() {
            captureExecutionEvents();
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

            agent = createAgent(List.of(blockingExecutor));
            agent.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);
            transport.dispatchTask(request);

            // Wait for task to start
            try { startedLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertThat(agent.getState()).isEqualTo(AgentState.EXECUTING);
            assertThat(agent.activeTaskCount()).isEqualTo(1);

            releaseLatch.countDown();

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    agent.getState() == AgentState.IDLE);
        }
    }

    // === Task ignored when DRAINING or OFFLINE ===

    @Nested
    @DisplayName("task ignored in non-active states")
    class TaskIgnored {

        @Test
        @DisplayName("should reflect OFFLINE state before start and after stop")
        void shouldReflectOfflineState() {
            agent = createAgent();
            assertThat(agent.getState()).isEqualTo(AgentState.OFFLINE);

            agent.start();
            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);

            agent.stop();
            assertThat(agent.getState()).isEqualTo(AgentState.OFFLINE);
        }

        @Test
        @DisplayName("should return OFFLINE state in descriptor after stop")
        void shouldReturnOfflineDescriptorAfterStop() {
            agent = createAgent();
            agent.start();
            agent.stop();

            assertThat(agent.getDescriptor().state()).isEqualTo(AgentState.OFFLINE);
        }
    }

    // === InMemory transport integration ===

    @Nested
    @DisplayName("in-memory transport integration")
    class InMemoryTransport {

        @Test
        @DisplayName("should receive tasks via in-memory transport dispatch")
        void shouldReceiveTasksViaInMemoryTransport() {
            var events = captureExecutionEvents();

            var executor = new TaskExecutor() {
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

            agent = createAgent(List.of(executor));
            agent.start();

            var executionId = ExecutionId.generate();
            var request = createRequest("http-get", executionId);

            // Dispatch via transport (simulating orchestrator side)
            transport.dispatchTask(request);

            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            assertThat(events.stream().anyMatch(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType())))
                    .isTrue();
            assertThat(events.stream().anyMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType())))
                    .isTrue();
        }

        @Test
        @DisplayName("should receive broadcast signals via in-memory transport")
        void shouldReceiveBroadcastSignalsViaTransport() {
            agent = createAgent();
            agent.start();
            assertThat(agent.getState()).isEqualTo(AgentState.IDLE);

            // Send restart signal via transport broadcast
            var restartSignal = new ScenarioRestartSignal(
                    SignalId.generate(), null, "ORCHESTRATOR_RESTART", Instant.now()
            );
            transport.broadcastSignal(restartSignal);

            await().pollDelay(200, TimeUnit.MILLISECONDS).until(() -> true);
            assertThat(agent.getState()).isEqualTo(AgentState.IDLE); // no tasks to cancel, stays IDLE
        }
    }

    // === TaskExecutor absence handled gracefully ===

    @Nested
    @DisplayName("missing executor handling")
    class MissingExecutor {

        @Test
        @DisplayName("should publish TASK_FAILED when executor missing at runtime")
        void shouldPublishTaskFailedWhenExecutorMissingAtRuntime() {
            var events = captureExecutionEvents();
            agent = createAgent(); // no executors
            agent.start();

            // Dispatch a task for a supported name that has no executor
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
    }
}
