package com.performance.platform.app.api.dto;

import java.util.Map;

/**
 * Resume d'une tache dans une execution.
 * {@code errorMessage} est present uniquement si le statut est KO (FAILED, TIMEOUT, SKIPPED).
 * {@code phase} peut etre null si l'information de phase n'est pas disponible dans les resultats persistes.
 * Les etapes planifiees sans resultat portent le statut PENDING ou RUNNING.
 */
public record TaskSummaryResponse(
        String taskId,
        String taskName,
        String phase,
        String status,
        String errorMessage,
        String agentId,
        Long durationMillis,
        String completedAt,
        Map<String, Object> configuration,
        Map<String, Object> outputs) {
    public TaskSummaryResponse(String taskId, String taskName, String phase, String status, String errorMessage) {
        this(taskId, taskName, phase, status, errorMessage, null, null, null, Map.of(), Map.of());
    }

    public TaskSummaryResponse {
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
