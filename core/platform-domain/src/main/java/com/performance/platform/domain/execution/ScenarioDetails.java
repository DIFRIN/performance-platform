package com.performance.platform.domain.execution;

import com.performance.platform.domain.report.ReportDefinition;

import java.util.List;
import java.util.Map;

/** Immutable scenario provenance retained independently of its executable plan. */
public record ScenarioDetails(String name, String version, List<String> tags,
                              Map<String, String> metadata, ReportDefinition report) {
    public ScenarioDetails {
        tags = tags == null ? List.of() : List.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
