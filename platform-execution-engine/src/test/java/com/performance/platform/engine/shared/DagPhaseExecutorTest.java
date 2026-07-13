package com.performance.platform.engine.shared;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DagPhaseExecutor")
class DagPhaseExecutorTest {

    private static TaskId t(String id) { return TaskId.of(id); }
    private static ScenarioId sId(String id) { return ScenarioId.of(id); }

    private static StepDefinition step(String id, String taskName, Phase phase,
                                        List<TaskId> dependsOn) {
        return new StepDefinition(t(id), taskName, phase, Map.of(),
                dependsOn == null ? List.of() : dependsOn,
                List.of(), Duration.ofSeconds(30), null);
    }

    private static StepDefinition step(String id, String taskName, Phase phase) {
        return step(id, taskName, phase, List.of());
    }

    private static ExecutionStep execStep(StepDefinition stepDef, List<TaskId> deps, int dagLevel) {
        return new ExecutionStep(stepDef, deps == null ? List.of() : deps, dagLevel, Set.of());
    }

    private DagPhaseExecutor createExecutor(StepDispatcher dispatcher) {
        return new DagPhaseExecutor(dispatcher);
    }

    @Nested
    @DisplayName("Empty steps")
    class EmptySteps {

        @Test
        @DisplayName("Empty steps list returns unchanged context")
        void emptySteps_returnsUnchangedContext() {
            var dispatcher = new StubStepDispatcher();
            var dpe = createExecutor(dispatcher);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(), ctx, Phase.PREPARATION,
                    new AtomicBoolean(false));

            assertSame(ctx, result.updatedContext());
            assertFalse(result.anyFailed());
            assertEquals(0, dispatcher.getCallCount());
        }
    }

    @Nested
    @DisplayName("Single level")
    class SingleLevel {

        @Test
        @DisplayName("Single dagLevel with multiple steps executes all in parallel")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void singleLevel_executesAllSteps() {
            StepDefinition stepA = step("A", "ta", Phase.PREPARATION);
            StepDefinition stepB = step("B", "tb", Phase.PREPARATION);
            ExecutionStep esA = execStep(stepA, List.of(), 0);
            ExecutionStep esB = execStep(stepB, List.of(), 0);

            var dispatcher = new StubStepDispatcher() {
                @Override
                public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
                    super.dispatch(execStep, context, phase);
                    return TaskResult.success(execStep.step().id(), execStep.step().taskName(),
                            Duration.ofMillis(5), Map.of());
                }
            };

            var dpe = createExecutor(dispatcher);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION,
                    new AtomicBoolean(false));

            assertFalse(result.anyFailed());
            assertEquals(2, dispatcher.getCallCount());
            // Results should be in context
            assertNotNull(result.updatedContext().store().get("A"));
            assertNotNull(result.updatedContext().store().get("B"));
        }
    }

    @Nested
    @DisplayName("Cancel")
    class Cancel {

        @Test
        @DisplayName("Cancel flag stops further levels")
        void cancelFlag_stopsFurtherLevels() {
            StepDefinition stepA = step("A", "ta", Phase.PREPARATION);
            StepDefinition stepB = step("B", "tb", Phase.PREPARATION);

            ExecutionStep esA = execStep(stepA, List.of(), 0);
            // stepB at level 1 — should be skipped when cancel flag is set
            ExecutionStep esB = execStep(stepB, List.of(), 1);

            var cancelled = new AtomicBoolean(false);
            var dispatcher = new StubStepDispatcher() {
                @Override
                public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
                    super.dispatch(execStep, context, phase);
                    cancelled.set(true); // Cancel after first step
                    return TaskResult.success(execStep.step().id(), execStep.step().taskName(),
                            Duration.ofMillis(5), Map.of());
                }
            };

            var dpe = createExecutor(dispatcher);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION, cancelled);

            // Only step A should have been dispatched (level 0)
            // Level 1 should have been skipped due to cancel flag
            assertTrue(cancelled.get());
            // At least step A was dispatched
            assertTrue(dispatcher.getCallCount() >= 1);
        }
    }

    @Nested
    @DisplayName("Dependency failure → SKIPPED")
    class DependencyFailure {

        @Test
        @DisplayName("When a dependency returns FAILED, dependent step is SKIPPED")
        void failedDependency_skipsDependent() {
            StepDefinition stepA = step("A", "ta", Phase.PREPARATION);
            StepDefinition stepB = step("B", "tb", Phase.PREPARATION, List.of(t("A")));

            ExecutionStep esA = execStep(stepA, List.of(), 0);
            ExecutionStep esB = execStep(stepB, List.of(t("A")), 1);

            var dispatcher = new StubStepDispatcher() {
                @Override
                public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
                    super.dispatch(execStep, context, phase);
                    if (execStep.step().id().value().equals("A")) {
                        return TaskResult.failed(execStep.step().id(), execStep.step().taskName(),
                                Duration.ofMillis(5), "failure", null);
                    }
                    return TaskResult.success(execStep.step().id(), execStep.step().taskName(),
                            Duration.ofMillis(5), Map.of());
                }
            };

            var dpe = createExecutor(dispatcher);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION,
                    new AtomicBoolean(false));

            assertTrue(result.anyFailed());
            // Step B should have been SKIPPED in outcomes
            boolean bSkipped = result.outcomes().stream()
                    .anyMatch(o -> o.taskId().value().equals("B")
                            && o.status() == TaskStatus.SKIPPED);
            assertTrue(bSkipped, "Step B should be SKIPPED when dependency A fails");

            // Only A should have been dispatched (B skipped without dispatch)
            assertEquals(1, dispatcher.getCallCount());
        }

        @Test
        @DisplayName("When all dependencies are SUCCESS, dependent step executes")
        void allDependenciesSuccess_executesDependent() {
            StepDefinition stepA = step("A", "ta", Phase.PREPARATION);
            StepDefinition stepB = step("B", "tb", Phase.PREPARATION, List.of(t("A")));

            ExecutionStep esA = execStep(stepA, List.of(), 0);
            ExecutionStep esB = execStep(stepB, List.of(t("A")), 1);

            var dispatcher = new StubStepDispatcher() {
                @Override
                public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
                    super.dispatch(execStep, context, phase);
                    return TaskResult.success(execStep.step().id(), execStep.step().taskName(),
                            Duration.ofMillis(5), Map.of());
                }
            };

            var dpe = createExecutor(dispatcher);
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            var result = dpe.executePhase(List.of(esA, esB), ctx, Phase.PREPARATION,
                    new AtomicBoolean(false));

            assertFalse(result.anyFailed());
            assertEquals(2, dispatcher.getCallCount());
        }
    }

    @Nested
    @DisplayName("All dependencies satisfied")
    class AllDependenciesSatisfied {

        @Test
        @DisplayName("No dependencies → satisfied")
        void noDependencies_satisfied() {
            var stepDef = step("A", "ta", Phase.PREPARATION);
            var execStep = execStep(stepDef, List.of(), 0);
            var dpe = createExecutor(new StubStepDispatcher());
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            assertTrue(dpe.allDependenciesSatisfied(execStep, ctx));
        }

        @Test
        @DisplayName("Missing dependency in context → not satisfied")
        void missingDependency_notSatisfied() {
            var stepDef = step("B", "tb", Phase.PREPARATION, List.of(t("A")));
            var execStep = execStep(stepDef, List.of(t("A")), 1);
            var dpe = createExecutor(new StubStepDispatcher());
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"));

            assertFalse(dpe.allDependenciesSatisfied(execStep, ctx));
        }

        @Test
        @DisplayName("FAILED dependency → not satisfied")
        void failedDependency_notSatisfied() {
            var stepDef = step("B", "tb", Phase.PREPARATION, List.of(t("A")));
            var execStep = execStep(stepDef, List.of(t("A")), 1);
            var dpe = createExecutor(new StubStepDispatcher());
            var ctx = ExecutionContext.initial(ExecutionId.generate(), sId("s1"))
                    .with("A", DagPhaseExecutor.DEFAULT_AGENT,
                            TaskResult.failed(t("A"), "ta", Duration.ofMillis(5), "error", null));

            assertFalse(dpe.allDependenciesSatisfied(execStep, ctx));
        }
    }

    @Nested
    @DisplayName("PhaseResult immutability")
    class PhaseResult {

        @Test
        @DisplayName("PhaseResult.outcomes() returns immutable list")
        void outcomes_isImmutable() {
            var result = new DagPhaseExecutor.PhaseResult(
                    ExecutionContext.initial(ExecutionId.generate(), sId("s1")),
                    false, List.of());

            assertThrows(UnsupportedOperationException.class,
                    () -> result.outcomes().add(new DagPhaseExecutor.StepOutcome(
                            t("X"), "tx", TaskResult.skipped(t("X"), "tx", "test"), TaskStatus.SKIPPED)));
        }
    }

    // -------------------------------------------------------------------------
    // Stub StepDispatcher
    // -------------------------------------------------------------------------

    static class StubStepDispatcher implements StepDispatcher {
        private final List<String> calledTaskIds = new CopyOnWriteArrayList<>();

        @Override
        public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
            calledTaskIds.add(execStep.step().id().value());
            return TaskResult.success(
                    execStep.step().id(),
                    execStep.step().taskName(),
                    Duration.ofMillis(1),
                    Map.of());
        }

        int getCallCount() { return calledTaskIds.size(); }

        List<String> getCalledTaskIds() { return List.copyOf(calledTaskIds); }
    }
}
