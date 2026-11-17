package com.performance.platform.assertion.performance;

import com.performance.platform.assertion.AssertionSummaryFactory;
import com.performance.platform.assertion.AbstractCheckAssertionExecutor;
import com.performance.platform.assertion.CheckEvaluator;
import com.performance.platform.assertion.InjectionAggregator;
import com.performance.platform.assertion.OperatorResolver;
import com.performance.platform.assertion.ParameterBinder;
import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.AssertionSummaries;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricCatalog;
import com.performance.platform.domain.assertion.MetricDefinition;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.QueryFingerprint;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.SloStatement;
import com.performance.platform.domain.assertion.TimeWindow;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.Assertion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assertion de performance normalisee.
 * <p>
 * Un seul bloc de declaration produit plusieurs checks :
 * <pre>
 * latency:   { p50, p95, p99, unit, severity }
 * errorRate: { max, unit, severity }
 * throughput:{ min, unit, severity }
 * checks:    [ {metric, operator, threshold, tolerance, severity, intent} ]
 * </pre>
 * Le resume contient toujours p50/p95/p99 (les non contraints sortent en
 * {@code SKIPPED}) afin que le rapport expose la structure complete.
 */
@Component
@Assertion(name = "performance-thresholds",
        description = "p50/p95/p99, error rate and throughput thresholds")
public class PerformanceThresholdsAssertionExecutor extends AbstractCheckAssertionExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(PerformanceThresholdsAssertionExecutor.class);
    private static final String TASK_NAME = "performance-thresholds";

    private static final List<String> LATENCY_METRICS = List.of(
            "injection.latency.p50", "injection.latency.p95", "injection.latency.p99");

    @Override
    public String getSupportedAssertionName() {
        return TASK_NAME;
    }

    @Override
    protected AssertionSummary evaluateChecks(ExecutionContext context,
                                                             StepDefinition step) {
        Instant start = Instant.now();
        SloStatement slo = AssertionSummaries.sloStatement(step);
        ParameterBinder params = ParameterBinder.of(referenceParameters(step));
        List<Check> checks = new ArrayList<>();
        Rollup rollup = rollup(params);

        try {
            Contributions injection = resolveInjection(context, params);
            if (injection == null) {
                log.warn("action=assertion_no_injection executionId={} assertionId={}",
                        context.executionId().value(), step.id().value());
                return AssertionSummaryFactory.build(step.id(), AssertionFamily.PERFORMANCE_THRESHOLDS,
                        slo, rollup,
                        List.of(Check.inconclusive(com.performance.platform.domain.assertion.MetricId.of(
                                        "injection.requests.total"),
                                "injection metrics",
                                "No InjectionResult available in the execution context")),
                        Duration.between(start, Instant.now()), Instant.now());
            }

            TimeWindow window = window(injection.first());
            checks.addAll(latencyChecks(injection, params, window));
            checks.addAll(errorRateChecks(injection, params, window));
            checks.addAll(throughputChecks(injection, params, window));
            checks.addAll(explicitChecks(injection, params, window));

            if (checks.isEmpty()) {
                checks.add(Check.inconclusive(
                        com.performance.platform.domain.assertion.MetricId.of("injection.requests.total"),
                        "performance thresholds", "No threshold configured in the assertion"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("action=assertion_param_error executionId={} assertionId={} error={}",
                    context.executionId().value(), step.id().value(), e.getMessage());
            checks.add(new Check(
                    com.performance.platform.domain.assertion.MetricId.of("injection.requests.total"),
                    null, null, AssertionStatus.ERROR, "parameter binding", e.getMessage()));
        }

        return AssertionSummaryFactory.build(step.id(), AssertionFamily.PERFORMANCE_THRESHOLDS,
                slo, rollup, checks, Duration.between(start, Instant.now()), Instant.now());
    }

    // ------------------------------------------------------------------

    private List<Check> latencyChecks(Contributions injection, ParameterBinder params,
                                      TimeWindow window) {
        Map<String, Object> latency = params.optionalMap("latency");
        if (latency.isEmpty()) {
            return List.of();
        }
        ParameterBinder latencyParams = ParameterBinder.of(latency);
        Severity severity = latencyParams.optionalSeverity("severity", Severity.WARNING);
        double tolerance = latencyParams.optionalDouble("tolerance", 0.0);
        double p50 = latencyParams.optionalDouble("p50");
        double p95 = latencyParams.optionalDouble("p95");
        double p99 = latencyParams.optionalDouble("p99");

        List<Check> checks = new ArrayList<>();
        checks.add(latencyCheck(injection, window, "injection.latency.p50", p50, severity, tolerance));
        checks.add(latencyCheck(injection, window, "injection.latency.p95", p95, severity, tolerance));
        checks.add(latencyCheck(injection, window, "injection.latency.p99", p99, severity, tolerance));
        return checks;
    }

    private Check latencyCheck(Contributions injection, TimeWindow window, String metric,
                               double threshold, Severity severity, double tolerance) {
        MetricDefinition definition = MetricCatalog.require(metric);
        com.performance.platform.domain.assertion.MetricId id = definition.id();
        if (Double.isNaN(threshold)) {
            return Check.skipped(id, metric + " not constrained", "No threshold provided");
        }
        Expectation expectation = new Expectation(AssertionOperator.LTE, threshold, tolerance, severity);
        return evaluate(injection, definition, window, expectation,
                metric + " <= " + trim(threshold) + " " + definition.unit());
    }

    private List<Check> errorRateChecks(Contributions injection, ParameterBinder params,
                                        TimeWindow window) {
        return singleThreshold(injection, params, window, "errorRate", "injection.error.rate",
                AssertionOperator.LTE, "max", Severity.CRITICAL);
    }

    private List<Check> throughputChecks(Contributions injection, ParameterBinder params,
                                         TimeWindow window) {
        return singleThreshold(injection, params, window, "throughput", "injection.throughput",
                AssertionOperator.GTE, "min", Severity.WARNING);
    }

    private List<Check> singleThreshold(Contributions injection, ParameterBinder params,
                                        TimeWindow window, String blockKey, String metric,
                                        AssertionOperator defaultOperator, String boundKey,
                                        Severity defaultSeverity) {
        if (!params.has(blockKey)) {
            return List.of();
        }
        Object raw = params.raw(blockKey);
        ParameterBinder block = raw instanceof Map<?, ?>
                ? ParameterBinder.of(params.optionalMap(blockKey))
                : new ParameterBinder(Map.of(boundKey, raw));
        MetricDefinition definition = MetricCatalog.require(metric);
        Severity severity = block.optionalSeverity("severity", defaultSeverity);
        double tolerance = block.optionalDouble("tolerance", 0.0);
        double threshold = block.optionalDouble(boundKey);
        if (Double.isNaN(threshold)) {
            return List.of(Check.skipped(definition.id(), metric + " not constrained",
                    "No '" + boundKey + "' provided"));
        }
        Expectation expectation = new Expectation(defaultOperator, threshold, tolerance, severity);
        return List.of(evaluate(injection, definition, window, expectation,
                metric + " " + OperatorResolver.symbol(defaultOperator) + " " + trim(threshold)
                        + " " + definition.unit()));
    }

    private List<Check> explicitChecks(Contributions injection, ParameterBinder params,
                                       TimeWindow window) {
        List<Map<String, Object>> declared = params.optionalListOfMaps("checks");
        if (declared.isEmpty()) {
            return List.of();
        }
        List<Check> checks = new ArrayList<>();
        for (Map<String, Object> entry : declared) {
            ParameterBinder check = ParameterBinder.of(entry);
            String metricName = check.requireString("metric");
            MetricDefinition definition = MetricCatalog.require(metricName);
            Severity severity = check.optionalSeverity("severity", Severity.WARNING);
            double tolerance = check.optionalDouble("tolerance", 0.0);
            double threshold = check.requireDouble("threshold");
            AssertionOperator operator = OperatorResolver.resolve(check.requireString("operator"));
            if (operator == AssertionOperator.EQ && tolerance == 0
                    && definition.requiresToleranceForEquality()) {
                throw new IllegalArgumentException(
                        "EQ on metric '" + metricName + "' requires a positive tolerance");
            }
            Expectation expectation = new Expectation(operator, threshold, tolerance, severity);
            checks.add(evaluate(injection, definition, window, expectation,
                    check.optionalString("intent", metricName + " "
                            + OperatorResolver.symbol(operator) + " " + trim(threshold))));
        }
        return checks;
    }

    // ------------------------------------------------------------------

    /** Toutes les contributions d'agents pour une injection ; une seule => chemin historique. */
    record Contributions(List<InjectionResult> all) {
        Contributions {
            all = List.copyOf(all);
        }

        InjectionResult first() {
            return all.get(0);
        }
    }

    private static Check evaluate(Contributions injection, MetricDefinition definition,
                                  TimeWindow window, Expectation expectation, String label) {
        InjectionResult first = injection.first();
        double value;
        if (injection.all().size() == 1) {
            value = valueOf(first, definition.id().toString());
        } else {
            // Reduction multi-agents (ADR-068) : MERGE/SUM/MIN/... via InjectionAggregator
            InjectionAggregator.Measure measure =
                    InjectionAggregator.aggregate(injection.all()).get(definition.id().toString());
            if (measure.isInconclusive()) {
                return Check.inconclusive(definition.id(), label, measure.inconclusiveReason());
            }
            value = measure.value();
        }
        Observation observation = new Observation(definition.id(), value, definition.unit(), window,
                first.taskId().value(),
                QueryFingerprint.internal(first.taskId().value(), window),
                Map.of("simulationClass", first.simulationClass(),
                        "totalRequests", injection.all().stream()
                                .mapToLong(InjectionResult::totalRequests).sum(),
                        "agents", injection.all().size()));
        return CheckEvaluator.evaluate(observation, expectation, definition.id(), label);
    }

    static double valueOf(InjectionResult result, String metric) {
        return switch (metric) {
            case "injection.latency.p50" -> result.p50Ms();
            case "injection.latency.p75" -> result.p75Ms();
            case "injection.latency.p90" -> result.p90Ms();
            case "injection.latency.p95" -> result.p95Ms();
            case "injection.latency.p99" -> result.p99Ms();
            case "injection.latency.max" -> result.maxMs();
            case "injection.latency.min" -> result.minMs();
            case "injection.latency.mean" -> result.meanMs();
            case "injection.error.rate" -> result.errorRate();
            case "injection.throughput" -> result.throughput();
            case "injection.requests.total" -> result.totalRequests();
            case "injection.requests.failed" -> result.failedRequests();
            default -> throw new IllegalArgumentException(
                    "Metric '" + metric + "' is not derived from an InjectionResult");
        };
    }

    /**
     * ADR-053 : {@code ref} est un champ de premiere classe de l'etape.
     * Il est transmis sous la cle interne {@code refTaskId}.
     */
    private static Map<String, Object> referenceParameters(StepDefinition step) {
        if (step.ref() == null) {
            return step.parameters();
        }
        var parameters = new HashMap<String, Object>(step.parameters());
        parameters.put("refTaskId", step.ref().value());
        return Map.copyOf(parameters);
    }

    private static Contributions resolveInjection(ExecutionContext context, ParameterBinder params) {
        String refTaskId = params.optionalString("refTaskId", null);
        List<InjectionResult> found = new ArrayList<>();
        if (refTaskId != null) {
            for (var result : context.getAll(refTaskId).values()) {
                Object output = result.outputs().get(InjectionResult.OUTPUT_KEY);
                if (output instanceof InjectionResult injection) {
                    found.add(injection);
                }
            }
            return found.isEmpty() ? null : new Contributions(found);
        }
        for (var taskEntry : context.store().entrySet()) {
            for (var agentEntry : taskEntry.getValue().entrySet()) {
                for (Object output : agentEntry.getValue().outputs().values()) {
                    if (output instanceof InjectionResult injection) {
                        return new Contributions(List.of(injection));
                    }
                }
            }
        }
        return null;
    }

    private static TimeWindow window(InjectionResult injection) {
        Instant end = Instant.now();
        Duration duration = injection.duration() == null ? Duration.ZERO : injection.duration();
        return new TimeWindow(end.minus(duration), end, null);
    }

    private static Rollup rollup(ParameterBinder params) {
        String raw = params.optionalString("rollup", "ALL");
        if (raw == null || raw.isBlank()) {
            return Rollup.all();
        }
        String normalized = raw.toUpperCase();
        if (normalized.startsWith("QUORUM")) {
            String digits = normalized.replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                return Rollup.quorum(Integer.parseInt(digits));
            }
        }
        return switch (normalized) {
            case "ANY" -> Rollup.any();
            default -> Rollup.all();
        };
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Expose les noms de metriques supportes (documentation, tests). */
    public static List<String> supportedMetrics() {
        List<String> metrics = new ArrayList<>(LATENCY_METRICS);
        metrics.add("injection.error.rate");
        metrics.add("injection.throughput");
        return List.copyOf(metrics);
    }

    /** Representation des parametres attendus, pour les messages d'erreur. */
    public static Map<String, String> parameterHelp() {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("latency", "{p50,p95,p99,unit,severity,tolerance}");
        help.put("errorRate", "{max,severity,tolerance}");
        help.put("throughput", "{min,severity,tolerance}");
        help.put("checks", "[{metric,operator,threshold,tolerance,severity,intent}]");
        help.put("refTaskId", "injection task id (optional if a single injection)");
        help.put("rollup", "ALL | ANY | QUORUM:n");
        return Map.copyOf(help);
    }
}
