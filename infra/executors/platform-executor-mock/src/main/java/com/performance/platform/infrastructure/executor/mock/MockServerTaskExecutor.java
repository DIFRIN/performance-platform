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
import com.performance.platform.plugin.resource.Endpoint;
import com.performance.platform.plugin.resource.EndpointResolver;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.ExecutionCompletionCleaner;
import com.performance.platform.plugin.ServiceControl;
import com.performance.platform.plugin.ServiceTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 *   <li>{@code mappingsPath} — optional WireMock root directory (contains a
 *       {@code mappings/} sub-directory). Supports {@code classpath:} roots, in
 *       which case the mappings are copied to a temporary directory so that they
 *       also load from a Spring Boot executable JAR.</li>
 *   <li>{@code action} — START, STOP, RESET, or VERIFY</li>
 *   <li>{@code target} — (EXTERNAL) logical target name resolved by {@link EndpointResolver}</li>
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
@Preparation(name = "mock-server", version = "2.0.0", description = "WireMock service step (embedded/external) with EndpointResolver")
@Component
public class MockServerTaskExecutor implements ServiceTaskExecutor, ExecutionCompletionCleaner {

    private static final Logger log = LoggerFactory.getLogger(MockServerTaskExecutor.class);

    private static final int DEFAULT_PORT = 8090;
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String MAPPINGS_SUBDIR = "mappings";
    private static final String DEPLOYMENT_EMBEDDED = "EMBEDDED";
    private static final String DEPLOYMENT_EXTERNAL = "EXTERNAL";

    static final String OUTPUT_PORT = "port";
    static final String OUTPUT_URL = "url";
    static final String OUTPUT_TOTAL_REQUESTS = "totalRequests";
    static final String OUTPUT_RECEIVED_CALLS = "receivedCalls";

    private static final String PARAM_DEPLOYMENT = "deployment";
    private static final String PARAM_PORT = "port";
    private static final String PARAM_MAPPINGS_PATH = "mappingsPath";
    private static final String PARAM_TARGET = "target";

    private static final String DEFAULT_EXECUTION_KEY = "default";

    private final Map<String, WireMockServer> serversByExecution = new ConcurrentHashMap<>();
    private final Map<String, Path> tempMappingRoots = new ConcurrentHashMap<>();
    /** Nullable: absent when no http-target module is assembled (ADR-057). */
    private final EndpointResolver endpointResolver;

    /** Spring constructor: the resolver is optional, the mock module never requires the http module. */
    @Autowired
    public MockServerTaskExecutor(ObjectProvider<EndpointResolver> endpointResolver) {
        this(endpointResolver.getIfAvailable());
    }

    public MockServerTaskExecutor(EndpointResolver endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    @Override
    public String getSupportedTaskName() {
        return "mock-server";
    }

    /**
     * Le mock est une service step (ADR-054) : il n'y a plus d'actions
     * START/STOP dans les parametres. Une etape sans {@code during} est une
     * erreur de scenario.
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        return TaskResult.failed(step.id(), getSupportedTaskName(), Duration.ZERO,
                "mock-server is a service step: declare 'during: injection' or 'during: scenario'", null);
    }

    @Override
    public TaskResult executeService(ExecutionContext context, StepDefinition step, ServiceControl control) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(control, "control must not be null");

        long startNanos = System.nanoTime();
        String deployment = paramString(step, PARAM_DEPLOYMENT, DEPLOYMENT_EMBEDDED).toUpperCase(Locale.ROOT);

        if (!DEPLOYMENT_EMBEDDED.equals(deployment) && !DEPLOYMENT_EXTERNAL.equals(deployment)) {
            return fail(step, startNanos, "Invalid deployment mode: " + deployment
                    + " (expected EMBEDDED or EXTERNAL)");
        }

        try {
            return DEPLOYMENT_EMBEDDED.equals(deployment)
                    ? runEmbeddedService(step, startNanos, context.executionId(), control)
                    : runExternalService(step, startNanos, context.executionId().value(), control);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("action=mock_server_wait_interrupted executionId={} stepId={}",
                    context.executionId().value(), step.id().value(), e);
            return fail(step, startNanos, "Interrupted while waiting for embedded mock port", e);
        } catch (Exception e) {
            log.error("action=mock_server_error executionId={} deployment={} stepId={}",
                    context.executionId().value(), deployment, step.id().value(), e);
            return fail(step, startNanos, e.getMessage(), e);
        } finally {
            String executionKey = context.executionId() != null
                    ? context.executionId().value() : DEFAULT_EXECUTION_KEY;
            stopEmbeddedQuietly(executionKey);
        }
    }

    // ─────────────────────────────── EMBEDDED SERVICE ───────────────────────────────

    /**
     * Demarre le serveur, attend le STOP, puis publie le resultat (compteurs
     * inclus) qui sera lu par les assertions via {@code ref}.
     */
    private TaskResult runEmbeddedService(StepDefinition step, long startNanos, ExecutionId executionId,
                                          ServiceControl control) throws InterruptedException {
        String executionKey = executionId != null ? executionId.value() : DEFAULT_EXECUTION_KEY;

        TaskResult started = startEmbedded(step, startNanos, executionKey);
        if (started.status() != com.performance.platform.domain.task.TaskStatus.SUCCESS) {
            return started;
        }

        control.awaitStop();
        WireMockServer server = serversByExecution.get(executionKey);
        if (server == null || !server.isRunning()) {
            return fail(step, startNanos, "Embedded mock is no longer running");
        }

        int port = server.port();
        String url = server.baseUrl();
        long count = server.countRequestsMatching(RequestPatternBuilder.allRequests().build()).getCount();

        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        Map<String, Object> outputs = Map.of(
                OUTPUT_PORT, port,
                OUTPUT_URL, url,
                OUTPUT_TOTAL_REQUESTS, count,
                OUTPUT_RECEIVED_CALLS, count
        );

        log.info("action=mock_server_service_stopped executionKey={} port={} totalRequests={}",
                executionKey, port, count);
        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
    }

    private TaskResult runExternalService(StepDefinition step, long startNanos, String executionId,
                                          ServiceControl control) throws InterruptedException {
        String externalUrl;
        try {
            externalUrl = resolveExternalUrl(step);
        } catch (IllegalArgumentException e) {
            return fail(step, startNanos, e.getMessage());
        }
        if (externalUrl == null || externalUrl.isBlank()) {
            return fail(step, startNanos, "Required parameter 'target' is missing for EXTERNAL deployment");
        }

        String baseUrl = externalUrl.replaceAll("/+$", "");
        String host;
        int port;
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            host = uri.getHost() != null ? uri.getHost() : "localhost";
            port = uri.getPort() >= 0 ? uri.getPort() : 8080;
        } catch (IllegalArgumentException e) {
            return fail(step, startNanos, "Invalid external URL: " + baseUrl, e);
        }

        TaskResult started = verifyExternalReachable(step, host, port, baseUrl, startNanos, executionId);
        if (started.status() != com.performance.platform.domain.task.TaskStatus.SUCCESS) {
            return started;
        }

        control.awaitStop();
        return verifyExternalCount(step, host, port, baseUrl, startNanos, executionId);
    }

    private void stopEmbeddedQuietly(String executionKey) {
        WireMockServer server = serversByExecution.remove(executionKey);
        if (server != null && server.isRunning()) {
            stopServer(server);
        }
        deleteTempRoot(executionKey);
    }

    // ─────────────────────────────── EMBEDDED ───────────────────────────────

    private synchronized TaskResult startEmbedded(StepDefinition step, long startNanos, String executionKey)
            throws InterruptedException {
        int port = paramInt(step, PARAM_PORT, DEFAULT_PORT);
        String mappingsPath = paramString(step, PARAM_MAPPINGS_PATH, null);

        long remaining = step.timeout() != null ? step.timeout().toNanos() : Duration.ofSeconds(30).toNanos();
        long deadline = System.nanoTime() + remaining;
        while (port != 0 && serversByExecution.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(executionKey) && entry.getValue().isRunning()
                        && entry.getValue().port() == port)) {
            if (remaining <= 0) {
                return fail(step, startNanos, "Timed out waiting for embedded mock port " + port
                        + "; use the same metadata.executionGroup for scenarios sharing this port");
            }
            log.info("action=mock_server_wait_port executionId={} port={}", executionKey, port);
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
            remaining = deadline - System.nanoTime();
        }

        // Stop any previously running server for this execution key
        WireMockServer existing = serversByExecution.remove(executionKey);
        if (existing != null && existing.isRunning()) {
            log.info("action=mock_server_stop_previous executionKey={} port={}", executionKey, existing.port());
            existing.stop();
        }

        var config = WireMockConfiguration.wireMockConfig().port(port);
        if (mappingsPath != null && !mappingsPath.isBlank()) {
            MappingsRoot root = resolveMappingsRoot(mappingsPath);
            config.usingFilesUnderDirectory(root.path());
            if (root.tempDir() != null) {
                Path previous = tempMappingRoots.put(executionKey, root.tempDir());
                deleteRecursively(previous);
            }
        }

        var server = new WireMockServer(config);

        log.info("action=mock_server_start embedded=true port={} mappingsPath={} stepId={}",
                port, mappingsPath, step.id().value());

        try {
            server.start();
        } catch (RuntimeException e) {
            try {
                server.stop();
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            deleteTempRoot(executionKey);
            notifyAll();
            throw e;
        }
        int actualPort = server.port();
        String url = server.baseUrl();
        serversByExecution.put(executionKey, server);

        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        Map<String, Object> outputs = Map.of(
                OUTPUT_PORT, actualPort,
                OUTPUT_URL, url
        );

        log.info("action=mock_server_started port={} url={} duration={} stepId={}",
                actualPort, url, formatDuration(elapsed), step.id().value());

        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
    }

    // ─────────────────────────────── EXTERNAL ───────────────────────────────

    /**
     * Resolves the external WireMock URL from the {@code target} parameter via
     * {@link EndpointResolver}.
     *
     * @param step the step definition containing parameters
     * @return the resolved base URL, or {@code null} if no target is provided
     * @throws IllegalArgumentException if the target name is unknown
     */
    private String resolveExternalUrl(StepDefinition step) {
        String targetName = paramString(step, PARAM_TARGET, null);
        if (targetName == null || targetName.isBlank()) {
            return null;
        }
        if (endpointResolver == null) {
            throw new IllegalStateException("No EndpointResolver available to resolve http-target: " + targetName);
        }
        Endpoint endpoint = endpointResolver.resolve(targetName);
        if (endpoint == null) {
            throw new IllegalArgumentException("Unknown http-target: " + targetName);
        }
        return endpoint.baseUrl();
    }

    private TaskResult verifyExternalReachable(StepDefinition step, String host, int port, String baseUrl, long startNanos, String executionId) {
        WireMock.configureFor(host, port);
        try {
            var mappings = WireMock.listAllStubMappings();
            if (mappings == null) {
                return fail(step, startNanos, "External WireMock at " + baseUrl + " returned null response");
            }

            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
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
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

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

            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
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
    public synchronized void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=mock_server_cleanup_all activeServers={}", serversByExecution.size());
            serversByExecution.values().forEach(this::stopServer);
            serversByExecution.clear();
            tempMappingRoots.values().forEach(MockServerTaskExecutor::deleteRecursively);
            tempMappingRoots.clear();
        } else {
            String key = executionId.value();
            WireMockServer server = serversByExecution.remove(key);
            if (server != null) {
                log.info("action=mock_server_cleanup executionId={}", key);
                stopServer(server);
            }
            deleteTempRoot(key);
        }
        notifyAll();
    }

    private void deleteTempRoot(String executionKey) {
        deleteRecursively(tempMappingRoots.remove(executionKey));
    }

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("action=mock_server_temp_cleanup_failed path={}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("action=mock_server_temp_cleanup_failed path={}", root, e);
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

    /** WireMock root directory; {@code tempDir} is set only for classpath roots. */
    private record MappingsRoot(String path, Path tempDir) {
    }

    /**
     * Resolves the WireMock root directory passed as {@code mappingsPath}.
     * <p>
     * A plain filesystem path is used as-is. A {@code classpath:} root is
     * materialised into a temporary directory because WireMock's own
     * {@code usingFilesUnderClasspath} cannot list resources inside a Spring
     * Boot nested JAR.
     */
    private static MappingsRoot resolveMappingsRoot(String mappingsPath) {
        if (!mappingsPath.startsWith(CLASSPATH_PREFIX)) {
            return new MappingsRoot(mappingsPath, null);
        }
        String classpathRoot = mappingsPath.substring(CLASSPATH_PREFIX.length())
                .replaceAll("/+$", "");
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(
                    "classpath*:" + classpathRoot + "/" + MAPPINGS_SUBDIR + "/*");
            if (resources.length == 0) {
                throw new IllegalArgumentException(
                        "No mappings found on classpath under "
                                + classpathRoot + "/" + MAPPINGS_SUBDIR);
            }

            Path root = Files.createTempDirectory("perf-mock-mappings-");
            Path mappingsDir = Files.createDirectories(root.resolve(MAPPINGS_SUBDIR));
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, mappingsDir.resolve(filename),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return new MappingsRoot(root.toString(), root);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to load classpath mappings from " + classpathRoot, e);
        }
    }

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
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message, Throwable cause) {
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }
}
