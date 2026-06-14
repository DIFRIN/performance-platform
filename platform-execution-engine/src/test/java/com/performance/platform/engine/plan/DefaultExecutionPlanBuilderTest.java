package com.performance.platform.engine.plan;

import com.performance.platform.application.exception.InvalidScenarioException;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultExecutionPlanBuilder")
class DefaultExecutionPlanBuilderTest {

    private DefaultExecutionPlanBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DefaultExecutionPlanBuilder();
    }

    // --- Helpers ---

    private static StepDefinition step(String id, Phase phase, String... dependsOn) {
        List<TaskId> deps = dependsOn == null || dependsOn.length == 0
                ? List.of()
                : List.of(dependsOn).stream().map(TaskId::of).toList();
        return new StepDefinition(
                TaskId.of(id), id, phase, Map.of(), deps,
                List.of(), null, null
        );
    }

    private static StepDefinition stepWithContext(String id, Phase phase,
                                                   List<String> requiredContexts,
                                                   String... dependsOn) {
        List<TaskId> deps = dependsOn == null || dependsOn.length == 0
                ? List.of()
                : List.of(dependsOn).stream().map(TaskId::of).toList();
        return new StepDefinition(
                TaskId.of(id), id, phase, Map.of(), deps,
                requiredContexts, null, null
        );
    }

    private static ScenarioDefinition scenario(StepDefinition... steps) {
        return new ScenarioDefinition(
                com.performance.platform.domain.id.ScenarioId.of("test-scenario"),
                "Test Scenario", "1.0",
                List.of(), Map.of(),
                ExecutionMode.LOCAL,
                List.of(steps),
                Map.of()
        );
    }

    @Nested
    @DisplayName("Plan construction")
    class PlanConstruction {

        @Test
        @DisplayName("empty scenario produces plan with zero steps")
        void emptyScenario() {
            ScenarioDefinition scenario = scenario();
            ExecutionPlan plan = builder.build(scenario);

            assertNotNull(plan.id());
            assertEquals(scenario.id(), plan.scenarioId());
            assertTrue(plan.preparationSteps().isEmpty());
            assertTrue(plan.injectionSteps().isEmpty());
            assertTrue(plan.assertionSteps().isEmpty());
            assertEquals(0, plan.totalSteps());
            assertNotNull(plan.initialContext());
        }

        @Test
        @DisplayName("steps are separated by phase")
        void stepsSeparatedByPhase() {
            StepDefinition prep = step("prep", Phase.PREPARATION);
            StepDefinition inject = step("inj", Phase.INJECTION);
            StepDefinition assertStep = step("asrt", Phase.ASSERTION);
            ScenarioDefinition scenario = scenario(prep, inject, assertStep);

            ExecutionPlan plan = builder.build(scenario);

            assertEquals(1, plan.preparationSteps().size());
            assertEquals(1, plan.injectionSteps().size());
            assertEquals(1, plan.assertionSteps().size());
            assertEquals("prep", plan.preparationSteps().getFirst().step().taskName());
            assertEquals("inj", plan.injectionSteps().getFirst().step().taskName());
            assertEquals("asrt", plan.assertionSteps().getFirst().step().taskName());
        }

        @Test
        @DisplayName("all steps in single phase → other phases empty")
        void allStepsInOnePhase() {
            StepDefinition s1 = step("a", Phase.PREPARATION);
            StepDefinition s2 = step("b", Phase.PREPARATION);
            ScenarioDefinition scenario = scenario(s1, s2);

            ExecutionPlan plan = builder.build(scenario);

            assertEquals(2, plan.preparationSteps().size());
            assertTrue(plan.injectionSteps().isEmpty());
            assertTrue(plan.assertionSteps().isEmpty());
        }

        @Test
        @DisplayName("plan id is unique per build")
        void uniquePlanIdPerBuild() {
            ScenarioDefinition scenario = scenario(step("a", Phase.PREPARATION));
            ExecutionPlan plan1 = builder.build(scenario);
            ExecutionPlan plan2 = builder.build(scenario);

            assertNotEquals(plan1.id(), plan2.id());
        }
    }

    @Nested
    @DisplayName("DAG level sorting within phases")
    class DagLevelSorting {

        @Test
        @DisplayName("steps sorted by dagLevel ascending within each phase")
        void sortedByDagLevel() {
            StepDefinition a = step("a", Phase.PREPARATION);
            StepDefinition b = step("b", Phase.PREPARATION, "a");  // dagLevel 1
            StepDefinition c = step("c", Phase.PREPARATION, "b");  // dagLevel 2
            ScenarioDefinition scenario = scenario(a, b, c);

            ExecutionPlan plan = builder.build(scenario);

            List<ExecutionStep> steps = plan.preparationSteps();
            assertEquals(3, steps.size());
            assertTrue(steps.get(0).dagLevel() <= steps.get(1).dagLevel());
            assertTrue(steps.get(1).dagLevel() <= steps.get(2).dagLevel());
            assertEquals("a", steps.get(0).step().taskName());
            assertEquals("b", steps.get(1).step().taskName());
            assertEquals("c", steps.get(2).step().taskName());
        }

        @Test
        @DisplayName("independent steps at same dagLevel are together")
        void independentStepsSameLevel() {
            StepDefinition a = step("a", Phase.INJECTION);
            StepDefinition b = step("b", Phase.INJECTION);
            StepDefinition c = step("c", Phase.INJECTION);
            ScenarioDefinition scenario = scenario(a, b, c);

            ExecutionPlan plan = builder.build(scenario);

            List<ExecutionStep> steps = plan.injectionSteps();
            assertEquals(3, steps.size());
            assertEquals(0, steps.get(0).dagLevel());
            assertEquals(0, steps.get(1).dagLevel());
            assertEquals(0, steps.get(2).dagLevel());
        }

        @Test
        @DisplayName("diamond dependency sorted correctly")
        void diamondDependency() {
            StepDefinition a = step("a", Phase.PREPARATION);
            StepDefinition b = step("b", Phase.PREPARATION, "a");
            StepDefinition c = step("c", Phase.PREPARATION, "a");
            StepDefinition d = step("d", Phase.PREPARATION, "b", "c");
            ScenarioDefinition scenario = scenario(a, d, b, c); // unordered input

            ExecutionPlan plan = builder.build(scenario);

            List<ExecutionStep> steps = plan.preparationSteps();
            assertEquals(4, steps.size());
            // a must be first (dagLevel 0)
            assertEquals("a", steps.get(0).step().taskName());
            assertEquals(0, steps.get(0).dagLevel());
            // b and c at level 1
            assertEquals(1, steps.get(1).dagLevel());
            assertEquals(1, steps.get(2).dagLevel());
            // d at level 2
            assertEquals("d", steps.get(3).step().taskName());
            assertEquals(2, steps.get(3).dagLevel());
        }
    }

    @Nested
    @DisplayName("Cross-phase dependencies")
    class CrossPhaseDependencies {

        @Test
        @DisplayName("injection step depends on preparation step")
        void injectionDependsOnPrep() {
            StepDefinition prep = step("prep-db", Phase.PREPARATION);
            StepDefinition inject = step("inject", Phase.INJECTION, "prep-db");
            ScenarioDefinition scenario = scenario(prep, inject);

            ExecutionPlan plan = builder.build(scenario);

            assertEquals(1, plan.preparationSteps().size());
            assertEquals(1, plan.injectionSteps().size());
            // prep gets dagLevel 0, inject gets dagLevel 1
            assertEquals(0, plan.preparationSteps().getFirst().dagLevel());
            assertEquals(1, plan.injectionSteps().getFirst().dagLevel());
        }

        @Test
        @DisplayName("assertion step depends on injection step")
        void assertionDependsOnInjection() {
            StepDefinition inject = step("inject", Phase.INJECTION);
            StepDefinition asrt = step("assert", Phase.ASSERTION, "inject");
            ScenarioDefinition scenario = scenario(inject, asrt);

            ExecutionPlan plan = builder.build(scenario);

            assertEquals(1, plan.injectionSteps().size());
            assertEquals(1, plan.assertionSteps().size());
            assertEquals(0, plan.injectionSteps().getFirst().dagLevel());
            assertEquals(1, plan.assertionSteps().getFirst().dagLevel());
        }

        @Test
        @DisplayName("full chain: prep → inject → assert")
        void fullChain() {
            StepDefinition prep = step("prep", Phase.PREPARATION);
            StepDefinition inject = step("inj", Phase.INJECTION, "prep");
            StepDefinition asrt = step("asrt", Phase.ASSERTION, "inj");
            ScenarioDefinition scenario = scenario(prep, inject, asrt);

            ExecutionPlan plan = builder.build(scenario);

            assertEquals(0, plan.preparationSteps().getFirst().dagLevel());
            assertEquals(1, plan.injectionSteps().getFirst().dagLevel());
            assertEquals(2, plan.assertionSteps().getFirst().dagLevel());
        }
    }

    @Nested
    @DisplayName("ExecutionStep fields")
    class ExecutionStepFields {

        @Test
        @DisplayName("dependencies are preserved from step definition")
        void dependenciesPreserved() {
            StepDefinition inject = step("inj", Phase.INJECTION, "prep-db", "prep-cache");
            ScenarioDefinition scenario = scenario(inject);

            ExecutionPlan plan = builder.build(scenario);

            ExecutionStep step = plan.injectionSteps().getFirst();
            assertEquals(2, step.dependencies().size());
            assertTrue(step.dependencies().contains(TaskId.of("prep-db")));
            assertTrue(step.dependencies().contains(TaskId.of("prep-cache")));
        }

        @Test
        @DisplayName("requiredContextKeys is Set.copyOf(step.requiredContexts())")
        void requiredContextKeys() {
            StepDefinition s = stepWithContext(
                    "task1", Phase.PREPARATION,
                    List.of("db.ready", "cache.warm"), "dep1"
            );
            ScenarioDefinition scenario = scenario(s);

            ExecutionPlan plan = builder.build(scenario);

            ExecutionStep step = plan.preparationSteps().getFirst();
            assertEquals(Set.of("db.ready", "cache.warm"), step.requiredContextKeys());
        }

        @Test
        @DisplayName("requiredContextKeys is empty set when step has no requiredContexts")
        void requiredContextKeysEmptyByDefault() {
            StepDefinition s = step("task1", Phase.PREPARATION);
            ScenarioDefinition scenario = scenario(s);

            ExecutionPlan plan = builder.build(scenario);

            ExecutionStep step = plan.preparationSteps().getFirst();
            assertNotNull(step.requiredContextKeys());
            assertTrue(step.requiredContextKeys().isEmpty());
        }

        @Test
        @DisplayName("step reference is preserved")
        void stepReferencePreserved() {
            StepDefinition s = stepWithContext(
                    "task1", Phase.INJECTION,
                    List.of("token"), "login"
            );
            ScenarioDefinition scenario = scenario(s);

            ExecutionPlan plan = builder.build(scenario);
            ExecutionStep execStep = plan.injectionSteps().getFirst();

            assertSame(s, execStep.step());
        }
    }

    @Nested
    @DisplayName("Initial context")
    class InitialContext {

        @Test
        @DisplayName("initial context has correct executionId and scenarioId")
        void initialContextIds() {
            ScenarioDefinition scenario = scenario(step("a", Phase.PREPARATION));
            ExecutionPlan plan = builder.build(scenario);

            ExecutionContext ctx = plan.initialContext();
            assertEquals(plan.id(), ctx.executionId());
            assertEquals(plan.scenarioId(), ctx.scenarioId());
        }

        @Test
        @DisplayName("initial context store is empty")
        void initialContextStoreEmpty() {
            ScenarioDefinition scenario = scenario(step("a", Phase.PREPARATION));
            ExecutionPlan plan = builder.build(scenario);

            ExecutionContext ctx = plan.initialContext();
            assertTrue(ctx.store().isEmpty());
        }
    }

    @Nested
    @DisplayName("Error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("cycle in dependencies propagates InvalidScenarioException")
        void cyclePropagatesException() {
            StepDefinition a = step("a", Phase.PREPARATION, "b");
            StepDefinition b = step("b", Phase.PREPARATION, "a");
            ScenarioDefinition scenario = scenario(a, b);

            assertThrows(InvalidScenarioException.class, () -> builder.build(scenario));
        }

        @Test
        @DisplayName("self-dependency cycle propagates exception")
        void selfCyclePropagates() {
            StepDefinition a = step("a", Phase.PREPARATION, "a");
            ScenarioDefinition scenario = scenario(a);

            assertThrows(InvalidScenarioException.class, () -> builder.build(scenario));
        }
    }

    @Nested
    @DisplayName("Plan immutability")
    class PlanImmutability {

        @Test
        @DisplayName("step lists are unmodifiable")
        void stepListsUnmodifiable() {
            ScenarioDefinition scenario = scenario(step("a", Phase.PREPARATION));
            ExecutionPlan plan = builder.build(scenario);

            assertThrows(UnsupportedOperationException.class,
                    () -> plan.preparationSteps().add(
                            new com.performance.platform.domain.execution.ExecutionStep(
                                    step("x", Phase.PREPARATION),
                                    List.of(), 0, Set.of()
                            )
                    ));
        }
    }
}
