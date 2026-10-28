package com.performance.platform.app.api.dto;

/** Exact submitted source; null for executions created without YAML. */
public record ScenarioSourceResponse(String yaml) {
}
