package com.performance.platform.domain.execution;

/**
 * Progression d'une execution : nombre de taches planifiees, reussies, echouees, en cours.
 * Record immuable — 0 annotation framework.
 *
 * <ul>
 *   <li>{@code total}   — nombre total de taches planifiees</li>
 *   <li>{@code ok}      — taches terminées avec statut SUCCESS</li>
 *   <li>{@code ko}      — taches terminées avec statut terminal non-success (FAILED, TIMEOUT, SKIPPED)</li>
 *   <li>{@code running} — taches planifiees sans resultat terminal</li>
 * </ul>
 */
public record ExecutionProgress(int total, int ok, int ko, int running) {

    public ExecutionProgress {
        if (total < 0 || ok < 0 || ko < 0 || running < 0) {
            throw new IllegalArgumentException("progress counters must be >= 0");
        }
    }
}
