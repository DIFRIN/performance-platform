package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.domain.event.AgentLost;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.campaign.ExecutionEngine;
import com.performance.platform.engine.correlation.DefaultTaskCorrelationTracker;
import com.performance.platform.application.ports.out.ExecutionPlanBuilder;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4 — un agent perdu libere le step en cours bien avant le timeout.
 * <p>
 * Le test utilise le transport in-memory : un handler simule le claim puis
 * l'agent est declare perdu. Le tracker invalide le claim, le signal de
 * completion se declenche et le moteur termine en echec rapidement.
 */
@DisplayName("Agent loss recovery (T4)")
class AgentLossRecoveryIT {

    private static final Duration EXECUTION_TIMEOUT = Duration.ofSeconds(30);

    @Test
    @DisplayName("should fail the step quickly instead of waiting for the timeout")
    void shouldFailFastOnAgentLoss() throws Exception {
        var transport = new InMemoryExecutionTransport();
        transport.connect();

        var tracker = new DefaultTaskCorrelationTracker();
        var repository = new RemoteExecutionEngineTest.StubExecutionRepository();
        var scenario = scenarioWithOneStep();
        var agentId = AgentId.of("agent-1");
        var claimed = new CountDownLatch(1);
        var dispatched = new AtomicReference<TaskExecutionRequest>();

        transport.receiveTask(request -> {
            dispatched.set(request);
            transport.publishEvent(new ExecutionEvent(
                    EventId.generate(), request.executionId(), request.id(), agentId,
                    ExecutionEvent.TASK_CLAIMED, Map.of(), Instant.now()));
            claimed.countDown();
        });

        var eventHandler = new TransportExecutionEventHandler(
                transport, tracker, repository, event -> { });
        var engine = new ExecutionEngine(
                planBuilderFor(scenario),
                repository,
                event -> { },
                signal -> { },
                new RemoteStepDispatcher(
                        transport,
                        new RemoteExecutionEngineTest.StubAgentAvailabilityChecker(),
                        tracker,
                        new ExecutionConfig(
                                Duration.ofSeconds(30), EXECUTION_TIMEOUT,
                                Duration.ofSeconds(10), TaskCompletionPolicy.FIRST_COMPLETE),
                        event -> { }),
                (executionId, outcomes, phase) -> { });

        var executionId = new AtomicReference<ExecutionId>();
        long startNs = System.nanoTime();
        Thread engineThread = Thread.ofVirtual().start(() ->
                executionId.set(engine.execute(scenario)));

        assertThat(claimed.await(5, TimeUnit.SECONDS))
                .as("agent should claim the dispatched task").isTrue();

        eventHandler.onAgentLost(new AgentLost(agentId, "heartbeat timeout", Instant.now()));

        engineThread.join(5_000);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertThat(engineThread.isAlive())
                .as("engine must not wait for the 30s execution timeout").isFalse();
        assertThat(elapsedMs).isLessThan(5_000);
        assertThat(repository.findById(executionId.get())).isPresent();
        assertThat(repository.findById(executionId.get()).orElseThrow().status())
                .isEqualTo(ExecutionStatus.FAILED);
        // L'etat de correlation est libere apres consommation (pas de fuite).
        assertThat(tracker.claimsFor(dispatched.get().id())).isEmpty();

        transport.disconnect();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static ScenarioDefinition scenarioWithOneStep() {
        var step = new StepDefinition(
                TaskId.of("slow-step"), "slow-task", Phase.PREPARATION, Map.of(),
                List.of(), List.of(), Duration.ofSeconds(20), null);
        return new ScenarioDefinition(
                ScenarioId.of("agent-loss"), "agent-loss", "1.0", List.of(), Map.of(),
                ExecutionMode.LOCAL, List.of(step), Map.of());
    }

    private static ExecutionPlanBuilder planBuilderFor(ScenarioDefinition scenario) {
        ExecutionId executionId = ExecutionId.generate();
        var step = scenario.steps().getFirst();
        var execStep = new com.performance.platform.domain.execution.ExecutionStep(
                step, List.of(), 0, Set.of());
        return ignored -> new ExecutionPlan(
                executionId, scenario.id(),
                List.of(execStep), List.of(), List.of(), List.of(),
                ExecutionContext.initial(executionId, scenario.id()));
    }
}
