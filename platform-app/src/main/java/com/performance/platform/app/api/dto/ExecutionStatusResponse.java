package com.performance.platform.app.api.dto;

import java.util.Map;

/**
 * Full status of an execution returned by the GET /executions/{id} endpoint.
 * Includes the global status, per-phase statuses, and timestamps.
 */
public record ExecutionStatusResponse(
        String executionId,
        String scenarioId,
        String status,
        Map<String, String> phaseStatuses,
        String startedAt,
        String updatedAt) {

    public ExecutionStatusResponse {
        phaseStatuses = phaseStatuses == null ? Map.of() : Map.copyOf(phaseStatuses);
    }
}
