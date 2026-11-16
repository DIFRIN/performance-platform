package com.performance.platform.engine.correlation;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du tracker de correlation au modele grant (ADR-055).
 */
@DisplayName("DefaultTaskCorrelationTracker")
class DefaultTaskCorrelationTrackerTest {

    private DefaultTaskCorrelationTracker tracker;
    private MessageId messageId;
    private TaskId taskId;
    private ExecutionId executionId;
    private AgentId agentA;
    private AgentId agentB;

    @BeforeEach
    void setUp() {
        tracker = new DefaultTaskCorrelationTracker();
        messageId = MessageId.generate();
        taskId = TaskId.of("task-1");
        executionId = ExecutionId.generate();
        agentA = AgentId.of("agent-a");
        agentB = AgentId.of("agent-b");
    }

    private TaskResult success(AgentId agent) {
        return TaskResult.success(taskId, taskId.value(), Duration.ofMillis(10), Map.of());
    }

    @Test
    @DisplayName("fanOut one : le premier claim est accorde, les suivants rejetes")
    void oneGrantsFirstClaimAndRejectsOthers() {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ONE);

        assertEquals(GrantDecision.GRANTED, tracker.onClaimed(messageId, agentA));
        assertEquals(GrantDecision.REJECTED, tracker.onClaimed(messageId, agentB));
        assertEquals(java.util.Set.of(agentA), tracker.grantedFor(messageId));
    }

    @Test
    @DisplayName("fanOut all : claims en attente puis settlement")
    void allSettlesClaims() throws Exception {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ALL);

        assertEquals(GrantDecision.PENDING, tracker.onClaimed(messageId, agentA));
        assertEquals(GrantDecision.PENDING, tracker.onClaimed(messageId, agentB));
        assertTrue(tracker.grantedFor(messageId).isEmpty());

        assertEquals(2, tracker.settleClaims(messageId).size());
        assertEquals(GrantDecision.REJECTED, tracker.onClaimed(messageId, AgentId.of("late")));
    }

    @Test
    @DisplayName("completion one : signal des que l'agent accorde a repondu")
    void completionSignalForOne() throws Exception {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ONE);
        tracker.onClaimed(messageId, agentA);

        var signal = tracker.completionSignal(messageId);
        assertFalse(signal.isDone());

        tracker.onCompleted(messageId, agentA, success(agentA));
        assertTrue(signal.get(1, TimeUnit.SECONDS) == null || signal.isDone());
    }

    @Test
    @DisplayName("completion all : attend tous les agents accordes")
    void completionSignalForAll() throws Exception {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ALL);
        tracker.onClaimed(messageId, agentA);
        tracker.onClaimed(messageId, agentB);
        tracker.settleClaims(messageId);

        var signal = tracker.completionSignal(messageId);
        tracker.onCompleted(messageId, agentA, success(agentA));
        assertFalse(signal.isDone());

        tracker.onCompleted(messageId, agentB, success(agentB));
        assertTrue(signal.isDone());
    }

    @Test
    @DisplayName("agent perdu : ses grants non repondus font echouer l'etape")
    void agentLostFailsGrantedClaims() {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ONE);
        tracker.onClaimed(messageId, agentA);

        tracker.onAgentLost(agentA);

        assertTrue(tracker.completionSignal(messageId).isDone());
    }

    @Test
    @DisplayName("release libere l'etat de correlation")
    void releaseRemovesState() {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ONE);
        assertEquals(1, tracker.stateCount());

        tracker.release(messageId);
        assertEquals(0, tracker.stateCount());
    }

    @Test
    @DisplayName("executionId est conserve pour les signaux de grant")
    void executionIdIsTracked() {
        tracker.trackDispatched(messageId, taskId, executionId, FanOut.ONE);
        assertEquals(executionId, tracker.executionIdFor(messageId));
        assertEquals(taskId, tracker.taskIdFor(messageId));
    }
}
