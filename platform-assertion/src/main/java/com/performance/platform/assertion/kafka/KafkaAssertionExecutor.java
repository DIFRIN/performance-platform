package com.performance.platform.assertion.kafka;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
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
 * Assertion executor pour les metriques Kafka.
 * Lit les outputs d'un step KafkaConsumer ou KafkaProducer depuis
 * l'{@link ExecutionContext}, extrait une metrique (consumedCount,
 * producedCount, lag), et la compare a un seuil via un
 * {@link AssertionOperator}.
 * <p>
 * Annotee {@code @Assertion(name = "kafka")} pour la decouverte
 * automatique par le {@code DefaultAssertionExecutorRegistry}.
 */
@Component
@Assertion(name = "kafka",
           description = "Kafka consumedCount/producedCount/lag assertions")
public class KafkaAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(KafkaAssertionExecutor.class);

    // --- Constantes de parametres ---

    static final String PARAM_METRIC = "metric";
    static final String PARAM_TOPIC = "topic";
    static final String PARAM_GROUP_ID = "groupId";
    static final String PARAM_OPERATOR = "operator";
    static final String PARAM_VALUE = "value";
    static final String PARAM_REF_TASK_ID = "refTaskId";

    // --- Noms de metriques supportes ---

    static final String METRIC_CONSUMED_COUNT = "consumedCount";
    static final String METRIC_PRODUCED_COUNT = "producedCount";
    static final String METRIC_LAG = "lag";

    // --- Clefs de sortie dans les outputs Kafka ---

    static final String OUTPUT_MESSAGES_CONSUMED = "messagesConsumed";
    static final String OUTPUT_MESSAGES_PRODUCED = "messagesProduced";
    static final String OUTPUT_LAG = "lag";

    private static final Map<String, String> METRIC_TO_OUTPUT_KEY = Map.of(
            METRIC_CONSUMED_COUNT, OUTPUT_MESSAGES_CONSUMED,
            METRIC_PRODUCED_COUNT, OUTPUT_MESSAGES_PRODUCED,
            METRIC_LAG, OUTPUT_LAG
    );

    @Override
    public String getSupportedAssertionName() {
        return "kafka";
    }

    /**
     * CC-02: pipeline cohesif — extraction parametres, resolution outputs contexte,
     * extraction metrique Kafka, evaluation operateur, construction resultat.
     * L'extraction en methodes separees fragmenterait la logique de conversion
     * metric→clef→extraction sans gain de reutilisabilite.
     *
     * @param context le contexte d'execution immuable
     * @param step    la definition de l'etape d'assertion
     * @return le resultat de l'evaluation (PASSED, FAILED, ou ERROR)
     */
    @Override
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        Instant start = Instant.now();

        try {
            Map<String, Object> params = step.parameters();

            // 1. Extraire et valider les parametres
            String metricName = getRequiredStringParam(params, PARAM_METRIC);
            String operatorStr = getRequiredStringParam(params, PARAM_OPERATOR);
            double expectedValue = getRequiredDoubleParam(params, PARAM_VALUE);
            String refTaskId = getRequiredStringParam(params, PARAM_REF_TASK_ID);
            String topic = getStringParam(params, PARAM_TOPIC, null);
            String groupId = getStringParam(params, PARAM_GROUP_ID, null);

            // 2. Valider la metrique
            String outputKey = METRIC_TO_OUTPUT_KEY.get(metricName);
            if (outputKey == null) {
                return buildErrorResult(step, start,
                        "Unsupported metric: '" + metricName
                        + "'. Supported: consumedCount, producedCount, lag",
                        params);
            }

            // 3. Resoudre l'operateur
            AssertionOperator operator = resolveOperator(operatorStr);

            // 4. Lire les outputs depuis le contexte
            Map<String, TaskResult> allResults = context.getAll(refTaskId);
            if (allResults.isEmpty()) {
                return buildErrorResult(step, start,
                        "No results found for refTaskId: '" + refTaskId + "'",
                        params);
            }

            // 5. Extraire la valeur de la metrique depuis les outputs
            TaskResult firstResult = allResults.values().iterator().next();
            Map<String, Object> outputs = firstResult.outputs();
            Object rawValue = outputs.get(outputKey);
            if (rawValue == null) {
                return buildErrorResult(step, start,
                        "Output key '" + outputKey + "' not found in task '"
                        + refTaskId + "' outputs: " + outputs.keySet(),
                        params);
            }
            double actualValue = toDouble(rawValue, outputKey);

            // 6. Evaluer l'operateur
            boolean passed = operator.evaluate(actualValue, expectedValue);

            // 7. Construire l'evidence
            var details = new HashMap<String, Object>();
            details.put("metric", metricName);
            details.put("outputKey", outputKey);
            details.put("refTaskId", refTaskId);
            if (topic != null) details.put("topic", topic);
            if (groupId != null) details.put("groupId", groupId);

            var evidence = new Evidence(
                    actualValue,
                    expectedValue,
                    operator,
                    inferUnit(metricName),
                    Map.copyOf(details));

            // 8. Construire la description et le resultat
            AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
            String description = buildDescription(metricName, actualValue,
                    expectedValue, operator, passed);

            Duration evaluationDuration = Duration.between(start, Instant.now());

            log.info("action=kafka_assertion_evaluated executionId={} assertionId={} "
                     + "metric={} refTaskId={} actual={} expected={} operator={} status={}",
                     context.executionId().value(), step.id().value(),
                     metricName, refTaskId, actualValue, expectedValue, operatorStr, status);

            return new AssertionResult(
                    step.id(),
                    status,
                    description,
                    evidence,
                    evaluationDuration,
                    Instant.now());

        } catch (IllegalArgumentException e) {
            log.warn("action=kafka_assertion_param_error executionId={} assertionId={} error={}",
                     context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start, e.getMessage(),
                    step.parameters());
        }
    }

    // --- Helpers de conversion ---

    /**
     * Convertit une valeur brute en double.
     * Supporte {@link Number} (Integer, Long, Double) et {@link String} parsable.
     *
     * @param rawValue la valeur brute
     * @param key      la clef dans les outputs (pour le message d'erreur)
     * @return la valeur en double
     * @throws IllegalArgumentException si la valeur n'est pas convertible
     */
    private double toDouble(Object rawValue, String key) {
        if (rawValue instanceof Number num) {
            return num.doubleValue();
        }
        if (rawValue instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Output '" + key + "' is not a valid number: '" + str + "'");
            }
        }
        throw new IllegalArgumentException(
                "Output '" + key + "' has unexpected type: "
                + rawValue.getClass().getSimpleName());
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

    private AssertionOperator resolveOperator(String operatorStr) {
        try {
            return AssertionOperator.valueOf(operatorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported operator: '" + operatorStr
                    + "'. Supported: LT, LTE, GT, GTE, EQ, NEQ");
        }
    }

    // --- Helpers de construction ---

    private AssertionResult buildErrorResult(StepDefinition step,
                                              Instant start,
                                              String errorMessage,
                                              Map<String, Object> params) {
        Duration duration = Duration.between(start, Instant.now());
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
                                     boolean passed) {
        String verdict = passed ? "PASSED" : "FAILED";
        return String.format("%s: kafka %s %.2f %s %.2f",
                verdict, metricName, actual,
                operatorSymbol(operator), expected);
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
            case METRIC_CONSUMED_COUNT, METRIC_PRODUCED_COUNT -> "messages";
            case METRIC_LAG -> "offset";
            default -> "units";
        };
    }
}
