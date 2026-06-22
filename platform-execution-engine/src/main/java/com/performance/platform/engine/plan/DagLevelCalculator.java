package com.performance.platform.engine.plan;

import com.performance.platform.application.exception.InvalidScenarioException;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Calcule le niveau DAG (dagLevel) de chaque etape par tri topologique
 * via l'algorithme de Kahn (BFS sur les degres entrants).
 *
 * <p>Regles de calcul :
 * <ul>
 *   <li>dagLevel = 0 si aucune dependance</li>
 *   <li>dagLevel = max(niveau des dependances) + 1 sinon</li>
 *   <li>Cycle detecte → {@link InvalidScenarioException}</li>
 * </ul>
 *
 * <p>Le calcul est global (toutes phases confondues) pour garantir la coherence
 * des niveaux en presence de dependances inter-phases. Les etapes sont ensuite
 * reparties par phase dans {@link DefaultExecutionPlanBuilder}.</p>
 */
public final class DagLevelCalculator {

    private DagLevelCalculator() {
        // classe utilitaire
    }

    /**
     * Calcule le dagLevel pour toutes les etapes du scenario.
     *
     * @param steps les etapes du scenario (toutes phases confondues)
     * @return map immuable {@code TaskId → dagLevel} pour chaque etape
     * @throws InvalidScenarioException si un cycle est detecte dans le graphe de dependances
     */
    public static Map<TaskId, Integer> compute(List<StepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            return Map.of();
        }

        // Etape 1 : collecter tous les nœuds (steps + dependances referencees)
        var allNodes = new LinkedHashSet<TaskId>();
        for (StepDefinition step : steps) {
            allNodes.add(step.id());
            List<TaskId> deps = step.dependsOn();
            if (deps != null) {
                allNodes.addAll(deps);
            }
        }

        // Etape 2 : construire le graphe et calculer les degres entrants
        // Arete : dep → step (la dependance doit s'executer avant le step)
        var inDegree = new LinkedHashMap<TaskId, Integer>();
        var adjacency = new LinkedHashMap<TaskId, List<TaskId>>();

        for (TaskId node : allNodes) {
            inDegree.put(node, 0);
            adjacency.put(node, new ArrayList<>());
        }

        for (StepDefinition step : steps) {
            List<TaskId> deps = step.dependsOn();
            if (deps == null) continue;
            for (TaskId dep : deps) {
                adjacency.get(dep).add(step.id());
                inDegree.merge(step.id(), 1, Integer::sum);
            }
        }

        // Etape 3 : BFS (Kahn) depuis les nœuds sans arete entrante
        var dagLevels = new LinkedHashMap<TaskId, Integer>();
        var queue = new ArrayDeque<TaskId>();

        for (TaskId node : allNodes) {
            if (inDegree.get(node) == 0) {
                dagLevels.put(node, 0);
                queue.add(node);
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            TaskId node = queue.poll();
            int currentLevel = dagLevels.get(node);
            processed++;

            for (TaskId neighbor : adjacency.get(node)) {
                // neighbor herite du niveau de node + 1, ou plus si deja superieur
                dagLevels.merge(neighbor, currentLevel + 1, Math::max);
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // Etape 4 : verification d'absence de cycle
        if (processed != allNodes.size()) {
            throw new InvalidScenarioException(
                "Cycle detected in step dependencies: " + processed
                + " nodes processed out of " + allNodes.size() + " total"
            );
        }

        return Map.copyOf(dagLevels);
    }
}
