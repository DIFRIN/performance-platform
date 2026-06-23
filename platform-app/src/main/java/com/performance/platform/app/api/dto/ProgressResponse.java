package com.performance.platform.app.api.dto;

/**
 * Progression d'une execution : nombre de taches planifiees, reussies, echouees, en cours.
 * Calculee serveur via {@code ExecutionProgressCalculator} (ISSUE-120).
 */
public record ProgressResponse(int total, int ok, int ko, int running) {
}
