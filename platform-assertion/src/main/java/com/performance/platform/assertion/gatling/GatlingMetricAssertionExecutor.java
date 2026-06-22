package com.performance.platform.assertion.gatling;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.AssertionExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CC-02: pipeline cohesif — extraction parametres, resolution contexte,
 * extraction metrique, evaluation operateur, construction resultat.
 * L'extraction en methodes separees fragmenterait la logique de
 * transformation inject→metrique→comparaison sans gain de reutilisabilite.
 * <p>
 * Assertion executor pour les metriques de performance Gatling.
 * Lit un {@link InjectionResult} depuis le {@link ExecutionContext},
 * extrait une metrique nommee (p95, errorRate, throughput...),
 * et la compare a un seuil via un {@link AssertionOperator}.
 * <p>
 * Annotee {@code @Assertion(name = "gatling-metric")} pour la
 * decouverte automatique par le {@code DefaultAssertionExecutorRegistry}.
 */
@Component
@Assertion(name = "gatling-metric",
           description = "Gatling metrics: p95, errorRate, throughput, etc.")
public class GatlingMetricAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(GatlingMetricAssertionExecutor.class);

    // --- Constantes de parametres ---

    static final String PARAM_METRIC = "metric";
    static final String PARAM_OPERATOR = "operator";
    static final String PARAM_VALUE = "value";
    static final String PARAM_UNIT = "unit";
    static final String PARAM_REF_TASK_ID = "refTaskId";

    @Override
    public String getSupportedAssertionName() {
        return "gatling-metric";
    }

    @Override
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        var start = Instant.now();

        try {
            Map<String, Object> params = step.parameters();

            // 1. Extraire et valider les parametres
            String metricName = getRequiredStringParam(params, PARAM_METRIC);
            String operatorStr = getRequiredStringParam(params, PARAM_OPERATOR);
            double expectedValue = getRequiredDoubleParam(params, PARAM_VALUE);
            String unit = getStringParam(params, PARAM_UNIT, "");

            // 2. Resoudre l'operateur
            AssertionOperator operator = resolveOperator(operatorStr);

            // 3. Verifier que la metrique est supportee
            if (!MetricExtractor.isSupported(metricName)) {
                return buildErrorResult(step, start,
                        "Unsupported metric: '" + metricName + "'",
                        params);
            }

            // 4. Resoudre l'InjectionResult depuis le contexte
            InjectionResult injectionResult = resolveInjectionResult(context, params);
            if (injectionResult == null) {
                return buildErrorResult(step, start,
                        "No InjectionResult found in context",
                        params);
            }

            // 5. Extraire la valeur reelle de la metrique
            double actualValue = MetricExtractor.extract(injectionResult, metricName);

            // 6. Evaluer l'operateur
            boolean passed = operator.evaluate(actualValue, expectedValue);

            // 7. Construire l'evidence
            var details = new HashMap<String, Object>();
            details.put("simulationClass", injectionResult.simulationClass());
            details.put("totalRequests", injectionResult.totalRequests());
            details.put("metric", metricName);
            details.put("unit", unit);
            details.put("refTaskId", params.getOrDefault(PARAM_REF_TASK_ID,
                    injectionResult.taskId().value()));

            var evidence = new Evidence(
                    actualValue,
                    expectedValue,
                    operator,
                    unit.isEmpty() ? inferUnit(metricName) : unit,
                    Map.copyOf(details));

            // 8. Construire la description et le resultat
            AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
            String description = buildDescription(metricName, actualValue,
                    expectedValue, operator, unit, passed);

            var evaluationDuration = Duration.between(start, Instant.now());

            log.info("action=assertion_evaluated executionId={} assertionId={} metric={} actual={} "
                     + "expected={} operator={} status={}",
                     context.executionId().value(), step.id().value(), metricName, actualValue,
                     expectedValue, operatorStr, status);

            return new AssertionResult(
                    step.id(),
                    status,
                    description,
                    evidence,
                    evaluationDuration,
                    Instant.now());

        } catch (IllegalArgumentException e) {
            log.warn("action=assertion_param_error executionId={} assertionId={} error={}",
                     context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start, e.getMessage(),
                    step.parameters());
        }
    }

    // --- Helpers de resolution ---

    /**
     * Resout l'{@link InjectionResult} depuis le contexte.
     * Si refTaskId est specifie, utilise cette task specifique ;
     * sinon, prend le premier InjectionResult trouve.
     */
    private InjectionResult resolveInjectionResult(ExecutionContext context,
                                                    Map<String, Object> params) {
        Object refTaskIdObj = params.get(PARAM_REF_TASK_ID);
        if (refTaskIdObj instanceof String refTaskId && !refTaskId.isEmpty()) {
            return context.getFirst(refTaskId, InjectionResult.class).orElse(null);
        }
        // Fallback : chercher le premier InjectionResult disponible
        for (var taskEntry : context.store().entrySet()) {
            for (var agentEntry : taskEntry.getValue().entrySet()) {
                for (var output : agentEntry.getValue().outputs().values()) {
                    if (output instanceof InjectionResult ir) {
                        return ir;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Convertit le nom d'operateur en {@link AssertionOperator}.
     *
     * @throws IllegalArgumentException si le nom est invalide
     */
    private AssertionOperator resolveOperator(String operatorStr) {
        try {
            return AssertionOperator.valueOf(operatorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported operator: '" + operatorStr
                    + "'. Supported: LT, LTE, GT, GTE, EQ, NEQ");
        }
    }

    // --- Helpers de parametres ---

    private String getRequiredStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: '" + key + "'");
        }
        if (!(value instanceof String str) || str.isEmpty()) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be a non-empty string, got: " + value);
        }
        return str;
    }

    private double getRequiredDoubleParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: '" + key + "'");
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a number, got: "
                + value.getClass().getSimpleName());
    }

    private String getStringParam(Map<String, Object> params, String key,
                                   String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String str && !str.isEmpty()) {
            return str;
        }
        return defaultValue;
    }

    // --- Helpers de construction ---

    private AssertionResult buildErrorResult(StepDefinition step,
                                              Instant start,
                                              String errorMessage,
                                              Map<String, Object> params) {
        var duration = Duration.between(start, Instant.now());
        return new AssertionResult(
                step.id(),
                AssertionStatus.ERROR,
                errorMessage,
                new Evidence(null, null, AssertionOperator.EQ, null,
                        Map.copyOf(params)),
                duration,
                Instant.now());
    }

    private String buildDescription(String metricName, double actual,
                                     double expected, AssertionOperator operator,
                                     String unit, boolean passed) {
        String unitSuffix = unit.isEmpty() ? "" : " " + unit;
        String verdict = passed ? "PASSED" : "FAILED";
        return String.format("%s: %s %.2f%s %s %.2f%s",
                verdict, metricName, actual, unitSuffix,
                operatorSymbol(operator), expected, unitSuffix);
    }

    private String operatorSymbol(AssertionOperator operator) {
        return switch (operator) {
            case LT  -> "<";
            case LTE -> "<=";
            case GT  -> ">";
            case GTE -> ">=";
            case EQ  -> "==";
            case NEQ -> "!=";
        };
    }

    private String inferUnit(String metricName) {
        return switch (metricName) {
            case "errorRate" -> "%";
            case "throughput" -> "req/s";
            case "totalRequests", "failedRequests" -> "requests";
            default -> "ms";
        };
    }
}
