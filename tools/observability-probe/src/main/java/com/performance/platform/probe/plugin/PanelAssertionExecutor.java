package com.performance.platform.probe.plugin;

import com.performance.platform.assertion.AssertionSummaryFactory;
import com.performance.platform.assertion.CheckEvaluator;
import com.performance.platform.assertion.OperatorResolver;
import com.performance.platform.assertion.ParameterBinder;
import com.performance.platform.assertion.TemporalCheckEvaluator;
import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummaries;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.CriterionType;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Reduction;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.SamplePoint;
import com.performance.platform.domain.assertion.SeriesObservation;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.SloStatement;
import com.performance.platform.domain.assertion.TemporalCriterion;
import com.performance.platform.domain.assertion.TimeWindow;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;

import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.probe.ProbeConfig;
import com.performance.platform.probe.TimeParser;
import com.performance.platform.probe.grafana.GrafanaClient;
import com.performance.platform.probe.kibana.KibanaClient;
import com.performance.platform.probe.model.ProbePoint;
import com.performance.platform.probe.model.ProbeSeries;
import com.performance.platform.probe.model.ProbeWindow;
import com.performance.platform.probe.model.QueryMapping;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assertion de panel : rejoue les requetes Grafana/Kibana sur la fenetre du
 * test et produit des checks normalises.
 * <p>
 * Implemente {@link AssertionExecutor} sans dependance Spring : le JAR de
 * l'annexe est decouvert par le {@code PluginRegistry} de la plateforme
 * (constructeur no-arg, ADR-050).
 * <p>
 * Parametres :
 * <pre>
 * source: grafana-prod
 * dashboard: api-slo        (ou saved-object + index + field)
 * panel: 12
 * lookback: 15m
 * queries: [{refId: A, metric: injection.latency.p95, unit: ms}]
 * checks:  [{metric: injection.latency.p95, operator: LTE, threshold: 500,
 *            severity: CRITICAL, criterion: SUSTAINED, limit: 0.9, minSamples: 30}]
 * description: {title, sli, slo, rationale}
 * </pre>
 */
@Assertion(name = "panel-assertion",
        description = "Dashboard panel assertion (Grafana/Kibana query replay)")
public class PanelAssertionExecutor implements AssertionExecutor {

    private static final String TASK_NAME = "panel-assertion";

    @Override
    public String getSupportedAssertionName() {
        return TASK_NAME;
    }

    @Override
    public AssertionSummary evaluate(ExecutionContext context, StepDefinition step) {
        Instant start = Instant.now();
        ParameterBinder params = ParameterBinder.of(step.parameters());
        SloStatement slo = AssertionSummaries.sloStatement(step);
        List<Check> checks = new ArrayList<>();

        try {
            ProbeConfig config = ProbeConfig.load(params.optionalString("source", "default"), null);
            ProbeWindow window = window(params);
            Map<String, String> variables = variables(params);
            List<ProbeSeries> series = fetch(config, params, window, variables);

            for (Map<String, Object> declared : params.optionalListOfMaps("checks")) {
                checks.add(evaluateCheck(declared, series, window));
            }
            if (checks.isEmpty()) {
                checks.add(Check.inconclusive(MetricId.of("panel.series.A"),
                        "panel assertion", "No check declared in the assertion"));
            }
        } catch (RuntimeException e) {
            checks.add(new Check(MetricId.of("panel.series.A"), null, null,
                    AssertionStatus.ERROR, "panel assertion", e.getMessage()));
        }

        Rollup rollup = Rollup.all();
        Duration duration = Duration.between(start, Instant.now());
        return AssertionSummaryFactory.build(step.id(), AssertionFamily.PANEL, slo, rollup,
                checks, duration, Instant.now());
    }

    // ------------------------------------------------------------------

    private List<ProbeSeries> fetch(ProbeConfig config, ParameterBinder params,
                                    ProbeWindow window, Map<String, String> variables) {
        List<QueryMapping> mappings = new ArrayList<>();
        for (Map<String, Object> query : params.optionalListOfMaps("queries")) {
            ParameterBinder binder = new ParameterBinder(query);
            mappings.add(new QueryMapping(
                    binder.optionalString("refId", "A"),
                    binder.optionalString("metric", "panel.series.A"),
                    binder.optionalString("unit", "")));
        }
        if (config.isGrafana()) {
            var client = new GrafanaClient(config);
            long step = TimeParser.parseDurationMillis(params.optionalString("step", "15s"),
                    Duration.ofSeconds(15));
            return client.query(params.requireString("dashboard"),
                    params.optionalInt("panel", 0), mappings, window, step, variables,
                    params.optionalInt("maxDataPoints", 400));
        }
        var client = new KibanaClient(config);
        QueryMapping mapping = mappings.isEmpty()
                ? new QueryMapping("A", "panel.series.A", "")
                : mappings.get(0);
        var esQuery = new KibanaClient.EsQuery(
                params.requireString("index"),
                params.optionalString("timeField", "@timestamp"),
                params.requireString("field"),
                params.optionalString("agg", "avg"),
                params.has("percentile") ? params.optionalDouble("percentile") : null,
                params.optionalString("interval", "1m"),
                mapping.metric(), mapping.unit());
        return client.query(esQuery, window, params.optionalString("mode", null));
    }

    private Check evaluateCheck(Map<String, Object> declared, List<ProbeSeries> series,
                                ProbeWindow window) {
        ParameterBinder binder = new ParameterBinder(declared);
        String metricName = binder.requireString("metric");
        MetricId metric = MetricId.of(metricName);
        Severity severity = binder.optionalSeverity("severity", Severity.WARNING);
        double tolerance = binder.optionalDouble("tolerance", 0.0);

        ProbeSeries matching = series.stream()
                .filter(one -> one.metric().equals(metricName))
                .findFirst()
                .orElse(null);
        if (matching == null || matching.isEmpty()) {
            return Check.inconclusive(metric, metricName,
                    "No data for '" + metricName + "' in window");
        }
        SeriesObservation observation = toSeries(metric, matching, window);
        String criterion = binder.optionalString("criterion", null);
        if (criterion != null) {
            return TemporalCheckEvaluator.evaluate(observation,
                    temporal(binder, criterion, severity), metricName, window.to());
        }
        AssertionOperator operator = OperatorResolver.resolve(binder.requireString("operator"));
        double threshold = binder.requireDouble("threshold");
        Expectation expectation = new Expectation(operator, threshold, tolerance, severity);
        Observation reduced = observation.reduce(Reduction.MAX);
        return CheckEvaluator.evaluate(reduced, expectation, metric,
                metricName + " " + OperatorResolver.symbol(operator) + " " + threshold);
    }

    private TemporalCriterion temporal(ParameterBinder binder, String criterion, Severity severity) {
        CriterionType type = CriterionType.valueOf(criterion.toUpperCase());
        DocFormatter.require(type);
        return new TemporalCriterion(type,
                binder.has("operator") ? OperatorResolver.resolve(binder.requireString("operator")) : null,
                binder.has("threshold") ? binder.optionalDouble("threshold") : null,
                binder.has("limit") ? binder.optionalDouble("limit") : null,
                binder.has("within") ? TimeParser.parseDuration(binder.optionalString("within", null), null) : null,
                binder.optionalInt("minSamples", TemporalCheckEvaluator.DEFAULT_MIN_SAMPLES),
                severity);
    }

    private static SeriesObservation toSeries(MetricId metric, ProbeSeries series, ProbeWindow window) {
        List<SamplePoint> points = series.points().stream()
                .map(point -> new SamplePoint(Instant.ofEpochMilli(point.timestamp()), point.value()))
                .toList();
        TimeWindow timeWindow = new TimeWindow(window.from(), window.to(),
                Duration.ofMillis(window.resolutionMillis()));
        return new SeriesObservation(metric, series.unit(), timeWindow, points,
                "panel", null, Map.of("sourceMetric", series.metric()));
    }

    private static ProbeWindow window(ParameterBinder params) {
        String from = params.optionalString("from", null);
        String to = params.optionalString("to", "now");
        if (from != null) {
            return TimeParser.window(from, to, params.optionalString("step",
                    params.optionalString("interval", "15s")));
        }
        Duration lookback = TimeParser.parseDuration(params.optionalString("lookback", "15m"),
                Duration.ofMinutes(15));
        Instant now = Instant.now();
        return new ProbeWindow(now.minus(lookback), now,
                TimeParser.parseDurationMillis(params.optionalString("step", "15s"),
                        Duration.ofSeconds(15)));
    }

    private static Map<String, String> variables(ParameterBinder params) {
        Map<String, String> variables = new LinkedHashMap<>();
        Map<String, Object> declared = params.optionalMap("variables");
        declared.forEach((key, value) -> variables.put(key, String.valueOf(value)));
        return variables;
    }

    /** Garde-fou : le type de critere doit exister (message lisible sinon). */
    private static final class DocFormatter {
        static void require(CriterionType type) {
            if (type == null) {
                throw new IllegalArgumentException("Unknown temporal criterion");
            }
        }
    }
}
