package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.domain.event.TaskGrantSignal;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.AgentLossPolicy;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.correlation.DefaultTaskCorrelationTracker;
import com.performance.platform.engine.shared.StepDispatchResult;
import com.performance.platform.transport.SignalChannel;
import com.performance.platform.transport.TaskChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("TransportStepDispatcher - parts et minAgents (ADR-067)")
class TransportStepDispatcherTest {

    private final DefaultTaskCorrelationTracker tracker = new DefaultTaskCorrelationTracker();
    private final TaskChannel taskChannel = mock(TaskChannel.class);
    private final SignalChannel signalChannel = mock(SignalChannel.class);

    private StepDefinition step(int minAgents) {
        return new StepDefinition(TaskId.of("inj"), "gatling", Phase.INJECTION, Map.of(), List.of(),
                null, null, null, FanOut.ALL, List.of(), Duration.ofSeconds(5), null,
                minAgents, AgentLossPolicy.ABORT);
    }

    private List<StepDispatchResult> run(int claims, int minAgents) {
        var dispatcher = new TransportStepDispatcher(taskChannel, signalChannel,
                mock(com.performance.platform.engine.availability.AgentAvailabilityChecker.class),
                tracker, new ExecutionConfig(null, null, Duration.ofMillis(300)),
                mock(ApplicationEventPublisher.class));
        // Les claims arrivent pendant la fenetre : on les injecte des le dispatch.
        org.mockito.Mockito.doAnswer(inv -> {
            var req = (com.performance.platform.transport.message.TaskExecutionRequest) inv.getArgument(0);
            for (int i = 0; i < claims; i++) {
                tracker.onClaimed(req.id(), AgentId.of("agent-" + (claims - i)));
            }
            // Completion immediate des agents accordes apres settlement.
            if (minAgents <= claims) {
                new Thread(() -> {
                    try { Thread.sleep(450); } catch (InterruptedException ignored) { }
                    for (AgentId a : tracker.grantedFor(req.id())) {
                        tracker.onCompleted(req.id(), a, TaskResult.success(
                                TaskId.of("inj"), "gatling", Duration.ZERO, Map.of()));
                    }
                }).start();
            }
            return null;
        }).when(taskChannel).dispatchTask(any());
        var ctx = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s"));
        return dispatcher.dispatch(new ExecutionStep(step(minAgents), List.of(), 0, java.util.Set.of()), ctx);
    }

    @Test
    @DisplayName("3 claims => parts (0,3),(1,3),(2,3) triees par AgentId")
    void threeClaimsEqualShares() {
        var results = run(3, 1);
        assertEquals(3, results.size());
        var captor = ArgumentCaptor.forClass(AgentSignal.class);
        verify(signalChannel, atLeast(3)).broadcastSignal(captor.capture());
        var grants = captor.getAllValues().stream().map(s -> (TaskGrantSignal) s).toList();
        for (int i = 0; i < 3; i++) {
            assertEquals("agent-" + (i + 1), grants.get(i).agentId().value());
            assertEquals(GrantShare.equal(i, 3), grants.get(i).share());
            assertTrue(grants.get(i).granted());
        }
    }

    @Test
    @DisplayName("2 claims avec minAgents=3 => aucun grant, etape FAILED")
    void minAgentsNotMet() {
        var results = run(2, 3);
        assertEquals(TaskStatus.FAILED, results.getFirst().result().status());
        assertTrue(results.getFirst().result().errorMessage()
                .contains("min_agents_not_met granted=2 required=3"));
        var captor = ArgumentCaptor.forClass(AgentSignal.class);
        verify(signalChannel, atLeast(2)).broadcastSignal(captor.capture());
        captor.getAllValues().forEach(s -> assertTrue(!((TaskGrantSignal) s).granted()));
    }

    private List<StepDispatchResult> runWithLoss(AgentLossPolicy policy) {
        var def = new StepDefinition(TaskId.of("inj"), "gatling", Phase.INJECTION, Map.of(), List.of(),
                null, null, null, FanOut.ALL, List.of(), Duration.ofSeconds(5), null, 1, policy);
        var dispatcher = new TransportStepDispatcher(taskChannel, signalChannel,
                mock(com.performance.platform.engine.availability.AgentAvailabilityChecker.class),
                tracker, new ExecutionConfig(null, null, Duration.ofMillis(300)),
                mock(ApplicationEventPublisher.class));
        org.mockito.Mockito.doAnswer(inv -> {
            var req = (com.performance.platform.transport.message.TaskExecutionRequest) inv.getArgument(0);
            for (int i = 1; i <= 3; i++) {
                tracker.onClaimed(req.id(), AgentId.of("agent-" + i));
            }
            new Thread(() -> {
                try { Thread.sleep(450); } catch (InterruptedException ignored) { }
                tracker.onAgentLost(AgentId.of("agent-3"));
                try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                for (String a : List.of("agent-1", "agent-2")) {
                    tracker.onCompleted(req.id(), AgentId.of(a), TaskResult.success(
                            TaskId.of("inj"), "gatling", Duration.ZERO, Map.of()));
                }
            }).start();
            return null;
        }).when(taskChannel).dispatchTask(any());
        var ctx = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s"));
        return dispatcher.dispatch(new ExecutionStep(def, List.of(), 0, java.util.Set.of()), ctx);
    }

    @Test
    @DisplayName("ABORT : perte d'un agent sur trois => etape FAILED")
    void abortOnAgentLoss() {
        var results = runWithLoss(AgentLossPolicy.ABORT);
        assertEquals(1, results.size());
        assertEquals(TaskStatus.FAILED, results.getFirst().result().status());
        assertTrue(results.getFirst().result().errorMessage().contains("agent_lost: agent-3"));
    }

    @Test
    @DisplayName("CONTINUE : perte d'un agent sur trois => 2 resultats, compteurs 3/2")
    void continueOnAgentLoss() {
        var results = runWithLoss(AgentLossPolicy.CONTINUE);
        assertEquals(2, results.size());
        for (var r : results) {
            assertEquals(TaskStatus.SUCCESS, r.result().status());
            assertEquals(3, r.result().outputs().get("grantedAgents"));
            assertEquals(2, r.result().outputs().get("reportingAgents"));
        }
    }
}
