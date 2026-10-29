package com.performance.platform.scenario.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Detecteur de cycle dans un graphe oriente de dependances (DAG).
 * Utilise l'algorithme de Kahn (tri topologique par BFS) pour detecter les cycles
 * et produire l'ordre topologique si le graphe est acyclique.
 */
public final class DagCycleDetector {

    private DagCycleDetector() {
        // classe utilitaire
    }

    /**
     * Resultat de l'analyse topologique d'un graphe de dependances.
     */
    public record DagAnalysisResult(
        boolean hasCycle,
        List<String> topologicalOrder,
        Map<String, Integer> topologicalIndex
    ) {
        public DagAnalysisResult {
            topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(topologicalOrder);
            topologicalIndex = topologicalIndex == null ? Map.of() : Map.copyOf(topologicalIndex);
        }
    }

    /**
     * Analyse un graphe defini par les relations dependsOn.
     * Chaque etape declare les etapes dont elle depend.
     *
     * @param dependsOnMap map : stepId -> [stepIds dont cette etape depend]
     * @return le resultat de l'analyse (cycle ou ordre topologique)
     */
    public static DagAnalysisResult analyze(Map<String, List<String>> dependsOnMap) {
        if (dependsOnMap == null || dependsOnMap.isEmpty()) {
            return new DagAnalysisResult(false, List.of(), Map.of());
        }

        // Collecter tous les noeuds
        Set<String> allNodes = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : dependsOnMap.entrySet()) {
            allNodes.add(entry.getKey());
            if (entry.getValue() != null) {
                allNodes.addAll(entry.getValue());
            }
        }
        List<String> nodes = new ArrayList<>(allNodes);

        // Construire le graphe et calculer les degres entrants
        // dependsOn: step depend de dep -> arete dep -> step (dep doit s'executer avant step)
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();

        for (String node : nodes) {
            inDegree.put(node, 0);
            adjacency.put(node, new ArrayList<>());
        }

        for (Map.Entry<String, List<String>> entry : dependsOnMap.entrySet()) {
            String step = entry.getKey();
            List<String> deps = entry.getValue();
            if (deps == null) continue;

            for (String dep : deps) {
                // Arete dep -> step
                adjacency.get(dep).add(step);
                inDegree.merge(step, 1, Integer::sum);
            }
        }

        // Algorithme de Kahn : BFS depuis les noeuds sans arete entrante
        Queue<String> queue = new ArrayDeque<>();
        for (String node : nodes) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<String> topologicalOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            topologicalOrder.add(node);
            for (String neighbor : adjacency.get(node)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        boolean hasCycle = topologicalOrder.size() != nodes.size();

        Map<String, Integer> topologicalIndex = new LinkedHashMap<>();
        for (int i = 0; i < topologicalOrder.size(); i++) {
            topologicalIndex.put(topologicalOrder.get(i), i);
        }

        return new DagAnalysisResult(hasCycle, topologicalOrder, topologicalIndex);
    }
}
