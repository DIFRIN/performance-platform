package com.performance.platform.scenario.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DagCycleDetector")
class DagCycleDetectorTest {

    @Nested
    @DisplayName("Acyclic graphs")
    class Acyclic {

        @Test
        @DisplayName("empty graph has no cycles")
        void emptyGraph() {
            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(Map.of());
            assertFalse(result.hasCycle());
            assertTrue(result.topologicalOrder().isEmpty());
        }

        @Test
        @DisplayName("null graph has no cycles")
        void nullGraph() {
            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(null);
            assertFalse(result.hasCycle());
        }

        @Test
        @DisplayName("single node with no dependencies")
        void singleNode() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertFalse(result.hasCycle());
            assertEquals(List.of("A"), result.topologicalOrder());
            assertEquals(0, result.topologicalIndex().get("A"));
        }

        @Test
        @DisplayName("linear chain A -> B -> C")
        void linearChain() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());
            graph.put("B", List.of("A"));
            graph.put("C", List.of("B"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertFalse(result.hasCycle());
            assertEquals(3, result.topologicalOrder().size());
            // A before B, B before C
            assertTrue(result.topologicalIndex().get("A") < result.topologicalIndex().get("B"));
            assertTrue(result.topologicalIndex().get("B") < result.topologicalIndex().get("C"));
        }

        @Test
        @DisplayName("diamond pattern A -> B, A -> C, B -> D, C -> D")
        void diamondPattern() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());
            graph.put("B", List.of("A"));
            graph.put("C", List.of("A"));
            graph.put("D", List.of("B", "C"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertFalse(result.hasCycle());
            assertEquals(4, result.topologicalOrder().size());
            // A must be first, D must be last
            assertEquals(0, result.topologicalIndex().get("A"));
            assertEquals(3, result.topologicalIndex().get("D"));
            // B and C come after A and before D
            assertTrue(result.topologicalIndex().get("B") < result.topologicalIndex().get("D"));
            assertTrue(result.topologicalIndex().get("C") < result.topologicalIndex().get("D"));
        }

        @Test
        @DisplayName("independent parallel nodes")
        void parallelNodes() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());
            graph.put("B", List.of());
            graph.put("C", List.of());

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertFalse(result.hasCycle());
            assertEquals(3, result.topologicalOrder().size());
            // All nodes are present
            assertTrue(result.topologicalIndex().containsKey("A"));
            assertTrue(result.topologicalIndex().containsKey("B"));
            assertTrue(result.topologicalIndex().containsKey("C"));
        }

        @Test
        @DisplayName("two disconnected chains")
        void twoChains() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());
            graph.put("B", List.of("A"));
            graph.put("X", List.of());
            graph.put("Y", List.of("X"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertFalse(result.hasCycle());
            assertEquals(4, result.topologicalOrder().size());
            assertTrue(result.topologicalIndex().get("A") < result.topologicalIndex().get("B"));
            assertTrue(result.topologicalIndex().get("X") < result.topologicalIndex().get("Y"));
        }
    }

    @Nested
    @DisplayName("Cyclic graphs")
    class Cyclic {

        @Test
        @DisplayName("simple 2-node cycle A <-> B")
        void simpleTwoNodeCycle() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("B"));
            graph.put("B", List.of("A"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertTrue(result.hasCycle());
        }

        @Test
        @DisplayName("self-loop A -> A")
        void selfLoop() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("A"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertTrue(result.hasCycle());
        }

        @Test
        @DisplayName("3-node cycle A -> B -> C -> A")
        void threeNodeCycle() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("C"));
            graph.put("B", List.of("A"));
            graph.put("C", List.of("B"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertTrue(result.hasCycle());
        }

        @Test
        @DisplayName("cycle within larger acyclic structure")
        void cycleInStructure() {
            // A -> B -> C, and C -> D -> B (cycle B-C-D)
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());
            graph.put("B", List.of("A"));
            graph.put("C", List.of("B", "D"));
            graph.put("D", List.of("C"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertTrue(result.hasCycle());
        }

        @Test
        @DisplayName("topological order for cyclic graph has fewer entries than nodes")
        void topologicalOrderIncompleteForCycle() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("B"));
            graph.put("B", List.of("A"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertTrue(result.hasCycle());
            // With a cycle, topological order should be incomplete
            assertTrue(result.topologicalOrder().size() < 2);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("node appearing only as dependency (not as key)")
        void nodeOnlyAsDependency() {
            // B depends on A, but A is not a key (only mentioned in B's deps)
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("B", List.of("A"));

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertFalse(result.hasCycle());
            assertEquals(2, result.topologicalOrder().size());
            // A comes before B
            assertTrue(result.topologicalIndex().get("A") < result.topologicalIndex().get("B"));
        }

        @Test
        @DisplayName("result immutability: topologicalOrder")
        void topologicalOrderImmutability() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertThrows(UnsupportedOperationException.class, () -> result.topologicalOrder().add("X"));
        }

        @Test
        @DisplayName("result immutability: topologicalIndex")
        void topologicalIndexImmutability() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of());

            DagCycleDetector.DagAnalysisResult result = DagCycleDetector.analyze(graph);
            assertThrows(UnsupportedOperationException.class, () -> result.topologicalIndex().put("X", 0));
        }
    }
}
