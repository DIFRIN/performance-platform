package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour ExecutionPlan, ExecutionStep, ExecutionState — immuabilite, copies defensives, validation.
 */
@DisplayName("ExecutionPlan / ExecutionStep / ExecutionState")
class ExecutionPlanTest {

    private static ExecutionId execId() {
        return ExecutionId.generate();
    }

    private static ScenarioId scenarioId() {
        return ScenarioId.of("scenario-test");
    }

    private static StepDefinition validStepDef() {
        return new StepDefinition(
            TaskId.of("step-1"), "http-get", Phase.INJECTION,
            Map.of(), List.of(), List.of(), null, null
        );
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(execId(), scenarioId());
    }

    // ════════════════════════════════════════════════════════════════════
    // ExecutionStep
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ExecutionStep")
    class ExecutionStepTests {

        @Test
        @DisplayName("construction nominale avec tous les champs")
        void nominalConstruction() {
            var step = validStepDef();
            var deps = List.of(TaskId.of("dep-1"));
            var ctxKeys = Set.of("key1", "key2");

            var execStep = new ExecutionStep(step, deps, 2, ctxKeys);

            assertSame(step, execStep.step());
            assertEquals(deps, execStep.dependencies());
            assertEquals(2, execStep.dagLevel());
            assertEquals(ctxKeys, execStep.requiredContextKeys());
        }

        @Test
        @DisplayName("collections null deviennent vides")
        void nullCollectionsDefaultToEmpty() {
            var execStep = new ExecutionStep(validStepDef(), null, 0, null);

            assertEquals(List.of(), execStep.dependencies());
            assertEquals(Set.of(), execStep.requiredContextKeys());
        }

        @Test
        @DisplayName("dagLevel negatif leve IllegalArgumentException")
        void negativeDagLevelThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                new ExecutionStep(validStepDef(), List.of(), -1, Set.of())
            );
        }

        @Test
        @DisplayName("dagLevel 0 est valide")
        void dagLevelZeroIsValid() {
            var step = new ExecutionStep(validStepDef(), List.of(), 0, Set.of());
            assertEquals(0, step.dagLevel());
        }

        @Test
        @DisplayName("modifier la liste dependencies source apres construction ne modifie pas le record")
        void dependenciesDefensiveCopy() {
            var mutableDeps = new ArrayList<>(List.of(TaskId.of("dep-1")));
            var execStep = new ExecutionStep(validStepDef(), mutableDeps, 0, Set.of());

            mutableDeps.add(TaskId.of("dep-2"));

            assertEquals(1, execStep.dependencies().size());
        }

        @Test
        @DisplayName("modifier le Set requiredContextKeys source apres construction ne modifie pas le record")
        void requiredContextKeysDefensiveCopy() {
            var mutableKeys = new java.util.HashSet<>(Set.of("key-a"));
            var execStep = new ExecutionStep(validStepDef(), List.of(), 0, mutableKeys);

            mutableKeys.add("key-b");

            assertEquals(Set.of("key-a"), execStep.requiredContextKeys());
        }

        @Test
        @DisplayName("dependencies() retourne une liste non-modifiable")
        void dependenciesUnmodifiable() {
            var execStep = new ExecutionStep(validStepDef(), List.of(TaskId.of("d")), 0, Set.of());
            assertThrows(UnsupportedOperationException.class,
                () -> execStep.dependencies().add(TaskId.of("x")));
        }

        @Test
        @DisplayName("requiredContextKeys() retourne un Set non-modifiable")
        void requiredContextKeysUnmodifiable() {
            var execStep = new ExecutionStep(validStepDef(), List.of(), 0, Set.of("k"));
            assertThrows(UnsupportedOperationException.class,
                () -> execStep.requiredContextKeys().add("x"));
        }

        @Test
        @DisplayName("step null leve NullPointerException")
        void nullStepThrows() {
            assertThrows(NullPointerException.class, () ->
                new ExecutionStep(null, List.of(), 0, Set.of())
            );
        }

        @Test
        @DisplayName("deux records identiques sont egaux")
        void identicalRecordsEqual() {
            var step1 = new ExecutionStep(validStepDef(), List.of(TaskId.of("d")), 1, Set.of("k"));
            var step2 = new ExecutionStep(validStepDef(), List.of(TaskId.of("d")), 1, Set.of("k"));
            assertEquals(step1, step2);
            assertEquals(step1.hashCode(), step2.hashCode());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ExecutionPlan
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ExecutionPlan")
    class ExecutionPlanTests {

        private ExecutionStep makeStep(String id) {
            return new ExecutionStep(
                new StepDefinition(TaskId.of(id), "dummy", Phase.PREPARATION,
                    Map.of(), List.of(), List.of(), null, null),
                List.of(), 0, Set.of()
            );
        }

        @Test
        @DisplayName("construction nominale avec etapes dans les 3 phases")
        void nominalConstruction() {
            var id = execId();
            var prep = List.of(makeStep("prep-1"));
            var inj = List.of(makeStep("inj-1"), makeStep("inj-2"));
            var assertSteps = List.of(makeStep("assert-1"));
            var ctx = emptyContext();

            var plan = new ExecutionPlan(id, scenarioId(), prep, inj, assertSteps, ctx);

            assertEquals(id, plan.id());
            assertEquals(scenarioId(), plan.scenarioId());
            assertEquals(prep, plan.preparationSteps());
            assertEquals(inj, plan.injectionSteps());
            assertEquals(assertSteps, plan.assertionSteps());
            assertSame(ctx, plan.initialContext());
        }

        @Test
        @DisplayName("listes null deviennent vides")
        void nullListsDefaultToEmpty() {
            var plan = new ExecutionPlan(execId(), scenarioId(), null, null, null, emptyContext());

            assertEquals(List.of(), plan.preparationSteps());
            assertEquals(List.of(), plan.injectionSteps());
            assertEquals(List.of(), plan.assertionSteps());
        }

        @Test
        @DisplayName("totalSteps retourne la somme des etapes des 3 phases")
        void totalStepsSumsAllPhases() {
            var plan = new ExecutionPlan(execId(), scenarioId(),
                List.of(makeStep("a"), makeStep("b")),
                List.of(makeStep("c")),
                List.of(makeStep("d"), makeStep("e"), makeStep("f")),
                emptyContext()
            );

            assertEquals(6, plan.totalSteps());
        }

        @Test
        @DisplayName("totalSteps = 0 quand toutes les listes sont vides")
        void totalStepsZeroWhenEmpty() {
            var plan = new ExecutionPlan(execId(), scenarioId(),
                List.of(), List.of(), List.of(), emptyContext());

            assertEquals(0, plan.totalSteps());
        }

        @Test
        @DisplayName("modifier une liste source apres construction ne modifie pas le record")
        void defensiveCopyOnConstruction() {
            var mutablePrep = new ArrayList<>(List.of(makeStep("prep-1")));
            var plan = new ExecutionPlan(execId(), scenarioId(), mutablePrep, List.of(), List.of(), emptyContext());

            mutablePrep.add(makeStep("prep-2"));

            assertEquals(1, plan.preparationSteps().size());
        }

        @Test
        @DisplayName("preparationSteps() retourne une liste non-modifiable")
        void preparationStepsUnmodifiable() {
            var plan = new ExecutionPlan(execId(), scenarioId(),
                List.of(makeStep("a")), List.of(), List.of(), emptyContext());

            assertThrows(UnsupportedOperationException.class,
                () -> plan.preparationSteps().add(makeStep("x")));
        }

        @Test
        @DisplayName("id null leve NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new ExecutionPlan(null, scenarioId(), List.of(), List.of(), List.of(), emptyContext())
            );
        }

        @Test
        @DisplayName("scenarioId null leve NullPointerException")
        void nullScenarioIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new ExecutionPlan(execId(), null, List.of(), List.of(), List.of(), emptyContext())
            );
        }

        @Test
        @DisplayName("initialContext null leve NullPointerException")
        void nullInitialContextThrows() {
            assertThrows(NullPointerException.class, () ->
                new ExecutionPlan(execId(), scenarioId(), List.of(), List.of(), List.of(), null)
            );
        }

        @Test
        @DisplayName("deux plans identiques sont egaux")
        void identicalPlansEqual() {
            var id = execId();
            var ctx = emptyContext();
            var steps = List.of(makeStep("a"));
            var p1 = new ExecutionPlan(id, scenarioId(), steps, List.of(), List.of(), ctx);
            var p2 = new ExecutionPlan(id, scenarioId(), steps, List.of(), List.of(), ctx);
            assertEquals(p1, p2);
            assertEquals(p1.hashCode(), p2.hashCode());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ExecutionState
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ExecutionState")
    class ExecutionStateTests {

        @Test
        @DisplayName("construction nominale")
        void nominalConstruction() {
            var id = execId();
            var now = Instant.now();
            var phaseStatuses = Map.of(
                Phase.PREPARATION, PhaseStatus.COMPLETED,
                Phase.INJECTION, PhaseStatus.RUNNING
            );
            var ctx = emptyContext();

            var state = new ExecutionState(id, scenarioId(), ExecutionStatus.RUNNING,
                phaseStatuses, ctx, now, now);

            assertEquals(id, state.id());
            assertEquals(scenarioId(), state.scenarioId());
            assertEquals(ExecutionStatus.RUNNING, state.status());
            assertEquals(phaseStatuses, state.phaseStatuses());
            assertSame(ctx, state.context());
            assertEquals(now, state.startedAt());
            assertEquals(now, state.updatedAt());
        }

        @Test
        @DisplayName("phaseStatuses null devient Map vide")
        void nullPhaseStatusesDefaultToEmpty() {
            var state = new ExecutionState(execId(), scenarioId(), ExecutionStatus.RUNNING,
                null, emptyContext(), Instant.now(), Instant.now());

            assertEquals(Map.of(), state.phaseStatuses());
        }

        @Test
        @DisplayName("id null leve NullPointerException")
        void nullIdThrows() {
            var now = Instant.now();
            assertThrows(NullPointerException.class, () ->
                new ExecutionState(null, scenarioId(), ExecutionStatus.RUNNING,
                    Map.of(), emptyContext(), now, now)
            );
        }

        @Test
        @DisplayName("scenarioId null leve NullPointerException")
        void nullScenarioIdThrows() {
            var now = Instant.now();
            assertThrows(NullPointerException.class, () ->
                new ExecutionState(execId(), null, ExecutionStatus.RUNNING,
                    Map.of(), emptyContext(), now, now)
            );
        }

        @Test
        @DisplayName("status null leve NullPointerException")
        void nullStatusThrows() {
            var now = Instant.now();
            assertThrows(NullPointerException.class, () ->
                new ExecutionState(execId(), scenarioId(), null,
                    Map.of(), emptyContext(), now, now)
            );
        }

        @Test
        @DisplayName("context null leve NullPointerException")
        void nullContextThrows() {
            var now = Instant.now();
            assertThrows(NullPointerException.class, () ->
                new ExecutionState(execId(), scenarioId(), ExecutionStatus.RUNNING,
                    Map.of(), null, now, now)
            );
        }

        @Test
        @DisplayName("startedAt null leve NullPointerException")
        void nullStartedAtThrows() {
            var now = Instant.now();
            assertThrows(NullPointerException.class, () ->
                new ExecutionState(execId(), scenarioId(), ExecutionStatus.RUNNING,
                    Map.of(), emptyContext(), null, now)
            );
        }

        @Test
        @DisplayName("updatedAt null leve NullPointerException")
        void nullUpdatedAtThrows() {
            var now = Instant.now();
            assertThrows(NullPointerException.class, () ->
                new ExecutionState(execId(), scenarioId(), ExecutionStatus.RUNNING,
                    Map.of(), emptyContext(), now, null)
            );
        }

        @Test
        @DisplayName("modifier la map phaseStatuses source apres construction ne modifie pas le record")
        void phaseStatusesDefensiveCopy() {
            var mutableStatuses = new HashMap<>(Map.of(Phase.PREPARATION, PhaseStatus.PENDING));
            var state = new ExecutionState(execId(), scenarioId(), ExecutionStatus.RUNNING,
                mutableStatuses, emptyContext(), Instant.now(), Instant.now());

            mutableStatuses.put(Phase.INJECTION, PhaseStatus.RUNNING);

            assertEquals(1, state.phaseStatuses().size());
            assertEquals(PhaseStatus.PENDING, state.phaseStatuses().get(Phase.PREPARATION));
        }

        @Test
        @DisplayName("phaseStatuses() retourne une map non-modifiable")
        void phaseStatusesUnmodifiable() {
            var state = new ExecutionState(execId(), scenarioId(), ExecutionStatus.RUNNING,
                Map.of(Phase.PREPARATION, PhaseStatus.PENDING),
                emptyContext(), Instant.now(), Instant.now());

            assertThrows(UnsupportedOperationException.class,
                () -> state.phaseStatuses().put(Phase.INJECTION, PhaseStatus.RUNNING));
        }

        @Test
        @DisplayName("deux etats identiques sont egaux")
        void identicalStatesEqual() {
            var id = execId();
            var now = Instant.now();
            var statuses = Map.of(Phase.INJECTION, PhaseStatus.RUNNING);
            var ctx = emptyContext();

            var s1 = new ExecutionState(id, scenarioId(), ExecutionStatus.RUNNING,
                statuses, ctx, now, now);
            var s2 = new ExecutionState(id, scenarioId(), ExecutionStatus.RUNNING,
                statuses, ctx, now, now);

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }
    }
}
