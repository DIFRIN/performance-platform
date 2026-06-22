package com.performance.platform.assertion.httpmock;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.infrastructure.executor.http.HttpTargetRegistry;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.AssertionExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
 * <b>v2.0.0 :</b> Supporte la resolution de la cible WireMock via
 * {@link HttpTargetRegistry} (parametre {@code target}) en plus du flux
 * legacy {@code refTaskId} (URL depuis ExecutionContext) et
 * {@code wiremockUrl} direct.
 * <p>
 * Toute operation I/O (appel HTTP a WireMock) est executee sous
 * Virtual Threads.
 * <p>
 * Annotee {@code @Assertion(name = "http-mock")} pour la decouverte
 * automatique par le {@code DefaultAssertionExecutorRegistry}.
 * <p>
 * <b>CC-02 :</b> Pipeline cohesif d'evaluation WireMock inseparable —
 * resolution source (target/wiremockUrl/refTaskId) → appel WireMock
 * (RestClient v2 ou JDK HttpClient legacy) → evaluation operateur
 * → construction AssertionResult, avec helpers parametres/metriques/
 * construction indissociables. Classe >300 lignes car les 3 strategies
 * de resolution HTTP + les helpers forment un ensemble coherent dont
 * l'extraction artificielle nuirait a la lisibilite.
 */
@Component
@Assertion(name = "http-mock", version = "2.0.0",
           description = "WireMock receivedCalls/matchedCalls/unmatchedCalls assertions")
public class HttpMockAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpMockAssertionExecutor.class);

    // --- Constantes de parametres ---

    static final String PARAM_METRIC = "metric";
    static final String PARAM_ENDPOINT = "endpoint";
    static final String PARAM_OPERATOR = "operator";
    static final String PARAM_VALUE = "value";
    static final String PARAM_REF_TASK_ID = "refTaskId";
    static final String PARAM_TARGET = "target";
    static final String PARAM_WIREMOCK_URL = "wiremockUrl";

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

    private final HttpTargetRegistry targetRegistry;
    private final HttpClient httpClient;

    public HttpMockAssertionExecutor(HttpTargetRegistry targetRegistry) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry,
                "targetRegistry must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // Package-private pour tests
    HttpMockAssertionExecutor(HttpTargetRegistry targetRegistry, HttpClient httpClient) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry,
                "targetRegistry must not be null");
        this.httpClient = Objects.requireNonNull(httpClient,
                "httpClient must not be null");
    }

    @Override
    public String getSupportedAssertionName() {
        return "http-mock";
    }

    /**
     * Evalue une assertion WireMock en interrogeant l'API admin.
     * <p>
     * <b>CC-02 :</b> Pipeline cohesif inseparable (~123 lignes) —
     * (1) extraction/validation parametres, (2) validation metrique,
     * (3) resolution operateur, (4) resolution source HTTP tri-flux
     * (target → wiremockUrl → refTaskId), (5) evaluation operateur,
     * (6) construction Evidence + AssertionResult, (7) logging structure.
     * Les etapes sont sequentielles avec logique de fallback
     * (v2 target → deprecated wiremockUrl → legacy refTaskId), formant
     * un flux de decision que l'extraction fragmenterait artificiellement.
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
            String refTaskId = getStringParam(params, PARAM_REF_TASK_ID, null);
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

            // 4. Resoudre la source HTTP (v2 target, legacy wiremockUrl, ou refTaskId)
            String targetName = getStringParam(params, PARAM_TARGET, null);
            String wiremockUrl = getStringParam(params, PARAM_WIREMOCK_URL, null);
            double actualValue;
            String mockUrl;

            if (targetName != null && !targetName.isBlank()) {
                RestClient restClient;
                try {
                    restClient = targetRegistry.clientFor(targetName);
                } catch (IllegalArgumentException e) {
                    log.warn("action=http_mock_assertion_target_not_found executionId={} "
                             + "assertionId={} target={}",
                             context.executionId().value(), step.id().value(), targetName);
                    return buildErrorResult(step, start,
                            "Unknown http-target: '" + targetName + "'. "
                            + "Check platform.http-targets configuration.",
                            params);
                }
                actualValue = fetchRequestCountViaRestClient(restClient);
                mockUrl = targetRegistry.get(targetName) != null
                        ? targetRegistry.get(targetName).baseUrl() : targetName;
            } else if (wiremockUrl != null && !wiremockUrl.isBlank()) {
                log.warn("action=deprecated_param param=wiremockUrl executionId={} "
                         + "assertionId={} — use 'target:' instead",
                         context.executionId().value(), step.id().value());
                actualValue = fetchRequestCount(wiremockUrl);
                mockUrl = wiremockUrl;
            } else {
                if (refTaskId == null || refTaskId.isBlank()) {
                    log.warn("action=http_mock_assertion_no_source executionId={} "
                             + "assertionId={}",
                             context.executionId().value(), step.id().value());
                    return buildErrorResult(step, start,
                            "No HTTP source configured. Provide 'target', "
                            + "'wiremockUrl', or 'refTaskId' parameter.",
                            params);
                }
                mockUrl = resolveMockUrl(context, refTaskId);
                if (mockUrl == null) {
                    log.warn("action=http_mock_assertion_mock_url_not_found executionId={} "
                             + "assertionId={} refTaskId={}",
                             context.executionId().value(), step.id().value(), refTaskId);
                    return buildErrorResult(step, start,
                            "Mock URL not found for refTaskId: '" + refTaskId
                            + "'. Provide 'target' or 'wiremockUrl' parameter, "
                            + "or ensure MockServerTaskExecutor produced 'url' output.",
                            params);
                }
                actualValue = fetchRequestCount(mockUrl);
            }

            boolean passed = operator.evaluate(actualValue, expectedValue);

            var details = new HashMap<String, Object>();
            details.put("metric", metricName);
            details.put("mockUrl", mockUrl);
            if (refTaskId != null && !refTaskId.isBlank()) details.put("refTaskId", refTaskId);
            if (targetName != null && !targetName.isBlank()) details.put("target", targetName);
            if (endpoint != null) details.put("endpoint", endpoint);

            var evidence = new Evidence(
                    actualValue,
                    expectedValue,
                    operator,
                    "calls",
                    Map.copyOf(details));

            AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
            String description = buildDescription(metricName, actualValue,
                    expectedValue, operator, passed);

            Duration evaluationDuration = Duration.between(start, Instant.now());

            log.info("action=http_mock_assertion_evaluated executionId={} assertionId={} "
                     + "metric={} refTaskId={} target={} actual={} expected={} operator={} status={}",
                     context.executionId().value(), step.id().value(),
                     metricName, refTaskId, targetName, actualValue, expectedValue, operatorStr, status);

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

    // --- Requete WireMock via RestClient (v2) ---

    double fetchRequestCountViaRestClient(RestClient restClient) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Double> future = executor.submit(() -> {
                String json = restClient.get()
                        .uri(ADMIN_REQUESTS_COUNT_PATH)
                        .retrieve()
                        .body(String.class);
                return extractCount(json);
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

    // --- Resolution URL Mock ---

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
