package com.performance.platform.reporting.model;

import com.performance.platform.domain.report.ReportDefinition;
import com.performance.platform.domain.report.Verdict;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The six sections shared by HTML, PDF and JSON. Missing measurements remain null. */
public record PerformanceAssessment(
        Context context, List<Load> loadModel, List<Check> sliSlo,
        Technical technicalIndicators, Analysis analysis, Validation validation) {
    public PerformanceAssessment {
        loadModel = List.copyOf(loadModel);
        sliSlo = List.copyOf(sliSlo);
    }

    public record Context(Map<String, String> components, String environment, String objective,
                          String testType, Instant startedAt, Instant finishedAt, double durationSeconds) {
        public Context { components = Map.copyOf(components); }
    }
    public record Load(String taskId, Map<String, Object> configuration, Long achievedRequests,
                       Double durationSeconds, String description, List<String> assumptions) {
        public Load {
            configuration = Map.copyOf(configuration);
            assumptions = List.copyOf(assumptions);
        }
    }
    public record Check(String name, String metric, String taskId, Double actual,
                        String operator, Double threshold, String status, boolean critical, String source) {}
    public record Measurement(String name, Double value, String unit, String source, boolean required) {}
    public record Technical(List<Measurement> measurements, List<ReportDefinition.Graph> graphs,
                            List<String> incidents) {
        public Technical {
            measurements = List.copyOf(measurements);
            graphs = List.copyOf(graphs);
            incidents = List.copyOf(incidents);
        }
    }
    public record Analysis(List<String> findings, List<String> recommendations) {
        public Analysis {
            findings = List.copyOf(findings);
            recommendations = List.copyOf(recommendations);
        }
    }
    public record Validation(Verdict status, List<Check> criteria, List<String> limitations) {
        public Validation {
            criteria = List.copyOf(criteria);
            limitations = List.copyOf(limitations);
        }
    }
}
