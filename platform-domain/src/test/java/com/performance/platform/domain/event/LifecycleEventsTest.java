package com.performance.platform.domain.event;

import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour les 12 events du cycle de vie scenario/phase/task.
 * Couvre : instanciation, egalite par valeur, validation non-null, champs nullables.
 */
@DisplayName("LifecycleEvents")
class LifecycleEventsTest {

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static ExecutionId exec() { return ExecutionId.of("exec-1"); }
    private static ScenarioId scenario() { return ScenarioId.of("scenario-1"); }
    private static TaskId task() { return TaskId.of("task-1"); }
    private static AgentId agent() { return AgentId.of("agent-1"); }
    private static MessageId msg() { return MessageId.of("msg-1"); }
    private static Instant now() { return Instant.now(); }

    private static TaskResult successResult() {
        return TaskResult.success(task(), "gatling-http", Duration.ofSeconds(10), Map.of("rps", 100));
    }

    // ─── ScenarioStarted ───────────────────────────────────────────────

    @Nested
    @DisplayName("ScenarioStarted")
    class ScenarioStartedTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var e = new ScenarioStarted(exec(), scenario(), ts);

            assertEquals(exec(), e.executionId());
            assertEquals(scenario(), e.scenarioId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new ScenarioStarted(exec(), scenario(), ts);
            var e2 = new ScenarioStarted(exec(), scenario(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("executionId different -> pas egaux")
        void differentExecutionIdNotEqual() {
            var ts = now();
            var e1 = new ScenarioStarted(ExecutionId.of("exec-1"), scenario(), ts);
            var e2 = new ScenarioStarted(ExecutionId.of("exec-2"), scenario(), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("executionId null leve NullPointerException")
        void nullExecutionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new ScenarioStarted(null, scenario(), now()));
        }

        @Test
        @DisplayName("toString contient le nom du record")
        void toStringContainsRecordName() {
            var e = new ScenarioStarted(exec(), scenario(), now());
            assertTrue(e.toString().contains("ScenarioStarted"));
        }
    }

    // ─── ScenarioFinished ──────────────────────────────────────────────

    @Nested
    @DisplayName("ScenarioFinished")
    class ScenarioFinishedTest {

        @Test
        @DisplayName("instanciable avec verdict SUCCESS")
        void instantiableWithSuccessVerdict() {
            var ts = now();
            var dur = Duration.ofMinutes(5);
            var e = new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS, dur, ts);

            assertEquals(exec(), e.executionId());
            assertEquals(scenario(), e.scenarioId());
            assertEquals(Verdict.SUCCESS, e.verdict());
            assertEquals(dur, e.duration());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("instanciable avec verdict FAILED")
        void instantiableWithFailedVerdict() {
            var e = new ScenarioFinished(exec(), scenario(), Verdict.FAILED,
                Duration.ofSeconds(30), now());

            assertEquals(Verdict.FAILED, e.verdict());
        }

        @Test
        @DisplayName("instanciable avec verdict WARNING")
        void instantiableWithWarningVerdict() {
            var e = new ScenarioFinished(exec(), scenario(), Verdict.WARNING,
                Duration.ofSeconds(30), now());

            assertEquals(Verdict.WARNING, e.verdict());
        }

        @Test
        @DisplayName("verdict different -> pas egaux")
        void differentVerdictNotEqual() {
            var ts = now();
            var dur = Duration.ofMinutes(1);
            var e1 = new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS, dur, ts);
            var e2 = new ScenarioFinished(exec(), scenario(), Verdict.FAILED, dur, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("duration different -> pas egaux")
        void differentDurationNotEqual() {
            var ts = now();
            var e1 = new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS,
                Duration.ofSeconds(10), ts);
            var e2 = new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS,
                Duration.ofSeconds(20), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var dur = Duration.ofMinutes(3);
            var e1 = new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS, dur, ts);
            var e2 = new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS, dur, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("verdict null leve NullPointerException")
        void nullVerdictThrows() {
            assertThrows(NullPointerException.class, () ->
                new ScenarioFinished(exec(), scenario(), null, Duration.ZERO, now()));
        }

        @Test
        @DisplayName("duration null leve NullPointerException")
        void nullDurationThrows() {
            assertThrows(NullPointerException.class, () ->
                new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS, null, now()));
        }
    }

    // ─── ScenarioCancelled ──────────────────────────────────────────────

    @Nested
    @DisplayName("ScenarioCancelled")
    class ScenarioCancelledTest {

        @Test
        @DisplayName("instanciable avec reason")
        void instantiableWithReason() {
            var ts = now();
            var e = new ScenarioCancelled(exec(), scenario(), "timeout exceeded", ts);

            assertEquals(exec(), e.executionId());
            assertEquals(scenario(), e.scenarioId());
            assertEquals("timeout exceeded", e.reason());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("reason peut etre null")
        void reasonCanBeNull() {
            var e = new ScenarioCancelled(exec(), scenario(), null, now());
            assertNull(e.reason());
        }

        @Test
        @DisplayName("reason different -> pas egaux")
        void differentReasonNotEqual() {
            var ts = now();
            var e1 = new ScenarioCancelled(exec(), scenario(), "reason-A", ts);
            var e2 = new ScenarioCancelled(exec(), scenario(), "reason-B", ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new ScenarioCancelled(exec(), scenario(), "timeout", ts);
            var e2 = new ScenarioCancelled(exec(), scenario(), "timeout", ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("scenarioId null leve NullPointerException")
        void nullScenarioIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new ScenarioCancelled(exec(), null, "reason", now()));
        }
    }

    // ─── PhaseStarted ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PhaseStarted")
    class PhaseStartedTest {

        @Test
        @DisplayName("instanciable pour chaque Phase")
        void instantiableForEachPhase() {
            var ts = now();
            for (var phase : Phase.values()) {
                var e = new PhaseStarted(exec(), phase, ts);
                assertEquals(exec(), e.executionId());
                assertEquals(phase, e.phase());
                assertEquals(ts, e.timestamp());
            }
        }

        @Test
        @DisplayName("phase different -> pas egaux")
        void differentPhaseNotEqual() {
            var ts = now();
            var e1 = new PhaseStarted(exec(), Phase.PREPARATION, ts);
            var e2 = new PhaseStarted(exec(), Phase.INJECTION, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new PhaseStarted(exec(), Phase.ASSERTION, ts);
            var e2 = new PhaseStarted(exec(), Phase.ASSERTION, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("phase null leve NullPointerException")
        void nullPhaseThrows() {
            assertThrows(NullPointerException.class, () ->
                new PhaseStarted(exec(), null, now()));
        }
    }

    // ─── PhaseCompleted ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PhaseCompleted")
    class PhaseCompletedTest {

        @Test
        @DisplayName("instanciable avec chaque PhaseStatus")
        void instantiableForEachStatus() {
            var ts = now();
            for (var status : PhaseStatus.values()) {
                var e = new PhaseCompleted(exec(), Phase.INJECTION, status, ts);
                assertEquals(exec(), e.executionId());
                assertEquals(Phase.INJECTION, e.phase());
                assertEquals(status, e.status());
                assertEquals(ts, e.timestamp());
            }
        }

        @Test
        @DisplayName("status different -> pas egaux")
        void differentStatusNotEqual() {
            var ts = now();
            var e1 = new PhaseCompleted(exec(), Phase.INJECTION, PhaseStatus.COMPLETED, ts);
            var e2 = new PhaseCompleted(exec(), Phase.INJECTION, PhaseStatus.FAILED, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new PhaseCompleted(exec(), Phase.PREPARATION, PhaseStatus.RUNNING, ts);
            var e2 = new PhaseCompleted(exec(), Phase.PREPARATION, PhaseStatus.RUNNING, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("status null leve NullPointerException")
        void nullStatusThrows() {
            assertThrows(NullPointerException.class, () ->
                new PhaseCompleted(exec(), Phase.PREPARATION, null, now()));
        }
    }

    // ─── TaskDispatched ─────────────────────────────────────────────────

    @Nested
    @DisplayName("TaskDispatched")
    class TaskDispatchedTest {

        @Test
        @DisplayName("instanciable avec taskName String")
        void instantiableWithStringTaskName() {
            var ts = now();
            var e = new TaskDispatched(exec(), task(), "gatling-http", msg(), ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.taskId());
            assertEquals("gatling-http", e.taskName());
            assertEquals(msg(), e.messageId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("taskName est bien un String (pas d'enum TaskType)")
        void taskNameIsString() {
            var e = new TaskDispatched(exec(), task(), "any-custom-task-name", msg(), now());
            assertTrue(e.taskName() instanceof String);
            assertEquals("any-custom-task-name", e.taskName());
        }

        @Test
        @DisplayName("taskName different -> pas egaux")
        void differentTaskNameNotEqual() {
            var ts = now();
            var e1 = new TaskDispatched(exec(), task(), "task-A", msg(), ts);
            var e2 = new TaskDispatched(exec(), task(), "task-B", msg(), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("messageId different -> pas egaux")
        void differentMessageIdNotEqual() {
            var ts = now();
            var e1 = new TaskDispatched(exec(), task(), "task", MessageId.of("msg-1"), ts);
            var e2 = new TaskDispatched(exec(), task(), "task", MessageId.of("msg-2"), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new TaskDispatched(exec(), task(), "gatling", msg(), ts);
            var e2 = new TaskDispatched(exec(), task(), "gatling", msg(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("taskName null leve NullPointerException")
        void nullTaskNameThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskDispatched(exec(), task(), null, msg(), now()));
        }

        @Test
        @DisplayName("messageId null leve NullPointerException")
        void nullMessageIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskDispatched(exec(), task(), "task", null, now()));
        }
    }

    // ─── TaskClaimedByAgent ─────────────────────────────────────────────

    @Nested
    @DisplayName("TaskClaimedByAgent")
    class TaskClaimedByAgentTest {

        @Test
        @DisplayName("instanciable")
        void instantiable() {
            var ts = now();
            var e = new TaskClaimedByAgent(exec(), task(), agent(), msg(), ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.taskId());
            assertEquals(agent(), e.agentId());
            assertEquals(msg(), e.messageId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("agentId different -> pas egaux")
        void differentAgentIdNotEqual() {
            var ts = now();
            var e1 = new TaskClaimedByAgent(exec(), task(), AgentId.of("agent-1"), msg(), ts);
            var e2 = new TaskClaimedByAgent(exec(), task(), AgentId.of("agent-2"), msg(), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new TaskClaimedByAgent(exec(), task(), agent(), msg(), ts);
            var e2 = new TaskClaimedByAgent(exec(), task(), agent(), msg(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("agentId null leve NullPointerException")
        void nullAgentIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskClaimedByAgent(exec(), task(), null, msg(), now()));
        }

        @Test
        @DisplayName("messageId null leve NullPointerException")
        void nullMessageIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskClaimedByAgent(exec(), task(), agent(), null, now()));
        }
    }

    // ─── TaskWorkInProgress ─────────────────────────────────────────────

    @Nested
    @DisplayName("TaskWorkInProgress")
    class TaskWorkInProgressTest {

        @Test
        @DisplayName("instanciable avec progressPercent 0")
        void instantiableWithZeroPercent() {
            var ts = now();
            var e = new TaskWorkInProgress(exec(), task(), agent(), 0, "starting", ts);

            assertEquals(0, e.progressPercent());
            assertEquals("starting", e.statusMessage());
        }

        @Test
        @DisplayName("instanciable avec progressPercent 50")
        void instantiableWithFiftyPercent() {
            var e = new TaskWorkInProgress(exec(), task(), agent(), 50, "half done", now());

            assertEquals(50, e.progressPercent());
        }

        @Test
        @DisplayName("instanciable avec progressPercent 100")
        void instantiableWithHundredPercent() {
            var e = new TaskWorkInProgress(exec(), task(), agent(), 100, "done", now());

            assertEquals(100, e.progressPercent());
        }

        @Test
        @DisplayName("statusMessage peut etre null")
        void statusMessageCanBeNull() {
            var e = new TaskWorkInProgress(exec(), task(), agent(), 50, null, now());
            assertNull(e.statusMessage());
        }

        @Test
        @DisplayName("progressPercent negatif leve IllegalArgumentException")
        void negativeProgressThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TaskWorkInProgress(exec(), task(), agent(), -1, "invalid", now()));
        }

        @Test
        @DisplayName("progressPercent > 100 leve IllegalArgumentException")
        void progressAbove100Throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new TaskWorkInProgress(exec(), task(), agent(), 101, "invalid", now()));
        }

        @Test
        @DisplayName("progressPercent different -> pas egaux")
        void differentProgressNotEqual() {
            var ts = now();
            var e1 = new TaskWorkInProgress(exec(), task(), agent(), 10, "msg", ts);
            var e2 = new TaskWorkInProgress(exec(), task(), agent(), 20, "msg", ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new TaskWorkInProgress(exec(), task(), agent(), 75, "running", ts);
            var e2 = new TaskWorkInProgress(exec(), task(), agent(), 75, "running", ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("factory of() fonctionne")
        void factoryOfWorks() {
            var ts = now();
            var e = TaskWorkInProgress.of(exec(), task(), agent(), 42, "working", ts);

            assertEquals(exec(), e.executionId());
            assertEquals(42, e.progressPercent());
            assertEquals("working", e.statusMessage());
        }

        @Test
        @DisplayName("agentId null leve NullPointerException")
        void nullAgentIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskWorkInProgress(exec(), task(), null, 50, "msg", now()));
        }
    }

    // ─── TaskStarted ────────────────────────────────────────────────────

    @Nested
    @DisplayName("TaskStarted")
    class TaskStartedTest {

        @Test
        @DisplayName("instanciable")
        void instantiable() {
            var ts = now();
            var e = new TaskStarted(exec(), task(), agent(), ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.taskId());
            assertEquals(agent(), e.agentId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new TaskStarted(exec(), task(), agent(), ts);
            var e2 = new TaskStarted(exec(), task(), agent(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("taskId different -> pas egaux")
        void differentTaskIdNotEqual() {
            var ts = now();
            var e1 = new TaskStarted(exec(), TaskId.of("t1"), agent(), ts);
            var e2 = new TaskStarted(exec(), TaskId.of("t2"), agent(), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("agentId null leve NullPointerException")
        void nullAgentIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskStarted(exec(), task(), null, now()));
        }

        @Test
        @DisplayName("timestamp null leve NullPointerException")
        void nullTimestampThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskStarted(exec(), task(), agent(), null));
        }
    }

    // ─── TaskCompleted ──────────────────────────────────────────────────

    @Nested
    @DisplayName("TaskCompleted")
    class TaskCompletedTest {

        @Test
        @DisplayName("instanciable")
        void instantiable() {
            var ts = now();
            var dur = Duration.ofSeconds(30);
            var result = successResult();
            var e = new TaskCompleted(exec(), task(), agent(), result, dur, ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.taskId());
            assertEquals(agent(), e.agentId());
            assertEquals(result, e.result());
            assertEquals(dur, e.duration());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("result different -> pas egaux")
        void differentResultNotEqual() {
            var ts = now();
            var dur = Duration.ofSeconds(10);
            var r1 = TaskResult.success(task(), "task-A", dur, Map.of());
            var r2 = TaskResult.success(task(), "task-B", dur, Map.of());
            var e1 = new TaskCompleted(exec(), task(), agent(), r1, dur, ts);
            var e2 = new TaskCompleted(exec(), task(), agent(), r2, dur, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var dur = Duration.ofSeconds(15);
            var result = successResult();
            var e1 = new TaskCompleted(exec(), task(), agent(), result, dur, ts);
            var e2 = new TaskCompleted(exec(), task(), agent(), result, dur, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("result null leve NullPointerException")
        void nullResultThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskCompleted(exec(), task(), agent(), null, Duration.ofSeconds(1), now()));
        }

        @Test
        @DisplayName("duration null leve NullPointerException")
        void nullDurationThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskCompleted(exec(), task(), agent(), successResult(), null, now()));
        }
    }

    // ─── TaskFailed ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("TaskFailed")
    class TaskFailedTest {

        @Test
        @DisplayName("instanciable")
        void instantiable() {
            var ts = now();
            var e = new TaskFailed(exec(), task(), agent(), "connection refused", 1, ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.taskId());
            assertEquals(agent(), e.agentId());
            assertEquals("connection refused", e.error());
            assertEquals(1, e.attempt());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("error peut etre null")
        void errorCanBeNull() {
            var e = new TaskFailed(exec(), task(), agent(), null, 1, now());
            assertNull(e.error());
        }

        @Test
        @DisplayName("attempt >= 1 accepte")
        void attemptOneOrAboveAccepted() {
            assertDoesNotThrow(() -> new TaskFailed(exec(), task(), agent(), "err", 1, now()));
            assertDoesNotThrow(() -> new TaskFailed(exec(), task(), agent(), "err", 3, now()));
            assertDoesNotThrow(() -> new TaskFailed(exec(), task(), agent(), "err", 10, now()));
        }

        @Test
        @DisplayName("attempt 0 leve IllegalArgumentException")
        void attemptZeroThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TaskFailed(exec(), task(), agent(), "err", 0, now()));
        }

        @Test
        @DisplayName("attempt negatif leve IllegalArgumentException")
        void negativeAttemptThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TaskFailed(exec(), task(), agent(), "err", -1, now()));
        }

        @Test
        @DisplayName("attempt different -> pas egaux")
        void differentAttemptNotEqual() {
            var ts = now();
            var e1 = new TaskFailed(exec(), task(), agent(), "err", 1, ts);
            var e2 = new TaskFailed(exec(), task(), agent(), "err", 2, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new TaskFailed(exec(), task(), agent(), "timeout", 3, ts);
            var e2 = new TaskFailed(exec(), task(), agent(), "timeout", 3, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("taskId null leve NullPointerException")
        void nullTaskIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskFailed(exec(), null, agent(), "err", 1, now()));
        }
    }

    // ─── TaskRetried ────────────────────────────────────────────────────

    @Nested
    @DisplayName("TaskRetried")
    class TaskRetriedTest {

        @Test
        @DisplayName("instanciable")
        void instantiable() {
            var ts = now();
            var next = ts.plusSeconds(30);
            var e = new TaskRetried(exec(), task(), 1, next, ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.taskId());
            assertEquals(1, e.attempt());
            assertEquals(next, e.nextAttemptAt());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("attempt >= 1 accepte")
        void attemptOneOrAboveAccepted() {
            var next = now().plusSeconds(10);
            assertDoesNotThrow(() -> new TaskRetried(exec(), task(), 1, next, now()));
            assertDoesNotThrow(() -> new TaskRetried(exec(), task(), 3, next, now()));
        }

        @Test
        @DisplayName("attempt 0 leve IllegalArgumentException")
        void attemptZeroThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TaskRetried(exec(), task(), 0, now().plusSeconds(10), now()));
        }

        @Test
        @DisplayName("attempt negatif leve IllegalArgumentException")
        void negativeAttemptThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new TaskRetried(exec(), task(), -1, now().plusSeconds(10), now()));
        }

        @Test
        @DisplayName("nextAttemptAt different -> pas egaux")
        void differentNextAttemptNotEqual() {
            var ts = now();
            var e1 = new TaskRetried(exec(), task(), 1, ts.plusSeconds(10), ts);
            var e2 = new TaskRetried(exec(), task(), 1, ts.plusSeconds(60), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("attempt different -> pas egaux")
        void differentAttemptNotEqual() {
            var ts = now();
            var next = ts.plusSeconds(30);
            var e1 = new TaskRetried(exec(), task(), 1, next, ts);
            var e2 = new TaskRetried(exec(), task(), 2, next, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var next = ts.plusSeconds(30);
            var e1 = new TaskRetried(exec(), task(), 2, next, ts);
            var e2 = new TaskRetried(exec(), task(), 2, next, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("nextAttemptAt null leve NullPointerException")
        void nullNextAttemptAtThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskRetried(exec(), task(), 1, null, now()));
        }

        @Test
        @DisplayName("executionId null leve NullPointerException")
        void nullExecutionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new TaskRetried(null, task(), 1, now().plusSeconds(10), now()));
        }
    }

    // ─── toString verifications ─────────────────────────────────────────

    @Nested
    @DisplayName("toString coverage")
    class ToStringCoverage {

        @Test
        @DisplayName("tous les events ont toString avec leur nom")
        void allEventsToStringContainsName() {
            var ts = now();
            var dur = Duration.ofSeconds(10);

            assertTrue(new ScenarioStarted(exec(), scenario(), ts).toString().contains("ScenarioStarted"));
            assertTrue(new ScenarioFinished(exec(), scenario(), Verdict.SUCCESS, dur, ts).toString().contains("ScenarioFinished"));
            assertTrue(new ScenarioCancelled(exec(), scenario(), "reason", ts).toString().contains("ScenarioCancelled"));
            assertTrue(new PhaseStarted(exec(), Phase.PREPARATION, ts).toString().contains("PhaseStarted"));
            assertTrue(new PhaseCompleted(exec(), Phase.INJECTION, PhaseStatus.COMPLETED, ts).toString().contains("PhaseCompleted"));
            assertTrue(new TaskDispatched(exec(), task(), "task", msg(), ts).toString().contains("TaskDispatched"));
            assertTrue(new TaskClaimedByAgent(exec(), task(), agent(), msg(), ts).toString().contains("TaskClaimedByAgent"));
            assertTrue(new TaskWorkInProgress(exec(), task(), agent(), 50, "msg", ts).toString().contains("TaskWorkInProgress"));
            assertTrue(new TaskStarted(exec(), task(), agent(), ts).toString().contains("TaskStarted"));
            assertTrue(new TaskCompleted(exec(), task(), agent(), successResult(), dur, ts).toString().contains("TaskCompleted"));
            assertTrue(new TaskFailed(exec(), task(), agent(), "err", 1, ts).toString().contains("TaskFailed"));
            assertTrue(new TaskRetried(exec(), task(), 1, ts.plusSeconds(10), ts).toString().contains("TaskRetried"));
        }
    }
}
