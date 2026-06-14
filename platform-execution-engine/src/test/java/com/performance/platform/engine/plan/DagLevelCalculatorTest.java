package com.performance.platform.engine.plan;

import com.performance.platform.application.exception.InvalidScenarioException;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DagLevelCalculator")
class DagLevelCalculatorTest {

    // --- Helpers ---

    private static StepDefinition step(String id, String... dependsOn) {
        List<TaskId> deps = dependsOn == null || dependsOn.length == 0
                ? List.of()
                : List.of(dependsOn).stream().map(TaskId::of).toList();
        return new StepDefinition(
                TaskId.of(id), id, Phase.PREPARATION, Map.of(), deps,
                List.of(), null, null
        );
    }

    @Nested
    @DisplayName("Empty and trivial inputs")
    class EmptyAndTrivial {

        @Test
        @DisplayName("null steps returns empty map")
        void nullSteps() {
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(null);
            assertNotNull(levels);
            assertTrue(levels.isEmpty());
        }

        @Test
        @DisplayName("empty steps returns empty map")
        void emptySteps() {
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(List.of());
            assertNotNull(levels);
            assertTrue(levels.isEmpty());
        }
    }

    @Nested
    @DisplayName("DAG level computation")
    class LevelComputation {

        @Test
        @DisplayName("single step with no dependencies → dagLevel 0")
        void singleStepNoDeps() {
            List<StepDefinition> steps = List.of(step("A"));
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(1, levels.size());
            assertEquals(0, levels.get(TaskId.of("A")));
        }

        @Test
        @DisplayName("multiple independent steps → all dagLevel 0")
        void multipleIndependentSteps() {
            List<StepDefinition> steps = List.of(step("A"), step("B"), step("C"));
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(3, levels.size());
            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(0, levels.get(TaskId.of("B")));
            assertEquals(0, levels.get(TaskId.of("C")));
        }

        @Test
        @DisplayName("linear chain A → B → C → A=0, B=1, C=2")
        void linearChain() {
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B", "A"),
                    step("C", "B")
            );
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(3, levels.size());
            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(1, levels.get(TaskId.of("B")));
            assertEquals(2, levels.get(TaskId.of("C")));
        }

        @Test
        @DisplayName("diamond: A→B, A→C, B→D, C→D → A=0, B=C=1, D=2")
        void diamondPattern() {
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B", "A"),
                    step("C", "A"),
                    step("D", "B", "C")
            );
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(4, levels.size());
            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(1, levels.get(TaskId.of("B")));
            assertEquals(1, levels.get(TaskId.of("C")));
            assertEquals(2, levels.get(TaskId.of("D")));
        }

        @Test
        @DisplayName("fan-out: A → B, A → C, A → D")
        void fanOut() {
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B", "A"),
                    step("C", "A"),
                    step("D", "A")
            );
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(1, levels.get(TaskId.of("B")));
            assertEquals(1, levels.get(TaskId.of("C")));
            assertEquals(1, levels.get(TaskId.of("D")));
        }

        @Test
        @DisplayName("fan-in: A→C, B→C → A=B=0, C=1")
        void fanIn() {
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B"),
                    step("C", "A", "B")
            );
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(0, levels.get(TaskId.of("B")));
            assertEquals(1, levels.get(TaskId.of("C")));
        }

        @Test
        @DisplayName("two disconnected chains: A→B and X→Y")
        void twoDisconnectedChains() {
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B", "A"),
                    step("X"),
                    step("Y", "X")
            );
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(1, levels.get(TaskId.of("B")));
            assertEquals(0, levels.get(TaskId.of("X")));
            assertEquals(1, levels.get(TaskId.of("Y")));
        }

        @Test
        @DisplayName("deep chain: A→B→C→D→E → levels 0 to 4")
        void deepChain() {
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B", "A"),
                    step("C", "B"),
                    step("D", "C"),
                    step("E", "D")
            );
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(0, levels.get(TaskId.of("A")));
            assertEquals(1, levels.get(TaskId.of("B")));
            assertEquals(2, levels.get(TaskId.of("C")));
            assertEquals(3, levels.get(TaskId.of("D")));
            assertEquals(4, levels.get(TaskId.of("E")));
        }

        @Test
        @DisplayName("cross-phase dependency: injection step depends on preparation step")
        void crossPhaseDependency() {
            StepDefinition prep = new StepDefinition(
                    TaskId.of("prep-db"), "prep-db", Phase.PREPARATION,
                    Map.of(), List.of(), List.of(), null, null
            );
            StepDefinition inject = new StepDefinition(
                    TaskId.of("inject"), "inject", Phase.INJECTION,
                    Map.of(), List.of(TaskId.of("prep-db")), List.of(), null, null
            );
            List<StepDefinition> steps = List.of(prep, inject);
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertEquals(0, levels.get(TaskId.of("prep-db")));
            assertEquals(1, levels.get(TaskId.of("inject")));
        }
    }

    @Nested
    @DisplayName("Cycle detection")
    class CycleDetection {

        @Test
        @DisplayName("simple 2-node cycle A ↔ B")
        void simpleTwoNodeCycle() {
            List<StepDefinition> steps = List.of(
                    step("A", "B"),
                    step("B", "A")
            );
            assertThrows(InvalidScenarioException.class,
                    () -> DagLevelCalculator.compute(steps));
        }

        @Test
        @DisplayName("self-loop A → A")
        void selfLoop() {
            List<StepDefinition> steps = List.of(step("A", "A"));
            assertThrows(InvalidScenarioException.class,
                    () -> DagLevelCalculator.compute(steps));
        }

        @Test
        @DisplayName("3-node cycle A → B → C → A")
        void threeNodeCycle() {
            List<StepDefinition> steps = List.of(
                    step("A", "C"),
                    step("B", "A"),
                    step("C", "B")
            );
            assertThrows(InvalidScenarioException.class,
                    () -> DagLevelCalculator.compute(steps));
        }

        @Test
        @DisplayName("cycle within larger acyclic structure")
        void cycleInLargerStructure() {
            // A → B → C, and C → D → B (cycle B-C-D)
            List<StepDefinition> steps = List.of(
                    step("A"),
                    step("B", "A"),
                    step("C", "B", "D"),
                    step("D", "C")
            );
            assertThrows(InvalidScenarioException.class,
                    () -> DagLevelCalculator.compute(steps));
        }

        @Test
        @DisplayName("exception message contains useful info")
        void exceptionMessageIsDescriptive() {
            List<StepDefinition> steps = List.of(
                    step("A", "B"),
                    step("B", "A")
            );
            InvalidScenarioException ex = assertThrows(InvalidScenarioException.class,
                    () -> DagLevelCalculator.compute(steps));
            assertTrue(ex.getMessage().contains("Cycle"));
        }
    }

    @Nested
    @DisplayName("Result immutability")
    class Immutability {

        @Test
        @DisplayName("returned map is unmodifiable")
        void mapIsUnmodifiable() {
            List<StepDefinition> steps = List.of(step("A"));
            Map<TaskId, Integer> levels = DagLevelCalculator.compute(steps);

            assertThrows(UnsupportedOperationException.class,
                    () -> levels.put(TaskId.of("B"), 0));
        }
    }
}
