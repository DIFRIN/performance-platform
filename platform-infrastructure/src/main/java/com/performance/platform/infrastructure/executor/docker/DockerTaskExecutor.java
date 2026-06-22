package com.performance.platform.infrastructure.executor.docker;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskExecutor for Docker container lifecycle: START, STOP, and PULL.
 * <p>
 * <strong>CC-02 justification:</strong> This class exceeds 300 lines because it
 * handles 3 distinct actions (START, STOP, PULL) each with its own parameter
 * extraction, error handling, output mapping, and logging — plus
 * {@link StatefulResourceCleaner} implementation, health-check polling,
 * and safe-stop semantics. Extracting individual actions into separate classes
 * would fragment the {@code "docker"} task name into multiple executors or
 * require a shared parameter-resolution helper that duplicates the
 * {@link StartParams} pattern. All methods remain under 40 lines through
 * the {@code StartParams} record extraction and focused helper methods
 * ({@link #waitForRunning}, {@link #stopSafely}).
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code action} — START, STOP, or PULL (case-insensitive)</li>
 *   <li>{@code image} — Docker image name (required for START and PULL)</li>
 *   <li>{@code containerName} — optional container name</li>
 *   <li>{@code ports} — optional map of host-to-container port mappings
 *       (e.g. {@code {"8080": "80"}})</li>
 *   <li>{@code env} — optional map of environment variables</li>
 *   <li>{@code waitForHealthCheck} — whether to poll until the container
 *       reports {@code running} (default {@code false})</li>
 *   <li>{@code healthCheckTimeout} — max seconds to wait for healthy status
 *       (default 30, only relevant when {@code waitForHealthCheck=true})</li>
 * </ul>
 * <p>
 * Outputs for START: {@code {containerId: "...", status: "running"}}.
 * Outputs for STOP: {@code {containerId: "...", status: "stopped"}}.
 * Outputs for PULL: {@code {image: "...", status: "pulled"}}.
 * <p>
 * Implements {@link StatefulResourceCleaner} to stop containers started
 * by a given execution on scenario restart.
 */
@Preparation(name = "docker", version = "1.0.0", description = "Docker start/stop/pull")
@Component
public class DockerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(DockerTaskExecutor.class);

    // ── Parameter keys ────────────────────────────────────────────────────

    static final String PARAM_ACTION = "action";
    static final String PARAM_IMAGE = "image";
    static final String PARAM_CONTAINER_NAME = "containerName";
    static final String PARAM_PORTS = "ports";
    static final String PARAM_ENV = "env";
    static final String PARAM_WAIT_FOR_HEALTH_CHECK = "waitForHealthCheck";
    static final String PARAM_HEALTH_CHECK_TIMEOUT = "healthCheckTimeout";

    // ── Output keys ───────────────────────────────────────────────────────

    static final String OUTPUT_CONTAINER_ID = "containerId";
    static final String OUTPUT_STATUS = "status";
    static final String OUTPUT_IMAGE = "image";

    // ── Action constants ──────────────────────────────────────────────────

    private static final String ACTION_START = "START";
    private static final String ACTION_STOP = "STOP";
    private static final String ACTION_PULL = "PULL";

    // ── Defaults ──────────────────────────────────────────────────────────

    private static final long DEFAULT_HEALTH_CHECK_TIMEOUT_SECONDS = 30;
    private static final long HEALTH_CHECK_POLL_INTERVAL_MS = 500;
    private static final String FALLBACK_EXECUTION_KEY = "default";

    // ── Dependencies & state ──────────────────────────────────────────────

    private final DockerClient dockerClient;

    /** Tracks container IDs per execution for cleanup. */
    private final Map<String, Set<String>> containersByExecution = new ConcurrentHashMap<>();

    public DockerTaskExecutor(DockerClient dockerClient) {
        this.dockerClient = Objects.requireNonNull(dockerClient, "dockerClient must not be null");
    }

    // ── TaskExecutor contract ─────────────────────────────────────────────

    @Override
    public String getSupportedTaskName() {
        return "docker";
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        String action = paramString(step, PARAM_ACTION, ACTION_START)
                .toUpperCase(Locale.ROOT);
        String executionKey = context.executionId() != null
                ? context.executionId().value() : FALLBACK_EXECUTION_KEY;

        return switch (action) {
            case ACTION_START -> executeStart(step, startNanos, executionKey);
            case ACTION_STOP -> executeStop(step, startNanos, executionKey);
            case ACTION_PULL -> executePull(step, startNanos);
            default -> fail(step, startNanos,
                    "Unknown action: " + action + " (expected START, STOP, or PULL)");
        };
    }

    // ── START action ──────────────────────────────────────────────────────

    /**
     * Resolved parameters for the START action, extracted once to keep
     * {@link #executeStart} focused on orchestration.
     */
    private record StartParams(String image, String containerName,
                               Map<String, String> ports, Map<String, String> env,
                               boolean waitForHealth, long healthTimeout) {

        static StartParams from(StepDefinition step) {
            return new StartParams(
                    paramString(step, PARAM_IMAGE, null),
                    paramString(step, PARAM_CONTAINER_NAME, null),
                    parseStringMap(step.parameters().get(PARAM_PORTS)),
                    parseStringMap(step.parameters().get(PARAM_ENV)),
                    paramBool(step, PARAM_WAIT_FOR_HEALTH_CHECK, false),
                    paramLong(step, PARAM_HEALTH_CHECK_TIMEOUT,
                            DEFAULT_HEALTH_CHECK_TIMEOUT_SECONDS));
        }

        boolean hasImage() {
            return image != null && !image.isBlank();
        }
    }

    private TaskResult executeStart(StepDefinition step, long startNanos,
                                     String executionKey) {
        var p = StartParams.from(step);
        if (!p.hasImage()) {
            return fail(step, startNanos, "Required parameter 'image' is missing or blank");
        }

        String containerId;
        try {
            containerId = dockerClient.runContainer(p.image(), p.containerName(),
                    p.ports(), p.env());
        } catch (DockerException e) {
            log.error("action=docker_start_failed executionKey={} image={} stepId={}",
                    executionKey, p.image(), step.id().value(), e);
            return fail(step, startNanos, "Failed to start container: " + e.getMessage(), e);
        }

        containersByExecution.computeIfAbsent(executionKey, k -> new HashSet<>())
                .add(containerId);

        if (p.waitForHealth() && !waitForRunning(containerId, p.healthTimeout())) {
            log.error("action=docker_health_check_failed containerId={} timeout={}s stepId={}",
                    containerId, p.healthTimeout(), step.id().value());
            return fail(step, startNanos,
                    "Container did not become healthy within " + p.healthTimeout() + "s");
        }

        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        log.info("action=docker_start_complete containerId={} image={} executionKey={} duration={} stepId={}",
                containerId, p.image(), executionKey, formatDuration(elapsed), step.id().value());

        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                Map.of(OUTPUT_CONTAINER_ID, containerId, OUTPUT_STATUS, "running"));
    }

    // ── STOP action ───────────────────────────────────────────────────────

    private TaskResult executeStop(StepDefinition step, long startNanos,
                                    String executionKey) {
        String containerId = paramString(step, OUTPUT_CONTAINER_ID, null);

        // If no containerId specified, stop all containers for this execution
        if (containerId == null || containerId.isBlank()) {
            Set<String> ids = containersByExecution.remove(executionKey);
            if (ids == null || ids.isEmpty()) {
                var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
                log.info("action=docker_stop_noop executionKey={}", executionKey);
                return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, Map.of());
            }
            for (String id : ids) {
                stopSafely(id);
            }
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_STATUS, "stopped"));
        }

        // Stop specific container
        stopSafely(containerId);
        Set<String> ids = containersByExecution.get(executionKey);
        if (ids != null) {
            ids.remove(containerId);
        }

        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                Map.of(OUTPUT_CONTAINER_ID, containerId, OUTPUT_STATUS, "stopped"));
    }

    // ── PULL action ───────────────────────────────────────────────────────

    private TaskResult executePull(StepDefinition step, long startNanos) {
        String image = paramString(step, PARAM_IMAGE, null);
        if (image == null || image.isBlank()) {
            return fail(step, startNanos, "Required parameter 'image' is missing or blank");
        }

        try {
            dockerClient.pullImage(image);
        } catch (DockerException e) {
            log.error("action=docker_pull_failed image={} stepId={}",
                    image, step.id().value(), e);
            return fail(step, startNanos, "Failed to pull image: " + e.getMessage(), e);
        }

        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        log.info("action=docker_pull_complete image={} duration={} stepId={}",
                image, formatDuration(elapsed), step.id().value());

        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                Map.of(OUTPUT_IMAGE, image, OUTPUT_STATUS, "pulled"));
    }

    // ── StatefulResourceCleaner contract ──────────────────────────────────

    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=docker_cleanup_all executions={}", containersByExecution.size());
            containersByExecution.values().forEach(ids ->
                    ids.forEach(this::stopSafely));
            containersByExecution.clear();
        } else {
            String key = executionId.value();
            Set<String> ids = containersByExecution.remove(key);
            if (ids != null) {
                log.info("action=docker_cleanup executionKey={} containers={}", key, ids.size());
                ids.forEach(this::stopSafely);
            }
        }
    }

    // ── Health check helper ───────────────────────────────────────────────

    private boolean waitForRunning(String containerId, long timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (dockerClient.isRunning(containerId)) {
                return true;
            }
            try {
                Thread.sleep(HEALTH_CHECK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ── Safe stop ─────────────────────────────────────────────────────────

    private void stopSafely(String containerId) {
        try {
            dockerClient.stopContainer(containerId);
        } catch (DockerException e) {
            log.warn("action=docker_stop_error containerId={}", containerId, e);
        }
    }

    // ── Parameter extraction helpers ──────────────────────────────────────

    private static String paramString(StepDefinition step, String key, String defaultValue) {
        Object value = step.parameters().get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private static long paramLong(StepDefinition step, String key, long defaultValue) {
        Object value = step.parameters().get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return defaultValue;
    }

    private static boolean paramBool(StepDefinition step, String key, boolean defaultValue) {
        Object value = step.parameters().get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private static Map<String, String> parseStringMap(Object rawValue) {
        if (rawValue instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> e.getValue().toString()));
        }
        return Map.of();
    }

    // ── Result helpers ────────────────────────────────────────────────────

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message,
                            Throwable cause) {
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }
}
