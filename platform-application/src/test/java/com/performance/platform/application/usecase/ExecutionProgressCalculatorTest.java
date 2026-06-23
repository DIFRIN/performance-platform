package com.performance.platform.application.usecase;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de {@link ExecutionProgressCalculator}.
 * Verifie le calcul ok/ko/running a partir des statuts reels des TaskResult.
 */
@DisplayName("ExecutionProgressCalculator")
class ExecutionProgressCalculatorTest {

    private static final ScenarioId SCENARIO_ID = ScenarioId.of(UUID.randomUUID().toString());
    private static final ExecutionId EXEC_ID = ExecutionId.generate();
    private static final AgentId AGENT_A = AgentId.generate();
    private static final AgentId AGENT_B = AgentId.generate();

    private ExecutionProgressCalculator calculator;
    private ExecutionState state;

    @BeforeEach
    void setUp() {
        calculator = new ExecutionProgressCalculator();
        state = new ExecutionState(
                EXEC_ID, SCENARIO_ID, ExecutionStatus.RUNNING, Map.of(),
                ExecutionContext.initial(EXEC_ID, SCENARIO_ID),
                Instant.now(), Instant.now()
        );
    }

    // --- Cas nominal

    @Test
    @DisplayName("map vide => progress 0/0/0/0")
    void emptyMapReturnsZeroProgress() {
        var progress = calculator.calculate(state, Map.of());

        assertEquals(0, progress.total());
        assertEquals(0, progress.ok());
        assertEquals(0, progress.ko());
        assertEquals(0, progress.running());
    }

    @Test
    @DisplayName("tache avec SUCCESS => ok=1")
    void taskWithSuccessResultCountsAsOk() {
        var taskId = TaskId.of("t1");
        var result = TaskResult.success(taskId, "prep", Duration.ofMillis(100), Map.of());
        var taskResults = Map.of(taskId, Map.of(AGENT_A, result));

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(1, progress.ok());
        assertEquals(0, progress.ko());
        assertEquals(0, progress.running());
    }

    @Test
    @DisplayName("tache avec FAILED => ko=1")
    void taskWithFailedResultCountsAsKo() {
        var taskId = TaskId.of("t1");
        var result = TaskResult.failed(taskId, "prep", Duration.ofMillis(50), "erreur", null);
        var taskResults = Map.of(taskId, Map.of(AGENT_A, result));

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(0, progress.ok());
        assertEquals(1, progress.ko());
        assertEquals(0, progress.running());
    }

    @Test
    @DisplayName("tache avec SKIPPED => ko=1")
    void taskWithSkippedResultCountsAsKo() {
        var taskId = TaskId.of("t1");
        var result = TaskResult.skipped(taskId, "prep", "skipped reason");
        var taskResults = Map.of(taskId, Map.of(AGENT_A, result));

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(0, progress.ok());
        assertEquals(1, progress.ko());
        assertEquals(0, progress.running());
    }

    @Test
    @DisplayName("tache avec TIMEOUT => ko=1")
    void taskWithTimeoutResultCountsAsKo() {
        var taskId = TaskId.of("t1");
        var result = new TaskResult(taskId, "prep", TaskStatus.TIMEOUT, Duration.ofSeconds(30),
                Map.of(), "timeout", null, Instant.now());
        var taskResults = Map.of(taskId, Map.of(AGENT_A, result));

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(0, progress.ok());
        assertEquals(1, progress.ko());
        assertEquals(0, progress.running());
    }

    @Test
    @DisplayName("tache sans resultat (map interne vide) => running=1")
    void taskWithNoResultCountsAsRunning() {
        var taskId = TaskId.of("t1");
        Map<TaskId, Map<AgentId, TaskResult>> taskResults = Map.of(taskId, Map.of());

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(0, progress.ok());
        assertEquals(0, progress.ko());
        assertEquals(1, progress.running());
    }

    // --- Scenario mixte

    @Test
    @DisplayName("scenario mixte : 1 ok + 1 ko + 1 running")
    void mixedScenario() {
        var t1 = TaskId.of("t1");
        var t2 = TaskId.of("t2");
        var t3 = TaskId.of("t3");

        var successResult = TaskResult.success(t1, "prep", Duration.ofMillis(100), Map.of());
        var failedResult = TaskResult.failed(t2, "inject", Duration.ofMillis(50), "erreur", null);

        Map<TaskId, Map<AgentId, TaskResult>> taskResults = new HashMap<>();
        taskResults.put(t1, Map.of(AGENT_A, successResult));
        taskResults.put(t2, Map.of(AGENT_A, failedResult));
        taskResults.put(t3, Map.of()); // running

        var progress = calculator.calculate(state, taskResults);

        assertEquals(3, progress.total());
        assertEquals(1, progress.ok());
        assertEquals(1, progress.ko());
        assertEquals(1, progress.running());
    }

    // --- Multi-agent (ADR-011)

    @Test
    @DisplayName("tache multi-agent avec un SUCCESS => ok=1")
    void multiAgentWithOneSuccessCountsAsOk() {
        var taskId = TaskId.of("t1");
        var success = TaskResult.success(taskId, "prep", Duration.ofMillis(100), Map.of());
        var failed = TaskResult.failed(taskId, "prep", Duration.ofMillis(50), "erreur", null);

        var taskResults = Map.of(taskId, Map.of(AGENT_A, success, AGENT_B, failed));

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(1, progress.ok());
        assertEquals(0, progress.ko());
        assertEquals(0, progress.running());
    }

    @Test
    @DisplayName("tache multi-agent sans SUCCESS => ko=1")
    void multiAgentWithNoSuccessCountsAsKo() {
        var taskId = TaskId.of("t1");
        var failedA = TaskResult.failed(taskId, "prep", Duration.ofMillis(50), "erreur A", null);
        var failedB = TaskResult.failed(taskId, "prep", Duration.ofMillis(60), "erreur B", null);

        var taskResults = Map.of(taskId, Map.of(AGENT_A, failedA, AGENT_B, failedB));

        var progress = calculator.calculate(state, taskResults);

        assertEquals(1, progress.total());
        assertEquals(0, progress.ok());
        assertEquals(1, progress.ko());
        assertEquals(0, progress.running());
    }

    // --- Completude

    @Test
    @DisplayName("total = ok + ko + running")
    void totalEqualsOkPlusKoPlusRunning() {
        var t1 = TaskId.of("t1");
        var t2 = TaskId.of("t2");
        var t3 = TaskId.of("t3");
        var t4 = TaskId.of("t4");

        Map<TaskId, Map<AgentId, TaskResult>> taskResults = new HashMap<>();
        taskResults.put(t1, Map.of(AGENT_A, TaskResult.success(t1, "a", Duration.ZERO, Map.of())));
        taskResults.put(t2, Map.of(AGENT_A, TaskResult.success(t2, "b", Duration.ZERO, Map.of())));
        taskResults.put(t3, Map.of(AGENT_A, TaskResult.failed(t3, "c", Duration.ZERO, "ko", null)));
        taskResults.put(t4, Map.of()); // running

        var progress = calculator.calculate(state, taskResults);

        assertEquals(progress.total(), progress.ok() + progress.ko() + progress.running());
        assertEquals(4, progress.total());
        assertEquals(2, progress.ok());
        assertEquals(1, progress.ko());
        assertEquals(1, progress.running());
    }

    // --- Invariants ExecutionProgress

    @Test
    @DisplayName("ExecutionProgress rejette les compteurs negatifs")
    void executionProgressRejectsNegativeCounters() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionProgress(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionProgress(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionProgress(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionProgress(0, 0, 0, -1));
    }

    // --- Validation des arguments

    @Test
    @DisplayName("calculate leve NullPointerException si state null")
    void calculateRejectsNullState() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, Map.of()));
    }

    @Test
    @DisplayName("calculate leve NullPointerException si taskResults null")
    void calculateRejectsNullTaskResults() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(state, null));
    }

    @Test
    @DisplayName("calculate retourne un ExecutionProgress non null")
    void calculateReturnsNonNull() {
        var result = calculator.calculate(state, Map.of());
        assertNotNull(result);
    }
}
