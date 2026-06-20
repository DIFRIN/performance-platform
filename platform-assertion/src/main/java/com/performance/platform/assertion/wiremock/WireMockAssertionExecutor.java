package com.performance.platform.assertion.wiremock;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
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
 * Assertion executor for WireMock request count verification.
 * <p>
 * Queries the WireMock admin API ({@code /__admin/requests/count}) to retrieve
 * the total number of received requests and compares it against an expected
 * value using an {@link AssertionOperator}.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code mockUrl} — WireMock base URL (required, e.g. {@code http://wiremock:8080})</li>
 *   <li>{@code operator} — assertion operator: EQ, LT, LTE, GT, GTE, NEQ</li>
 *   <li>{@code expectedCount} — expected request count (required)</li>
 *   <li>{@code endpoint} — optional endpoint filter (not yet implemented, reserved)</li>
 * </ul>
 * <p>
 * All HTTP I/O runs under Virtual Threads.
 * <p>
 * Annotated {@code @Assertion(name = "wiremock")} for auto-discovery by
 * {@code DefaultAssertionExecutorRegistry}.
 * <p>
 * <b>CC-02:</b> Cohesive pipeline — resolve mock URL, call admin API,
 * extract count from JSON, evaluate operator, build result. Each step
 * depends on the previous; extracting into separate classes would
 * fragment a single-purpose sequential flow without reuse.
 */
@Component
@Assertion(name = "wiremock",
        description = "WireMock total request count assertion")
public class WireMockAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(WireMockAssertionExecutor.class);

    // --- Parameter keys ---
    static final String PARAM_MOCK_URL = "mockUrl";
    static final String PARAM_OPERATOR = "operator";
    static final String PARAM_EXPECTED_COUNT = "expectedCount";
    static final String PARAM_ENDPOINT = "endpoint";

    // --- WireMock admin API ---
    static final String ADMIN_REQUESTS_COUNT_PATH = "/__admin/requests/count";

    // --- JSON pattern to extract "count" ---
    private static final Pattern COUNT_PATTERN =
            Pattern.compile("\"count\"\\s*:\\s*(\\d+)");

    private final HttpClient httpClient;

    public WireMockAssertionExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // Package-private constructor for tests
    WireMockAssertionExecutor(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public String getSupportedAssertionName() {
        return "wiremock";
    }

    /**
     * Evaluates the WireMock request count assertion.
     * <p>
     * <b>CC-02:</b> Pipeline steps — param extraction, HTTP call,
     * count extraction, operator evaluation, result construction —
     * form a single cohesive evaluation unit.
     */
    @Override
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        Instant start = Instant.now();

        try {
            Map<String, Object> params = step.parameters();

            // 1. Extract parameters
            String mockUrl = getRequiredStringParam(params, PARAM_MOCK_URL);
            String operatorStr = getStringParam(params, PARAM_OPERATOR, "EQ");
            double expectedCount = getRequiredDoubleParam(params, PARAM_EXPECTED_COUNT);
            String endpoint = getStringParam(params, PARAM_ENDPOINT, null);

            // 2. Resolve operator
            AssertionOperator operator = resolveOperator(operatorStr);

            // 3. Call WireMock admin API under Virtual Thread
            double actualCount = fetchRequestCount(mockUrl);

            // 4. Evaluate operator
            boolean passed = operator.evaluate(actualCount, expectedCount);

            // 5. Build evidence
            Map<String, Object> details = new HashMap<>();
            details.put("mockUrl", mockUrl);
            if (endpoint != null) details.put("endpoint", endpoint);

            Evidence evidence = new Evidence(
                    actualCount,
                    expectedCount,
                    operator,
                    "requests",
                    Map.copyOf(details));

            // 6. Build result
            AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
            String description = buildDescription(actualCount, expectedCount, operator, passed);

            Duration evalDuration = Duration.between(start, Instant.now());

            log.info("action=wiremock_assertion_evaluated executionId={} assertionId={} "
                     + "mockUrl={} actualCount={} expectedCount={} operator={} status={}",
                     context.executionId().value(), step.id().value(),
                     mockUrl, actualCount, expectedCount, operatorStr, status);

            return new AssertionResult(
                    step.id(),
                    status,
                    description,
                    evidence,
                    evalDuration,
                    Instant.now());

        } catch (IllegalArgumentException e) {
            log.warn("action=wiremock_assertion_param_error executionId={} assertionId={} error={}",
                     context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start, e.getMessage(), step.parameters());
        }
    }

    // --- WireMock HTTP call ---

    /**
     * Calls the WireMock admin API {@code /__admin/requests/count}
     * and returns the total count of received requests.
     * <p>
     * Executed under Virtual Thread to avoid blocking the platform thread pool.
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
                            "WireMock admin API returned HTTP " + response.statusCode()
                            + " for: " + url);
                }
                return extractCount(response.body());
            });
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(
                    "WireMock request count fetch interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException(
                    "WireMock request count fetch failed: " + e.getMessage(), e.getCause());
        }
    }

    /**
     * Extracts the {@code count} field from a WireMock JSON response body.
     * Expected format: {@code {"count": 12345}}.
     */
    private double extractCount(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("Empty response from WireMock admin API");
        }
        Matcher matcher = COUNT_PATTERN.matcher(json);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        throw new IllegalArgumentException(
                "Could not extract 'count' from WireMock response: "
                + json.substring(0, Math.min(200, json.length())));
    }

    // --- Parameter helpers ---

    private String getRequiredStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: '" + key + "'");
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
            throw new IllegalArgumentException("Missing required parameter: '" + key + "'");
        }
        if (value instanceof Number num) return num.doubleValue();
        throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a number, got: "
                + value.getClass().getSimpleName());
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String str && !str.isEmpty()) return str;
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

    // --- Result construction ---

    private AssertionResult buildErrorResult(StepDefinition step, Instant start,
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

    private String buildDescription(double actual, double expected,
                                     AssertionOperator operator, boolean passed) {
        String verdict = passed ? "PASSED" : "FAILED";
        return String.format("%s: WireMock received requests %.0f %s %.0f",
                verdict, actual, operatorSymbol(operator), expected);
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
