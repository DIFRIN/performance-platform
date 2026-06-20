package com.performance.platform.app.api.dto;

/**
 * Response returned after a scenario is accepted for execution.
 */
public record SubmitResponse(String executionId, String status) {
}
