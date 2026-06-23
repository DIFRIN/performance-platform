package com.performance.platform.application.usecase;

import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import java.util.Map;
import java.util.Objects;

/**
 * Calcule la progression d'une execution a partir de son etat et des resultats de taches connus.
 * <p>
 * Regles (ADR-020) :
 * <ul>
 *   <li>{@code total}   — nombre de taches uniques dans la map {@code taskResults}</li>
 *   <li>{@code ok}      — taches ayant au moins un resultat {@link TaskStatus#SUCCESS}</li>
 *   <li>{@code ko}      — taches sans aucun SUCCESS et ayant au moins un resultat terminal
 *                         (FAILED, TIMEOUT ou SKIPPED)</li>
 *   <li>{@code running} — taches sans aucun resultat encore (map interne vide)</li>
 * </ul>
 * Les taches en {@code running} ne contribuent ni a {@code ok} ni a {@code ko}.
 */
public class ExecutionProgressCalculator {

    /**
     * Derive la progression a partir des resultats de taches connus.
     *
     * @param state       etat courant de l'execution (jamais null)
     * @param taskResults map de tous les taskIds planifies vers leurs resultats par agent
     *                    (map interne vide = tache en cours / sans resultat)
     * @return progression calculee, jamais null
     */
    public ExecutionProgress calculate(
            ExecutionState state,
            Map<TaskId, Map<AgentId, TaskResult>> taskResults) {

        Objects.requireNonNull(state, "state required");
        Objects.requireNonNull(taskResults, "taskResults required");

        int total = taskResults.size();
        int ok = 0;
        int ko = 0;
        int running = 0;

        for (Map<AgentId, TaskResult> agentResults : taskResults.values()) {
            if (agentResults == null || agentResults.isEmpty()) {
                running++;
                continue;
            }

            boolean hasSuccess = agentResults.values().stream()
                    .anyMatch(r -> r.status() == TaskStatus.SUCCESS);

            if (hasSuccess) {
                ok++;
            } else {
                // Tous les resultats sont terminaux non-success (FAILED, TIMEOUT, SKIPPED)
                ko++;
            }
        }

        return new ExecutionProgress(total, ok, ko, running);
    }
}
