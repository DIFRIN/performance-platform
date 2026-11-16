package com.performance.platform.reporting.model;

import com.performance.platform.domain.report.ReportDefinition;
import com.performance.platform.domain.report.Verdict;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The six sections shared by HTML, PDF and JSON. Missing measurements remain null. */
public record PerformanceAssessment(
        Context context, List<Load> loadModel, List<Check> sliSlo,
        Technical technicalIndicators, Analysis analysis, Validation validation,
        List<SloSection> sloStatements, Review review) {
    public PerformanceAssessment {
        loadModel = List.copyOf(loadModel);
        sliSlo = List.copyOf(sliSlo);
        sloStatements = sloStatements == null ? List.of() : List.copyOf(sloStatements);
        review = review == null ? Review.none() : review;
    }

    /** Bloc SLI/SLO textuel d'une assertion, avec ses checks. */
    public record SloSection(String assertionId, String title, String sli, String slo,
                             String rationale, List<Check> checks) {
        public SloSection {
            assertionId = assertionId == null ? "" : assertionId;
            title = title == null ? "" : title;
            sli = sli == null ? "" : sli;
            slo = slo == null ? "" : slo;
            rationale = rationale == null ? "" : rationale;
            checks = checks == null ? List.of() : List.copyOf(checks);
        }

        public boolean described() {
            return !(title.isBlank() && sli.isBlank() && slo.isBlank());
        }
    }

    /** Revue humaine, append-only et attribuee. */
    public record Review(ReviewState state, String author, java.time.Instant decidedAt,
                         String rationale, List<String> evidenceRefs) {
        public Review {
            state = state == null ? ReviewState.NONE : state;
            author = author == null ? "" : author;
            rationale = rationale == null ? "" : rationale;
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }

        public static Review none() {
            return new Review(ReviewState.NONE, "", null, "", List.of());
        }

        public static Review required(String reason) {
            return new Review(ReviewState.REQUIRED, "", null, reason, List.of());
        }
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
