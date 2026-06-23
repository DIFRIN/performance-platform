package com.performance.platform.app.api.dto;

import java.util.List;
import java.util.Objects;

/**
 * Reponse de l'endpoint GET /api/v1/executions/{id}/tasks.
 * Contient le total de taches et leurs resumes.
 */
public record TaskListResponse(
        String executionId,
        int total,
        List<TaskSummaryResponse> tasks) {

    public TaskListResponse {
        Objects.requireNonNull(executionId, "executionId required");
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
