package com.performance.platform.engine.correlation;

import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultTaskCorrelationTracker")
class DefaultTaskCorrelationTrackerTest {

    private DefaultTaskCorrelationTracker tracker;
    private MessageId messageId;
    private TaskId taskId;
    private ExecutionId executionId;
    private AgentId agentA;
    private AgentId agentB;
    private AgentId agentC;

    @BeforeEach
    void setUp() {
        tracker = new DefaultTaskCorrelationTracker();
        messageId = MessageId.generate();
        taskId = TaskId.of("perf-test-1");
        executionId = ExecutionId.generate();
        agentA = AgentId.of("agent-a");
        agentB = AgentId.of("agent-b");
        agentC = AgentId.of("agent-c");
    }

    // ── Construction ────────────────────────────────────────────

    @Test
    @DisplayName("should be constructable without Spring context")
    void shouldBeConstructableWithoutSpring() {
        assertNotNull(tracker);
    }

    // ── trackDispatched ─────────────────────────────────────────

    @Nested
    @DisplayName("trackDispatched")
    class TrackDispatchedTests {

        @Test
        @DisplayName("should register message without error")
        void shouldRegisterMessageWithoutError() {
            assertDoesNotThrow(() ->
                    tracker.trackDispatched(messageId, taskId, executionId));
        }

        @Test
        @DisplayName("should allow multiple dispatches for same messageId")
        void shouldAllowMultipleDispatches() {
            tracker.trackDispatched(messageId, taskId, executionId);
            assertDoesNotThrow(() ->
                    tracker.trackDispatched(messageId, taskId, executionId));
        }
    }

    // ── onClaimed ───────────────────────────────────────────────

    @Nested
    @DisplayName("onClaimed")
    class OnClaimedTests {

        @Test
        @DisplayName("should register a claim")
        void shouldRegisterClaim() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);

            Set<AgentId> claims = tracker.claimsFor(messageId);
            assertEquals(1, claims.size());
            assertTrue(claims.contains(agentA));
        }

        @Test
        @DisplayName("should register multiple claims for same messageId")
        void shouldRegisterMultipleClaims() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            Set<AgentId> claims = tracker.claimsFor(messageId);
            assertEquals(2, claims.size());
            assertTrue(claims.contains(agentA));
            assertTrue(claims.contains(agentB));
        }

        @Test
        @DisplayName("should auto-create state when claim arrives before dispatch")
        void shouldAutoCreateStateWhenClaimBeforeDispatch() {
            tracker.onClaimed(messageId, agentA);

            Set<AgentId> claims = tracker.claimsFor(messageId);
            assertEquals(1, claims.size());
        }
    }

    // ── claimsFor ───────────────────────────────────────────────

    @Nested
    @DisplayName("claimsFor")
    class ClaimsForTests {

        @Test
        @DisplayName("should return empty set for unknown messageId")
        void shouldReturnEmptySetForUnknown() {
            Set<AgentId> claims = tracker.claimsFor(messageId);
            assertTrue(claims.isEmpty());
        }

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            tracker.onClaimed(messageId, agentA);
            Set<AgentId> claims = tracker.claimsFor(messageId);
            assertThrows(UnsupportedOperationException.class,
                    () -> claims.add(agentB));
        }

        @Test
        @DisplayName("should reflect current claims after multiple onClaimed")
        void shouldReflectCurrentClaims() {
            tracker.onClaimed(messageId, agentA);
            assertEquals(1, tracker.claimsFor(messageId).size());

            tracker.onClaimed(messageId, agentB);
            assertEquals(2, tracker.claimsFor(messageId).size());

            tracker.onClaimed(messageId, agentC);
            assertEquals(3, tracker.claimsFor(messageId).size());
        }
    }

    // ── onCompleted ─────────────────────────────────────────────

    @Nested
    @DisplayName("onCompleted")
    class OnCompletedTests {

        @Test
        @DisplayName("should register completion")
        void shouldRegisterCompletion() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);

            TaskResult result = successResult();
            tracker.onCompleted(messageId, agentA, result);

            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.FIRST_COMPLETE));
        }

        private TaskResult successResult() {
            return TaskResult.success(taskId, "perf-test", Duration.ofSeconds(2), Map.of("p95", "120ms"));
        }
    }

    // ── onFailed ────────────────────────────────────────────────

    @Nested
    @DisplayName("onFailed")
    class OnFailedTests {

        @Test
        @DisplayName("should register failure without error")
        void shouldRegisterFailure() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onFailed(messageId, agentA, "connection timeout");

            // ALL_COMPLETE: claimed + failed means complete
            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }
    }

    // ── FIRST_COMPLETE policy ───────────────────────────────────

    @Nested
    @DisplayName("FIRST_COMPLETE policy")
    class FirstCompletePolicyTests {

        @Test
        @DisplayName("should NOT be complete when no agent completed")
        void shouldNotBeCompleteWhenNoneCompleted() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.FIRST_COMPLETE));
        }

        @Test
        @DisplayName("should be complete after first agent completes")
        void shouldBeCompleteAfterFirstAgentCompletes() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            tracker.onCompleted(messageId, agentA, successResult());

            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.FIRST_COMPLETE));
        }

        @Test
        @DisplayName("should stay complete even if other agent fails after first succeeded")
        void shouldStayCompleteAfterSubsequentFailure() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            tracker.onCompleted(messageId, agentA, successResult());
            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.FIRST_COMPLETE));

            // agentB fails — still complete
            tracker.onFailed(messageId, agentB, "timeout");
            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.FIRST_COMPLETE));
        }

        private TaskResult successResult() {
            return TaskResult.success(taskId, "perf-test", Duration.ofSeconds(2), Map.of("p95", "120ms"));
        }
    }

    // ── ALL_COMPLETE policy ─────────────────────────────────────

    @Nested
    @DisplayName("ALL_COMPLETE policy")
    class AllCompletePolicyTests {

        @Test
        @DisplayName("should NOT be complete when no agents claimed")
        void shouldNotBeCompleteWhenNoClaims() {
            tracker.trackDispatched(messageId, taskId, executionId);

            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }

        @Test
        @DisplayName("should NOT be complete when only 1 of 2 claimed completed")
        void shouldNotBeCompleteWhenPartialCompletion() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            tracker.onCompleted(messageId, agentA, successResult());

            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }

        @Test
        @DisplayName("should be complete when all claimed agents completed")
        void shouldBeCompleteWhenAllCompleted() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            tracker.onCompleted(messageId, agentA, successResult());
            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));

            tracker.onCompleted(messageId, agentB, successResult());
            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }

        @Test
        @DisplayName("should be complete when all claimed completed OR failed")
        void shouldBeCompleteWhenMixOfCompletedAndFailed() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);
            tracker.onClaimed(messageId, agentC);

            tracker.onCompleted(messageId, agentA, successResult());
            tracker.onFailed(messageId, agentB, "connection refused");
            tracker.onCompleted(messageId, agentC, successResult());

            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }

        @Test
        @DisplayName("should be complete when all claimed agents failed")
        void shouldBeCompleteWhenAllFailed() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);

            tracker.onFailed(messageId, agentA, "timeout");
            tracker.onFailed(messageId, agentB, "oom");

            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }

        @Test
        @DisplayName("should handle 3 claims where 3rd completes before 2nd")
        void shouldHandleOutOfOrderCompletions() {
            tracker.trackDispatched(messageId, taskId, executionId);
            tracker.onClaimed(messageId, agentA);
            tracker.onClaimed(messageId, agentB);
            tracker.onClaimed(messageId, agentC);

            // agentC completes first
            tracker.onCompleted(messageId, agentC, successResult());
            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));

            // agentA fails
            tracker.onFailed(messageId, agentA, "error");
            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));

            // agentB completes last
            tracker.onCompleted(messageId, agentB, successResult());
            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }

        private TaskResult successResult() {
            return TaskResult.success(taskId, "perf-test", Duration.ofSeconds(2), Map.of("p95", "120ms"));
        }
    }

    // ── isComplete for unknown messageId ────────────────────────

    @Nested
    @DisplayName("isComplete for unknown messageId")
    class UnknownMessageIdTests {

        @Test
        @DisplayName("should return false for FIRST_COMPLETE on unknown messageId")
        void shouldReturnFalseFirstCompleteForUnknown() {
            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.FIRST_COMPLETE));
        }

        @Test
        @DisplayName("should return false for ALL_COMPLETE on unknown messageId")
        void shouldReturnFalseAllCompleteForUnknown() {
            assertFalse(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));
        }
    }

    // ── Thread safety ───────────────────────────────────────────

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("should handle concurrent claims and completions")
        void shouldHandleConcurrentClaimsAndCompletions() throws Exception {
            int numAgents = 50;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numAgents);
            AtomicBoolean allCompleteSeen = new AtomicBoolean(false);

            tracker.trackDispatched(messageId, taskId, executionId);

            for (int i = 0; i < numAgents; i++) {
                final AgentId agent = AgentId.of("agent-" + i);
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        tracker.onClaimed(messageId, agent);
                        if (index % 2 == 0) {
                            TaskResult result = TaskResult.success(taskId, "perf-test",
                                    Duration.ofSeconds(1), Map.of("idx", index));
                            tracker.onCompleted(messageId, agent, result);
                        } else {
                            tracker.onFailed(messageId, agent, "simulated-error-" + index);
                        }
                        allCompleteSeen.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);

            assertTrue(finished, "all agents should complete within timeout");
            assertTrue(allCompleteSeen.get());

            // After all agents complete, ALL_COMPLETE should be true
            assertTrue(tracker.isComplete(messageId, TaskCompletionPolicy.ALL_COMPLETE));

            // Claims count should match
            assertEquals(numAgents, tracker.claimsFor(messageId).size());
        }
    }
}
