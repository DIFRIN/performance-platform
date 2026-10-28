package com.performance.platform.reporting.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.ReportDefinition;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.PerformanceAssessment;
import com.performance.platform.reporting.model.PerformanceAssessment.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Evaluates only measured facts; a graph or missing threshold cannot establish compliance. */
final class PerformanceAssessmentBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final List<String> VALIDATION_METRICS = List.of(
            "durationSeconds", "totalRequests", "errorRate", "saturation");

    private PerformanceAssessmentBuilder() {}

    static PerformanceAssessment build(ExecutionState state, List<InjectionReportEntry> injections,
                                       List<AssertionReportEntry> assertions) {
        var details = state.scenarioDetails();
        var spec = details == null ? null : details.report();
        if (spec == null) spec = new ReportDefinition(null, null, null, null, null,
                null, null, null, null, null, null);
        var limitations = new ArrayList<String>();
        if (spec.components().isEmpty()) limitations.add("Application component versions not provided");
        if (blank(spec.environment())) limitations.add("Target environment not provided");
        if (blank(spec.objective())) limitations.add("Test objective not provided");
        if (blank(spec.testType())) limitations.add("Test type not provided");
        if (blank(spec.loadDescription())) limitations.add("Load model description not provided");
        if (spec.assumptions().isEmpty()) limitations.add("Load model assumptions not provided");

        var measurements = new ArrayList<Measurement>();
        for (var observation : spec.observations()) {
            Double actual = observation.value();
            if (actual == null) actual = observationValue(state, observation.taskId(), observation.output());
            String source = observation.value() == null
                    ? observation.taskId() + "." + observation.output() : observation.source();
            measurements.add(new Measurement(observation.name(), actual, observation.unit(), source, observation.required()));
            if (actual == null && observation.required()) {
                limitations.add("Required measurement not collected: " + observation.name());
            }
        }
        for (String name : List.of("CPU", "RAM", "GC", "heap", "disk IO", "DB latency",
                "slow DB queries", "network latency", "network saturation")) {
            if (measurements.stream().noneMatch(m -> m.name().equalsIgnoreCase(name))) {
                measurements.add(new Measurement(name, null, null, null, false));
            }
        }

        var checks = new ArrayList<Check>();
        for (var criterion : spec.criteria()) {
            Double actual = metric(criterion.metric(), criterion.taskId(), state, injections, assertions, measurements);
            checks.add(new Check(criterion.name(), criterion.metric(), criterion.taskId(), actual,
                    criterion.operator(), criterion.threshold(), actual == null ? "UNKNOWN" :
                    compare(actual, criterion.operator(), criterion.threshold()) ? "PASSED" : "FAILED",
                    criterion.critical(), "Scenario criterion"));
        }
        for (var assertion : assertions) {
            var status = assertion.result().status();
            var configured = spec.criteria().stream().filter(c -> "assertion".equals(c.metric())
                    && assertion.assertionId().value().equals(c.taskId())).findFirst();
            boolean critical = status == AssertionStatus.ERROR
                    || configured.map(ReportDefinition.Criterion::critical).orElse(true);
            checks.add(new Check(assertion.result().description(), "assertion", assertion.assertionId().value(),
                    status == AssertionStatus.PASSED ? Double.valueOf(1) :
                            status == AssertionStatus.SKIPPED ? null : Double.valueOf(0),
                    "==", 1.0, status == AssertionStatus.PASSED ? "PASSED" :
                    status == AssertionStatus.SKIPPED ? "UNKNOWN" : "FAILED", critical, "Executed assertion"));
        }
        var validation = new ArrayList<Check>();
        for (String metric : VALIDATION_METRICS) {
            var matching = checks.stream().filter(c -> c.metric().equals(metric)).toList();
            if (matching.isEmpty()) {
                validation.add(new Check(metric, metric, null, null, null, null,
                        "UNKNOWN", true, "No validation criterion configured"));
                limitations.add("Validation criterion missing: " + metric);
            } else {
                validation.addAll(matching);
            }
        }
        if (checks.stream().noneMatch(c -> List.of("p50Ms", "p95Ms", "p99Ms", "throughput").contains(c.metric()))) {
            limitations.add("No latency or throughput SLO configured");
        }
        if (injections.isEmpty()) limitations.add("No injection measurements available");
        else if (injections.stream().anyMatch(i -> i.metrics().totalRequests() == 0)) {
            limitations.add("An injection has no observed requests; latency and throughput are not validated");
        }
        if (state.tasks().stream().anyMatch(task -> state.context().getAll(task.id().value()).isEmpty())) {
            limitations.add("Some planned steps have no execution result");
        }
        boolean failedTask = state.context().store().values().stream().flatMap(m -> m.values().stream())
                .anyMatch(r -> !r.isSuccess() && state.tasks().stream().noneMatch(t ->
                        t.id().equals(r.taskId()) && t.phase() == Phase.ASSERTION
                                && assertions.stream().anyMatch(a -> a.assertionId().equals(t.id()))));
        boolean criticalFailure = checks.stream().anyMatch(c -> c.critical() && c.status().equals("FAILED"));
        boolean warning = !limitations.isEmpty() || checks.stream().anyMatch(c -> !c.status().equals("PASSED"));
        boolean failedWithoutAssertionEvidence = state.status() == ExecutionStatus.FAILED
                && assertions.stream().noneMatch(a -> a.result().status() == AssertionStatus.FAILED
                        || a.result().status() == AssertionStatus.ERROR);
        Verdict verdict = failedTask || criticalFailure || failedWithoutAssertionEvidence ? Verdict.FAILED
                : warning || state.status() != ExecutionStatus.COMPLETED
                ? Verdict.WARNING : Verdict.SUCCESS;
        var findings = new ArrayList<String>(spec.incidents());
        checks.stream().filter(c -> !c.status().equals("PASSED")).forEach(c ->
                findings.add(c.name() + ": " + c.status() + " (observed=" + c.actual()
                        + ", expected " + c.operator() + " " + c.threshold() + ")"));
        state.context().store().forEach((task, agents) -> agents.forEach((agent, result) -> {
            if (!result.isSuccess()) findings.add(task + " / " + agent + ": " + result.status()
                    + (result.errorMessage() == null ? "" : " - " + result.errorMessage()));
        }));
        var recommendations = new ArrayList<>(spec.recommendations());
        if (!limitations.isEmpty()) recommendations.add("Complete missing context, measurements and validation criteria before approval.");
        if (criticalFailure || failedTask) recommendations.add("Investigate failed checks and execution errors before repeating the test.");
        if (findings.isEmpty()) findings.add("No anomaly detected in collected evidence; this is not proof of unmeasured resource health.");
        findings.add("Contention diagnosis requires correlated measurements; no bottleneck is inferred from latency alone.");

        var loads = new ArrayList<Load>();
        for (var task : state.tasks()) {
            if (task.phase() != Phase.INJECTION) continue;
            var matching = injections.stream().filter(i -> i.taskId().equals(task.id())).toList();
            loads.add(new Load(task.id().value(), task.configuration(),
                    matching.isEmpty() ? null : Long.valueOf(matching.stream().mapToLong(i -> i.metrics().totalRequests()).sum()),
                    matching.isEmpty() ? null : Double.valueOf(matching.stream().mapToDouble(i -> seconds(i.metrics().duration())).max().orElseThrow()),
                    spec.loadDescription(), spec.assumptions()));
        }
        if (loads.isEmpty()) for (var injection : injections) {
            loads.add(new Load(injection.taskId().value(), Map.of(), injection.metrics().totalRequests(),
                    seconds(injection.metrics().duration()), spec.loadDescription(), spec.assumptions()));
        }
        var injectionTasks = state.context().store().values().stream().flatMap(m -> m.values().stream())
                .filter(r -> r.outputs().get(InjectionResult.OUTPUT_KEY) instanceof InjectionResult).toList();
        var start = injectionTasks.stream().map(r -> r.completedAt().minus(r.duration()))
                .min(java.util.Comparator.naturalOrder()).orElse(state.startedAt());
        var end = injectionTasks.stream().map(r -> r.completedAt())
                .max(java.util.Comparator.naturalOrder()).orElse(state.updatedAt());
        return new PerformanceAssessment(new Context(spec.components(), spec.environment(), spec.objective(),
                spec.testType(), start, end, seconds(Duration.between(start, end))),
                List.copyOf(loads), List.copyOf(checks),
                new Technical(List.copyOf(measurements), spec.graphs(), spec.incidents()),
                new Analysis(List.copyOf(findings), List.copyOf(recommendations)),
                new Validation(verdict, List.copyOf(validation), List.copyOf(limitations)));
    }

    private static Double metric(String name, String taskId, ExecutionState state,
                                 List<InjectionReportEntry> injections, List<AssertionReportEntry> assertions,
                                 List<Measurement> observations) {
        var results = injections.stream().filter(i -> taskId == null || i.taskId().value().equals(taskId))
                .map(InjectionReportEntry::metrics).toList();
        if ("assertion".equals(name)) {
            var matches = assertions.stream().filter(a -> a.assertionId().value().equals(taskId)).toList();
            if (matches.isEmpty() || matches.stream().anyMatch(a -> a.result().status() == AssertionStatus.SKIPPED)) return null;
            return matches.stream().allMatch(a -> a.result().status() == AssertionStatus.PASSED) ? 1.0 : 0.0;
        }
        for (var observation : observations) if (observation.name().equals(name)) return observation.value();
        if (results.isEmpty()) return null;
        if (List.of("p50Ms", "p95Ms", "p99Ms", "throughput").contains(name)
                && results.stream().anyMatch(r -> r.totalRequests() == 0)) return null;
        return switch (name) {
            case "totalRequests" -> (double) results.stream().mapToLong(InjectionResult::totalRequests).sum();
            case "durationSeconds" -> {
                var matching = state.context().store().values().stream().flatMap(m -> m.values().stream())
                        .filter(r -> (taskId == null || r.taskId().value().equals(taskId))
                                && r.outputs().get(InjectionResult.OUTPUT_KEY) instanceof InjectionResult).toList();
                var start = matching.stream().map(r -> r.completedAt().minus(r.duration()))
                        .min(java.util.Comparator.naturalOrder()).orElseThrow();
                var end = matching.stream().map(r -> r.completedAt())
                        .max(java.util.Comparator.naturalOrder()).orElseThrow();
                yield seconds(Duration.between(start, end));
            }
            case "errorRate" -> {
                long total = results.stream().mapToLong(InjectionResult::totalRequests).sum();
                yield total == 0 ? null : InjectionResult.computeErrorRate(total,
                        results.stream().mapToLong(InjectionResult::failedRequests).sum());
            }
            // Percentiles cannot be averaged; use the worst agent value for validation.
            case "p50Ms" -> (double) results.stream().mapToLong(InjectionResult::p50Ms).max().orElseThrow();
            case "p95Ms" -> (double) results.stream().mapToLong(InjectionResult::p95Ms).max().orElseThrow();
            case "p99Ms" -> (double) results.stream().mapToLong(InjectionResult::p99Ms).max().orElseThrow();
            case "throughput" -> results.stream().mapToDouble(InjectionResult::throughput).min().orElseThrow();
            default -> null;
        };
    }

    private static Double observationValue(ExecutionState state, String taskId, String path) {
        var values = new ArrayList<Double>();
        for (var result : state.context().getAll(taskId).values()) {
            Object value = result.outputs();
            for (String segment : path.split("\\.")) {
                if (!(value instanceof Map<?, ?>)) value = MAPPER.convertValue(value, Map.class);
                value = value instanceof Map<?, ?> map ? map.get(segment) : null;
                if (value == null) break;
            }
            if (value instanceof Number number) {
                if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Non-finite observation: " + path);
                values.add(number.doubleValue());
            } else if (value != null) {
                throw new IllegalArgumentException("Observation must be numeric: " + taskId + "." + path);
            }
        }
        return values.stream().max(Double::compare).orElse(null);
    }

    private static boolean compare(double actual, String operator, double threshold) {
        return switch (operator) {
            case "<" -> actual < threshold;
            case "<=" -> actual <= threshold;
            case ">" -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "==" -> actual == threshold;
            case "!=" -> actual != threshold;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static double seconds(Duration duration) { return duration.toMillis() / 1000.0; }
}
