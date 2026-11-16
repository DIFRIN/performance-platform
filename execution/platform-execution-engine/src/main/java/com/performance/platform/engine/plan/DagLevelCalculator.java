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
import java.util.Set;

/**
 * Calcule le niveau DAG (dagLevel) de chaque etape par tri topologique
 * via l'algorithme de Kahn (BFS sur les degres entrants).
 *
 * <p>Regles de calcul :
 * <ul>
 *   <li>dagLevel = 0 si aucune dependance</li>
 *   <li>dagLevel = max(niveau des dependances) + 1 sinon</li>
 *   <li>Cycle detecte -> {@link InvalidScenarioException}</li>
 * </ul>
 *
 * <p>Seules les aretes intra-phase entre action steps ordonnent le DAG (ADR-053) :
 * une reference a une autre phase ou a une service step est ignoree.</p>
 */
public final class DagLevelCalculator {

    private DagLevelCalculator() {
        // classe utilitaire
    }

    /**
     * Calcule le dagLevel pour toutes les etapes du scenario.
     *
     * @param steps les etapes du scenario (toutes phases confondues)
     * @return map immuable {@code TaskId -> dagLevel} pour chaque etape
     * @throws InvalidScenarioException si un cycle est detecte dans le graphe de dependances
     */
    public static Map<TaskId, Integer> compute(List<StepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            return Map.of();
        }

        Map<TaskId, StepDefinition> byId = new LinkedHashMap<>();
        for (StepDefinition step : steps) {
            if (step.id() != null) {
                byId.put(step.id(), step);
            }
        }

        // Etape 1 : collecter tous les nœuds
        var allNodes = new LinkedHashSet<TaskId>();
        for (StepDefinition step : steps) {
            allNodes.add(step.id());
        }

        // Etape 2 : construire le graphe des aretes intra-phase
        var inDegree = new LinkedHashMap<TaskId, Integer>();
        var adjacency = new LinkedHashMap<TaskId, List<TaskId>>();

        for (TaskId node : allNodes) {
            inDegree.put(node, 0);
            adjacency.put(node, new ArrayList<>());
        }

        for (StepDefinition step : steps) {
            List<TaskId> deps = step.dependsOn();
            if (deps == null || step.hasLifetime()) continue;
            for (TaskId dep : deps) {
                StepDefinition depStep = byId.get(dep);
                if (dep.equals(step.id())) {
                    throw new InvalidScenarioException(
                            "Self-dependency detected on step: " + step.id().value());
                }
                if (depStep == null || depStep.hasLifetime()) continue;
                if (depStep.phase() != step.phase()) continue;
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
                dagLevels.merge(neighbor, currentLevel + 1, Math::max);
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (processed != allNodes.size()) {
            throw new InvalidScenarioException(
                "Cycle detected in step dependencies: " + processed
                + " nodes processed out of " + allNodes.size() + " total"
            );
        }

        return Map.copyOf(dagLevels);
    }
}
