package com.performance.platform.infrastructure.executor.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.infrastructure.executor.http.HttpTargetRegistry;
import com.performance.platform.infrastructure.executor.http.HttpTargetProperties;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskExecutor for WireMock server lifecycle: start, stop, reset, and verify.
 * <p>
 * Supports two deployment modes:
 * <ul>
 *   <li>{@code EMBEDDED} — runs WireMock in-process on the specified port.</li>
 *   <li>{@code EXTERNAL} — interacts with a remote WireMock via its admin API.</li>
 * </ul>
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code deployment} — EMBEDDED (default) or EXTERNAL</li>
 *   <li>{@code port} — port number for embedded mode (default 8090)</li>
 *   <li>{@code mappingsPath} — optional path to stub mappings directory</li>
 *   <li>{@code action} — START, STOP, RESET, or VERIFY</li>
 *   <li>{@code target} — (recommended, EXTERNAL) HttpTargetRegistry target name</li>
 *   <li>{@code wiremockUrl} — (deprecated, EXTERNAL) direct WireMock admin URL</li>
 * </ul>
 * <p>
 * Outputs: {@code {port: 8090, url: "http://localhost:8090"}}.
 * For VERIFY, adds {@code {totalRequests: N}}.
 * <p>
 * Implements {@link StatefulResourceCleaner} to stop embedded servers on scenario restart.
 * <p>
 * <strong>CC-02 justification:</strong> This class exceeds 300 lines because it handles
 * 2 deployment modes (EMBEDDED, EXTERNAL) x 4 actions (START, STOP, RESET, VERIFY)
 * = 8 distinct operation paths, plus helpers, cleanup, and parameter extraction.
 * The two modes share the same conceptual domain (WireMock lifecycle) and extracting
 * EXTERNAL into a separate class would fragment cohesion without reducing overall
 * complexity — the split would require shared parameter extraction, error handling,
 * and output key constants to be duplicated or extracted to a third shared class.
 */
@Preparation(name = "mock-server", version = "1.1.0", description = "WireMock embedded/external with HttpTargetRegistry")
@Component
public class MockServerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(MockServerTaskExecutor.class);

    private static final int DEFAULT_PORT = 8090;
    private static final String DEPLOYMENT_EMBEDDED = "EMBEDDED";
    private static final String DEPLOYMENT_EXTERNAL = "EXTERNAL";
    private static final String ACTION_START = "START";
    private static final String ACTION_STOP = "STOP";
    private static final String ACTION_RESET = "RESET";
    private static final String ACTION_VERIFY = "VERIFY";

    static final String OUTPUT_PORT = "port";
    static final String OUTPUT_URL = "url";
    static final String OUTPUT_TOTAL_REQUESTS = "totalRequests";

    private static final String PARAM_DEPLOYMENT = "deployment";
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_PORT = "port";
    private static final String PARAM_MAPPINGS_PATH = "mappingsPath";
    private static final String PARAM_TARGET = "target";
    private static final String PARAM_WIREMOCK_URL = "wiremockUrl";

    private static final String DEFAULT_EXECUTION_KEY = "default";

    private final Map<String, WireMockServer> serversByExecution = new ConcurrentHashMap<>();
    private final HttpTargetRegistry targetRegistry;

    public MockServerTaskExecutor(HttpTargetRegistry targetRegistry) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry, "targetRegistry required");
    }

    @Override
    public String getSupportedTaskName() {
        return "mock-server";
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        String deployment = paramString(step, PARAM_DEPLOYMENT, DEPLOYMENT_EMBEDDED).toUpperCase(Locale.ROOT);
        String action = paramString(step, PARAM_ACTION, ACTION_START).toUpperCase(Locale.ROOT);

        if (!DEPLOYMENT_EMBEDDED.equals(deployment) && !DEPLOYMENT_EXTERNAL.equals(deployment)) {
            return fail(step, startNanos, "Invalid deployment mode: " + deployment
                    + " (expected EMBEDDED or EXTERNAL)");
        }

        try {
            if (DEPLOYMENT_EMBEDDED.equals(deployment)) {
                return executeEmbedded(step, action, startNanos, context.executionId());
            } else {
                return executeExternal(step, action, startNanos, context.executionId().value());
            }
        } catch (Exception e) {
            log.error("action=mock_server_error executionId={} deployment={} action={} stepId={}",
                    context.executionId().value(), deployment, action, step.id().value(), e);
            return fail(step, startNanos, e.getMessage(), e);
        }
    }

    // ─────────────────────────────── EMBEDDED ───────────────────────────────

    private TaskResult executeEmbedded(StepDefinition step, String action, long startNanos, ExecutionId executionId) {
        String executionKey = executionId != null ? executionId.value() : DEFAULT_EXECUTION_KEY;

        return switch (action) {
            case ACTION_START -> startEmbedded(step, startNanos, executionKey);
            case ACTION_STOP -> stopEmbedded(step, startNanos, executionKey);
            case ACTION_RESET -> resetEmbedded(step, startNanos, executionKey);
            case ACTION_VERIFY -> verifyEmbedded(step, startNanos, executionKey);
            default -> fail(step, startNanos, "Unknown action: " + action);
        };
    }

    private TaskResult startEmbedded(StepDefinition step, long startNanos, String executionKey) {
        int port = paramInt(step, PARAM_PORT, DEFAULT_PORT);
        String mappingsPath = paramString(step, PARAM_MAPPINGS_PATH, null);

        // Stop any previously running server for this execution key
        WireMockServer existing = serversByExecution.remove(executionKey);
        if (existing != null && existing.isRunning()) {
            log.info("action=mock_server_stop_previous executionKey={} port={}", executionKey, existing.port());
            existing.stop();
        }

        var config = WireMockConfiguration.wireMockConfig().port(port);
        if (mappingsPath != null && !mappingsPath.isBlank()) {
            config.usingFilesUnderDirectory(mappingsPath);
        }

        WireMockServer server = new WireMockServer(config);

        log.info("action=mock_server_start embedded=true port={} mappingsPath={} stepId={}",
                port, mappingsPath, step.id().value());

        server.start();
        int actualPort = server.port();
        String url = server.baseUrl();
        serversByExecution.put(executionKey, server);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        Map<String, Object> outputs = Map.of(
                OUTPUT_PORT, actualPort,
                OUTPUT_URL, url
        );

        log.info("action=mock_server_started port={} url={} duration={} stepId={}",
                actualPort, url, formatDuration(elapsed), step.id().value());

        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
    }

    private TaskResult stopEmbedded(StepDefinition step, long startNanos, String executionKey) {
        WireMockServer server = serversByExecution.remove(executionKey);
        if (server != null && server.isRunning()) {
            int port = server.port();
            server.stop();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            log.info("action=mock_server_stopped executionKey={} port={} duration={}",
                    executionKey, port, formatDuration(elapsed));
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, Map.of());
        }
        log.info("action=mock_server_stop_noop executionKey={}", executionKey);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, Map.of());
    }

    private TaskResult resetEmbedded(StepDefinition step, long startNanos, String executionKey) {
        WireMockServer server = serversByExecution.get(executionKey);
        if (server == null || !server.isRunning()) {
            return fail(step, startNanos, "No running embedded WireMock for executionKey=" + executionKey);
        }
        server.resetAll();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        log.info("action=mock_server_reset executionKey={} duration={}", executionKey, formatDuration(elapsed));
        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, Map.of());
    }

    private TaskResult verifyEmbedded(StepDefinition step, long startNanos, String executionKey) {
        WireMockServer server = serversByExecution.get(executionKey);
        if (server == null || !server.isRunning()) {
            return fail(step, startNanos, "No running embedded WireMock for executionKey=" + executionKey);
        }

        int port = server.port();
        String url = server.baseUrl();
        long count = server.countRequestsMatching(RequestPatternBuilder.allRequests().build()).getCount();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        Map<String, Object> outputs = Map.of(
                OUTPUT_PORT, port,
                OUTPUT_URL, url,
                OUTPUT_TOTAL_REQUESTS, count
        );

        log.info("action=mock_server_verify executionKey={} totalRequests={} duration={}",
                executionKey, count, formatDuration(elapsed));

        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
    }

    // ─────────────────────────────── EXTERNAL ───────────────────────────────

    /**
     * Resolves the external WireMock URL from parameters.
     * <p>Preferred: {@code target} parameter via {@link HttpTargetRegistry}.
     * Legacy (deprecated): {@code wiremockUrl} parameter with WARN log.
     *
     * @param step the step definition containing parameters
     * @return the resolved base URL, or {@code null} if neither parameter is provided
     * @throws IllegalArgumentException if the target name is unknown
     */
    private String resolveExternalUrl(StepDefinition step) {
        String targetName = paramString(step, PARAM_TARGET, null);
        if (targetName != null && !targetName.isBlank()) {
            HttpTargetProperties props = targetRegistry.get(targetName);
            if (props == null) {
                throw new IllegalArgumentException("Unknown http-target: " + targetName);
            }
            return props.baseUrl();
        }

        String wiremockUrl = paramString(step, PARAM_WIREMOCK_URL, null);
        if (wiremockUrl != null && !wiremockUrl.isBlank()) {
            log.warn("action=deprecated_param param=wiremockUrl stepId={} — use 'target:' instead",
                    step.id().value());
            return wiremockUrl;
        }

        return null;
    }

    private TaskResult executeExternal(StepDefinition step, String action, long startNanos, String executionId) {
        String externalUrl;
        try {
            externalUrl = resolveExternalUrl(step);
        } catch (IllegalArgumentException e) {
            log.warn("action=mock_server_unknown_target stepId={} message={}",
                    step.id().value(), e.getMessage());
            return fail(step, startNanos, e.getMessage());
        }

        if (externalUrl == null || externalUrl.isBlank()) {
            return fail(step, startNanos,
                    "Required parameter 'target' (or legacy 'wiremockUrl') is missing for EXTERNAL deployment");
        }

        String baseUrl = externalUrl.replaceAll("/+$", ""); // strip trailing slashes

        // Parse host and port from baseUrl for WireMock client
        String host;
        int port;
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            host = uri.getHost() != null ? uri.getHost() : "localhost";
            port = uri.getPort() >= 0 ? uri.getPort() : 8080;
        } catch (IllegalArgumentException e) {
            return fail(step, startNanos, "Invalid external URL: " + baseUrl + " — " + e.getMessage(), e);
        }

        return switch (action) {
            case ACTION_START -> verifyExternalReachable(step, host, port, baseUrl, startNanos, executionId);
            case ACTION_STOP -> TaskResult.success(step.id(), getSupportedTaskName(),
                    Duration.ofNanos(System.nanoTime() - startNanos), Map.of());
            case ACTION_RESET -> resetExternal(step, host, port, baseUrl, startNanos, executionId);
            case ACTION_VERIFY -> verifyExternalCount(step, host, port, baseUrl, startNanos, executionId);
            default -> fail(step, startNanos, "Unknown action: " + action);
        };
    }

    private TaskResult verifyExternalReachable(StepDefinition step, String host, int port, String baseUrl, long startNanos, String executionId) {
        WireMock.configureFor(host, port);
        try {
            var mappings = WireMock.listAllStubMappings();
            if (mappings == null) {
                return fail(step, startNanos, "External WireMock at " + baseUrl + " returned null response");
            }

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_PORT, port,
                    OUTPUT_URL, baseUrl
            );

            log.info("action=mock_server_external_verified executionId={} url={} stubCount={} duration={} stepId={}",
                    executionId, baseUrl, mappings.getMappings().size(), formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=mock_server_external_unreachable executionId={} url={} stepId={}",
                    executionId, baseUrl, step.id().value(), e);
            return fail(step, startNanos, "External WireMock at " + baseUrl + " is not reachable: " + e.getMessage(), e);
        }
    }

    private TaskResult resetExternal(StepDefinition step, String host, int port, String baseUrl, long startNanos, String executionId) {
        WireMock.configureFor(host, port);
        try {
            WireMock.resetAllRequests();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            log.info("action=mock_server_external_reset executionId={} url={} duration={} stepId={}",
                    executionId, baseUrl, formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, Map.of(OUTPUT_URL, baseUrl));

        } catch (Exception e) {
            log.error("action=mock_server_external_reset_failed executionId={} url={} stepId={}",
                    executionId, baseUrl, step.id().value(), e);
            return fail(step, startNanos, "Failed to reset external WireMock at " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    private TaskResult verifyExternalCount(StepDefinition step, String host, int port, String baseUrl, long startNanos, String executionId) {
        try {
            var adminClient = new HttpAdminClient(host, port);
            var countResult = adminClient.countRequestsMatching(RequestPatternBuilder.allRequests().build());
            long count = countResult.getCount();

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    OUTPUT_URL, baseUrl,
                    OUTPUT_TOTAL_REQUESTS, count
            );

            log.info("action=mock_server_external_verify executionId={} url={} totalRequests={} duration={} stepId={}",
                    executionId, baseUrl, count, formatDuration(elapsed), step.id().value());

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=mock_server_external_verify_failed executionId={} url={} stepId={}",
                    executionId, baseUrl, step.id().value(), e);
            return fail(step, startNanos, "Failed to verify external WireMock at " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────── CLEANUP ───────────────────────────────

    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=mock_server_cleanup_all activeServers={}", serversByExecution.size());
            serversByExecution.values().forEach(this::stopServer);
            serversByExecution.clear();
        } else {
            String key = executionId.value();
            WireMockServer server = serversByExecution.remove(key);
            if (server != null) {
                log.info("action=mock_server_cleanup executionId={}", key);
                stopServer(server);
            }
        }
    }

    private void stopServer(WireMockServer server) {
        try {
            if (server.isRunning()) {
                server.stop();
            }
        } catch (Exception e) {
            log.warn("action=mock_server_stop_error", e);
        }
    }

    // ─────────────────────────────── HELPERS ───────────────────────────────

    private static String paramString(StepDefinition step, String key, String defaultValue) {
        Object value = step.parameters().get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private static int paramInt(StepDefinition step, String key, int defaultValue) {
        Object value = step.parameters().get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return defaultValue;
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message, Throwable cause) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }
}
