package com.performance.platform.assertion.httpmock;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assertion executor pour les assertions WireMock.
 * Interroge l'API admin WireMock pour obtenir le nombre d'appels
 * recus et le compare a un seuil via un {@link AssertionOperator}.
 * <p>
 * Supporte les metriques {@code receivedCalls}, {@code matchedCalls}
 * et {@code unmatchedCalls} — toutes interrogeant
 * {@code /__admin/requests/count}.
 * <p>
 * Toute operation I/O (appel HTTP a WireMock) est executee sous
 * Virtual Threads.
 * <p>
 * Annotee {@code @Assertion(name = "http-mock")} pour la decouverte
 * automatique par le {@code DefaultAssertionExecutorRegistry}.
 * <p>
 * <b>CC-02:</b> Pipeline cohesif d'evaluation WireMock —
 * resolution des parametres ({@link #getRequiredStringParam},
 * {@link #getRequiredDoubleParam}) → validation metrique →
 * resolution URL mock depuis {@link ExecutionContext}
 * ({@link #resolveMockUrl}) → appel HTTP
 * {@code /__admin/requests/count} ({@link #fetchRequestCount}) →
 * extraction count JSON ({@link #extractCount}) →
 * evaluation {@link AssertionOperator} → construction
 * {@link AssertionResult} ({@link #buildErrorResult},
 * {@link #buildDescription}). Les helpers de parametres,
 * de resolution, d'appel HTTP et de construction sont
 * indissociables du flux d'evaluation WireMock.
 * Extraire une portion isolee nuirait a la lisibilite
 * du pipeline sequentiel.</p>
 */
@Component
@Assertion(name = "http-mock",
           description = "WireMock receivedCalls/matchedCalls/unmatchedCalls assertions")
public class HttpMockAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpMockAssertionExecutor.class);

    // --- Constantes de parametres ---

    static final String PARAM_METRIC = "metric";
    static final String PARAM_ENDPOINT = "endpoint";
    static final String PARAM_OPERATOR = "operator";
    static final String PARAM_VALUE = "value";
    static final String PARAM_REF_TASK_ID = "refTaskId";

    // --- Noms de metriques supportes ---

    static final String METRIC_RECEIVED_CALLS = "receivedCalls";
    static final String METRIC_MATCHED_CALLS = "matchedCalls";
    static final String METRIC_UNMATCHED_CALLS = "unmatchedCalls";

    // --- Clefs de sortie MockServerTaskExecutor ---

    static final String OUTPUT_URL = "url";

    // --- WireMock admin API ---

    static final String ADMIN_REQUESTS_COUNT_PATH = "/__admin/requests/count";

    // --- Pattern JSON pour extraire "count" ---

    private static final Pattern COUNT_PATTERN =
            Pattern.compile("\"count\"\\s*:\\s*(\\d+)");

    private final HttpClient httpClient;

    public HttpMockAssertionExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // Package-private pour tests
    HttpMockAssertionExecutor(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient,
                "httpClient must not be null");
    }

    @Override
    public String getSupportedAssertionName() {
        return "http-mock";
    }

    /**
     * CC-02: pipeline cohesif — extraction parametres, resolution URL mock,
     * appel HTTP WireMock admin, extraction count, evaluation operateur,
     * construction resultat. L'extraction en methodes separees fragmenterait
     * la logique de conversion metric→endpoint HTTP→count sans gain
     * de reutilisabilite.
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
            String endpoint = getStringParam(params, PARAM_ENDPOINT, null);

            // 2. Valider la metrique
            if (!isSupportedMetric(metricName)) {
                log.warn("action=http_mock_assertion_unsupported_metric executionId={} "
                         + "assertionId={} metric={}",
                         context.executionId().value(), step.id().value(), metricName);
                return buildErrorResult(step, start,
                        "Unsupported metric: '" + metricName
                        + "'. Supported: receivedCalls, matchedCalls, unmatchedCalls",
                        params);
            }

            // 3. Resoudre l'operateur
            AssertionOperator operator = resolveOperator(operatorStr);

            // 4. Lire l'URL du mock depuis le contexte
            String mockUrl = resolveMockUrl(context, refTaskId);
            if (mockUrl == null) {
                log.warn("action=http_mock_assertion_mock_url_not_found executionId={} "
                         + "assertionId={} refTaskId={}",
                         context.executionId().value(), step.id().value(), refTaskId);
                return buildErrorResult(step, start,
                        "Mock URL not found for refTaskId: '" + refTaskId
                        + "'. Ensure MockServerTaskExecutor produced 'url' output.",
                        params);
            }

            // 5. Appeler l'API admin WireMock sous Virtual Thread
            double actualValue = fetchRequestCount(mockUrl);

            // 6. Evaluer l'operateur
            boolean passed = operator.evaluate(actualValue, expectedValue);

            // 7. Construire l'evidence
            Map<String, Object> details = new HashMap<>();
            details.put("metric", metricName);
            details.put("mockUrl", mockUrl);
            details.put("refTaskId", refTaskId);
            if (endpoint != null) details.put("endpoint", endpoint);

            Evidence evidence = new Evidence(
                    actualValue,
                    expectedValue,
                    operator,
                    "calls",
                    Map.copyOf(details));

            // 8. Construire la description et le resultat
            AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
            String description = buildDescription(metricName, actualValue,
                    expectedValue, operator, passed);

            Duration evaluationDuration = Duration.between(start, Instant.now());

            log.info("action=http_mock_assertion_evaluated executionId={} assertionId={} "
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
            log.warn("action=http_mock_assertion_param_error executionId={} assertionId={} error={}",
                     context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start, e.getMessage(),
                    step.parameters());
        }
    }

    // --- Resolution URL Mock ---

    /**
     * Resout l'URL de base du WireMock depuis les outputs du contexte.
     * Lit le {@link TaskResult} pour la task refTaskId et extrait la clef
     * {@code "url"}.
     *
     * @param context   le contexte d'execution
     * @param refTaskId l'identifiant de la task MockServer
     * @return l'URL de base, ou null si non trouvee
     */
    private String resolveMockUrl(ExecutionContext context, String refTaskId) {
        Map<String, TaskResult> allResults = context.getAll(refTaskId);
        if (allResults.isEmpty()) return null;
        TaskResult firstResult = allResults.values().iterator().next();
        Object urlObj = firstResult.outputs().get(OUTPUT_URL);
        if (urlObj instanceof String url && !url.isEmpty()) {
            return url;
        }
        return null;
    }

    // --- Appel HTTP WireMock Admin ---

    /**
     * Interroge l'API admin WireMock {@code /__admin/requests/count}
     * et retourne le nombre d'appels.
     * <p>
     * L'appel HTTP est execute sous Virtual Thread pour ne pas bloquer
     * la plateforme.
     *
     * @param baseUrl l'URL de base du WireMock (ex: {@code http://localhost:8090})
     * @return le nombre de requetes dans le journal WireMock
     * @throws IllegalArgumentException si l'appel HTTP echoue
     */
    double fetchRequestCount(String baseUrl) {
        String url = baseUrl.replaceAll("/+$", "") + ADMIN_REQUESTS_COUNT_PATH;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Double> future = executor.submit(() -> {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalArgumentException(
                            "WireMock admin API returned HTTP "
                            + response.statusCode() + " for: " + url);
                }
                return extractCount(response.body());
            });
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(
                    "WireMock request count fetch interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException(
                    "WireMock request count fetch failed: "
                    + e.getMessage(), e.getCause());
        }
    }

    /**
     * Extrait le count du corps JSON WireMock {@code {"count": 123}}.
     */
    private double extractCount(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty response from WireMock admin API");
        }
        Matcher matcher = COUNT_PATTERN.matcher(json);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        throw new IllegalArgumentException(
                "Could not extract 'count' from WireMock response: "
                + json.substring(0, Math.min(200, json.length())));
    }

    // --- Helpers de metrique ---

    private boolean isSupportedMetric(String metric) {
        return METRIC_RECEIVED_CALLS.equals(metric)
                || METRIC_MATCHED_CALLS.equals(metric)
                || METRIC_UNMATCHED_CALLS.equals(metric);
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
        return String.format("%s: WireMock %s %.0f %s %.0f",
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
}
