package com.performance.platform.agent.runtime;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.registration.TransportAgentRegistration;
import com.performance.platform.application.ports.out.LifecycleSignalHandler;
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
import com.performance.platform.plugin.ExecutorAvailability;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("TaskAgentRuntime - ExecutorAvailability")
class TaskAgentRuntimeAvailabilityTest {

    private static class BaseExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
            return TaskResult.success(step.id(), step.taskName(), Duration.ofMillis(1), Map.of());
        }
        @Override
        public String getSupportedTaskName() {
            return "http-get";
        }
    }

    private static final class GatedExecutor extends BaseExecutor implements ExecutorAvailability {
        final AtomicBoolean available = new AtomicBoolean();
        @Override
        public boolean canAcceptNewTask() {
            return available.get();
        }
    }

    private InMemoryExecutionTransport transport;
    private TaskAgentRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null && runtime.isStarted() && !runtime.isStopped()) {
            runtime.stop();
        }
    }

    private CopyOnWriteArrayList<ExecutionEvent> start(TaskExecutor executor) {
        transport = new InMemoryExecutionTransport();
        var agentId = AgentId.generate();
        var tasks = Set.of("http-get");
        var descriptor = new AgentDescriptor(agentId, "test-agent", "localhost", 9090,
                "http://localhost:9090/callback", tasks, new AgentCapabilities(5, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofSeconds(30));
        runtime = new TaskAgentRuntime(transport, transport, transport, transport,
                new DefaultTaskSpecializationFilter(tasks, agentId), descriptor,
                Duration.ofSeconds(30), List.of(executor), List.of(),
                new TransportAgentRegistration(transport), Duration.ofSeconds(3),
                org.mockito.Mockito.mock(LifecycleSignalHandler.class));
        var events = new CopyOnWriteArrayList<ExecutionEvent>();
        transport.subscribe(events::add);
        runtime.start();
        return events;
    }

    private TaskExecutionRequest request() {
        var executionId = ExecutionId.generate();
        var step = new StepDefinition(TaskId.of("task-" + UUID.randomUUID().toString().substring(0, 8)),
                "http-get", Phase.PREPARATION, Map.of(), List.of(), List.of(),
                Duration.ofSeconds(30), null);
        return new TaskExecutionRequest(MessageId.generate(), executionId, step,
                PartialExecutionContext.empty(executionId, ScenarioId.of("s-1")),
                Instant.now(), RetryPolicy.defaults());
    }

    private static boolean claimed(List<ExecutionEvent> events) {
        return events.stream().anyMatch(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()));
    }

    @Test
    @DisplayName("unavailable executor: no claim emitted")
    void noClaimWhenUnavailable() {
        var events = start(new GatedExecutor());

        transport.dispatchTask(request());

        await().pollDelay(300, TimeUnit.MILLISECONDS).until(() -> true);
        assertThat(claimed(events)).isFalse();
        assertThat(runtime.processedMessageCount()).isZero();
    }

    @Test
    @DisplayName("available executor: claim emitted")
    void claimWhenAvailable() {
        var executor = new GatedExecutor();
        executor.available.set(true);
        var events = start(executor);

        transport.dispatchTask(request());

        await().atMost(5, TimeUnit.SECONDS).until(() -> claimed(events));
    }

    @Test
    @DisplayName("executor without the interface: claim emitted as before")
    void claimWhenNoInterface() {
        var events = start(new BaseExecutor());

        transport.dispatchTask(request());

        await().atMost(5, TimeUnit.SECONDS).until(() -> claimed(events));
    }
}
