package com.performance.platform.app.api.dto;

/**
 * Resume d'une execution retourne par GET /api/v1/executions.
 * Inclut l'identifiant, le scenario, le statut, les timestamps et la progression.
 */
public record ExecutionSummaryResponse(
        String executionId,
        String scenarioId,
        String status,
        String startedAt,
        String updatedAt,
        ProgressResponse progress) {
}
