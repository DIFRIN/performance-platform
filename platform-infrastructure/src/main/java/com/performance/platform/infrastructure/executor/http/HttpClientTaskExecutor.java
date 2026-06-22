package com.performance.platform.infrastructure.executor.http;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Preparation(name = "http-client", version = "1.0.0",
        description = "HTTP client for test preparation: health checks, resets, API seeding")
@Component
public class HttpClientTaskExecutor implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpClientTaskExecutor.class);

    static final String PARAM_TARGET = "target";
    static final String PARAM_METHOD = "method";
    static final String PARAM_PATH = "path";
    static final String PARAM_BODY = "body";
    static final String PARAM_EXPECTED_STATUS = "expectedStatus";
    static final String PARAM_CONTENT_TYPE = "contentType";

    static final String OUTPUT_STATUS_CODE = "statusCode";
    static final String OUTPUT_RESPONSE_BODY = "responseBody";
    static final String OUTPUT_DURATION_MS = "durationMs";

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_PATCH = "PATCH";

    private static final String DEFAULT_CONTENT_TYPE = "application/json";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final HttpTargetRegistry targetRegistry;

    public HttpClientTaskExecutor(HttpTargetRegistry targetRegistry) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry, "targetRegistry required");
    }

    @Override
    public String getSupportedTaskName() {
        return "http-client";
    }

    /**
     * <p><b>CC-02</b>: method ~47 lines - parameter extraction, target/path
     * validation, logical path resolution, RestClient retrieval, and Virtual
     * Thread dispatch with timeout guard form a single cohesive control-flow
     * unit that would lose clarity if split.</p>
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();

        String targetName = paramString(step, PARAM_TARGET, null);
        String method = paramString(step, PARAM_METHOD, METHOD_GET).toUpperCase();
        String logicalPath = paramString(step, PARAM_PATH, null);
        String body = paramString(step, PARAM_BODY, null);
        int expectedStatus = parseExpectedStatus(step);
        String contentType = paramString(step, PARAM_CONTENT_TYPE, DEFAULT_CONTENT_TYPE);

        if (targetName == null || targetName.isBlank()) {
            return fail(step, startNanos, "Required parameter 'target' is missing or blank");
        }
        if (logicalPath == null || logicalPath.isBlank()) {
            return fail(step, startNanos, "Required parameter 'path' is missing or blank");
        }

        String resolvedPath = targetRegistry.resolvePath(targetName, logicalPath);

        RestClient client;
        try {
            client = targetRegistry.clientFor(targetName);
        } catch (IllegalArgumentException e) {
            log.warn("action=http_unknown_target target={} executionId={}",
                    targetName, context.executionId().value());
            return fail(step, startNanos, "Unknown http-target: " + targetName);
        }

        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : DEFAULT_TIMEOUT_MS;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> executeRequest(
                    step, startNanos, client, method, resolvedPath,
                    body, contentType, expectedStatus, context.executionId().value()));
            return future.get(timeoutMs + 5_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("action=http_timeout method={} path={} executionId={} timeoutMs={}",
                    method, resolvedPath, context.executionId().value(), timeoutMs);
            return fail(step, startNanos, "HTTP request timed out after " + timeoutMs + "ms");
        } catch (Exception e) {
            log.error("action=http_unexpected_error method={} path={} executionId={}",
                    method, resolvedPath, context.executionId().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * <p><b>CC-02</b>: method ~57 lines - HTTP method switch, RestClient
     * .toEntity() call, 4xx/5xx capture, status/body extraction,
     * output assembly, expectedStatus assertion, and TaskResult return form
     * a single cohesive pipeline that would lose clarity if split.</p>
     */
    private TaskResult executeRequest(StepDefinition step, long startNanos,
                                      RestClient client, String method, String path,
                                      String body, String contentType, int expectedStatus,
                                      String executionId) {
        log.info("action=http_request method={} path={} target={} executionId={}",
                method, path, step.parameters().get(PARAM_TARGET), executionId);

        ResponseEntity<String> entity;

        try {
            entity = switch (method) {
                case METHOD_GET -> client.get().uri(path)
                        .retrieve().toEntity(String.class);
                case METHOD_DELETE -> client.delete().uri(path)
                        .retrieve().toEntity(String.class);
                case METHOD_POST -> client.post().uri(path)
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "")
                        .retrieve().toEntity(String.class);
                case METHOD_PUT -> client.put().uri(path)
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "")
                        .retrieve().toEntity(String.class);
                case METHOD_PATCH -> client.patch().uri(path)
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "")
                        .retrieve().toEntity(String.class);
                default -> throw new IllegalArgumentException(
                        "Unsupported HTTP method: " + method);
            };
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            entity = ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());
        }

        int status = entity.getStatusCode().value();
        String respBody = entity.getBody() != null ? entity.getBody() : "";
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        log.info("action=http_response statusCode={} durationMs={} executionId={}",
                status, elapsed.toMillis(), executionId);

        Map<String, Object> outputs = Map.of(
                OUTPUT_STATUS_CODE, status,
                OUTPUT_RESPONSE_BODY, respBody,
                OUTPUT_DURATION_MS, elapsed.toMillis()
        );

        if (expectedStatus != 0 && status != expectedStatus) {
            log.warn("action=http_status_mismatch expected={} actual={} executionId={}",
                    expectedStatus, status, executionId);
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Expected HTTP " + expectedStatus + " but got " + status, null);
        }

        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
    }

    private static int parseExpectedStatus(StepDefinition step) {
        Object val = step.parameters().get(PARAM_EXPECTED_STATUS);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String paramString(StepDefinition step, String key, String defaultValue) {
        Object value = step.parameters().get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }
}
