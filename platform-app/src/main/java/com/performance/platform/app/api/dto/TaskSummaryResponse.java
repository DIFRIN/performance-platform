package com.performance.platform.app.api.dto;

/**
 * Resume d'une tache dans une execution.
 * {@code errorMessage} est present uniquement si le statut est KO (FAILED, TIMEOUT, SKIPPED).
 * {@code phase} peut etre null si l'information de phase n'est pas disponible dans les resultats persistes.
 */
public record TaskSummaryResponse(
        String taskId,
        String taskName,
        String phase,
        String status,
        String errorMessage) {
}
