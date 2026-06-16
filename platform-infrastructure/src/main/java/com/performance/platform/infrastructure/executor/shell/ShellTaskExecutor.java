package com.performance.platform.infrastructure.executor.shell;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TaskExecutor for shell command execution.
 * <p>
 * Runs an external process with configurable command, arguments, working directory,
 * environment variables, and timeout. Captures stdout and stderr independently.
 * <p>
 * <strong>CC-02 justification:</strong> This class exceeds 300 lines because it
 * encapsulates the full lifecycle of an OS process — parameter extraction,
 * ProcessBuilder construction, concurrent stream reading (stdout/stderr on
 * Virtual Threads), timeout enforcement via {@code Process.waitFor(long, TimeUnit)},
 * exit-code-based success/failure logic, and stateful cleanup
 * ({@link StatefulResourceCleaner}). Extracting, for example, the stream-reading
 * block or the ProcessBuilder setup into separate classes would fragment a
 * naturally cohesive unit: these steps share tight coupling through the
 * {@code Process} object, the execution key, and the shared output/error
 * constants. The extracted methods ({@link #collectProcessOutputs},
 * {@link #buildProcessBuilder}, {@link #ShellParameters}) keep all methods
 * under 40 lines while preserving the single-responsibility nature of the class
 * ("execute a shell command end-to-end").
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code command} — required: the executable to run (e.g. {@code bash}, {@code python})</li>
 *   <li>{@code args} — optional: list of arguments passed to the command</li>
 *   <li>{@code workingDirectory} — optional: working directory for the process</li>
 *   <li>{@code env} — optional: map of environment variables to set</li>
 *   <li>{@code timeout} — optional: timeout in seconds (default 30)</li>
 *   <li>{@code successExitCodes} — optional: list of exit codes treated as success (default [0])</li>
 * </ul>
 * <p>
 * Outputs: {@code {exitCode: N, stdout: "...", stderr: "..."}} — present
 * for both success and failure (exit code outside {@code successExitCodes}).
 * <p>
 * Exit code not in {@code successExitCodes} produces {@link TaskResult#failed}.
 * <p>
 * Process I/O (stdout/stderr reading) runs under Virtual Threads.
 * The {@code timeout} parameter uses {@link Process#waitFor(long, TimeUnit)} natively
 * to kill the child process if it exceeds the limit.
 * <p>
 * Implements {@link StatefulResourceCleaner} to destroy running child processes
 * on scenario restart.
 */
@Preparation(name = "shell", description = "Shell command execution")
@Component
public class ShellTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(ShellTaskExecutor.class);

    // ── Parameter keys ────────────────────────────────────────────────────────

    static final String PARAM_COMMAND = "command";
    static final String PARAM_ARGS = "args";
    static final String PARAM_WORKING_DIRECTORY = "workingDirectory";
    static final String PARAM_ENV = "env";
    static final String PARAM_TIMEOUT = "timeout";
    static final String PARAM_SUCCESS_EXIT_CODES = "successExitCodes";

    // ── Output keys ───────────────────────────────────────────────────────────

    static final String OUTPUT_EXIT_CODE = "exitCode";
    static final String OUTPUT_STDOUT = "stdout";
    static final String OUTPUT_STDERR = "stderr";

    // ── Defaults ──────────────────────────────────────────────────────────────

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_SUCCESS_EXIT_CODE = 0;
    private static final String FALLBACK_EXECUTION_KEY = "default";

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<String, Process> processesByExecution = new ConcurrentHashMap<>();

    // ── Parameter extraction record ────────────────────────────────────────────

    /**
     * Resolved parameters from a {@link StepDefinition}, extracted once at the
     * start of {@link #execute} to avoid scattering parameter-access calls.
     */
    private record ShellParameters(
            String command,
            List<String> args,
            String workingDir,
            Map<String, String> env,
            long timeoutSeconds,
            List<Integer> successCodes) {

        static ShellParameters from(StepDefinition step) {
            return new ShellParameters(
                    paramString(step, PARAM_COMMAND, null),
                    parseStringList(step.parameters().get(PARAM_ARGS)),
                    paramString(step, PARAM_WORKING_DIRECTORY, null),
                    parseStringMap(step.parameters().get(PARAM_ENV)),
                    paramLong(step, PARAM_TIMEOUT, DEFAULT_TIMEOUT_SECONDS),
                    parseIntList(step.parameters().get(PARAM_SUCCESS_EXIT_CODES),
                            List.of(DEFAULT_SUCCESS_EXIT_CODE))
            );
        }
    }

    // ── TaskExecutor contract ─────────────────────────────────────────────────

    @Override
    public String getSupportedTaskName() {
        return "shell";
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        ShellParameters params = ShellParameters.from(step);

        if (params.command() == null || params.command().isBlank()) {
            return fail(step, startNanos, "Required parameter '" + PARAM_COMMAND + "' is missing or blank");
        }

        if (params.timeoutSeconds() <= 0) {
            return fail(step, startNanos, "timeout must be positive: " + params.timeoutSeconds() + "s");
        }

        ProcessBuilder pb = buildProcessBuilder(params);
        if (pb == null) {
            return fail(step, startNanos, "workingDirectory is not a directory: " + params.workingDir());
        }

        String executionKey = context.executionId() != null ? context.executionId().value() : FALLBACK_EXECUTION_KEY;

        // Run the entire process lifecycle on a Virtual Thread for non-blocking I/O.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.submit(() ->
                    executeProcess(pb, executionKey, params.command(), step, startNanos,
                            params.successCodes(), params.timeoutSeconds())
            ).get();
        } catch (Exception e) {
            log.error("action=shell_unexpected_error executionId={} command={} stepId={}",
                    executionKey, params.command(), step.id().value(), e);
            return fail(step, startNanos, "Unexpected error: " + e.getMessage(), e);
        }
    }

    // ── StatefulResourceCleaner contract ──────────────────────────────────────

    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=shell_cleanup_all activeProcesses={}", processesByExecution.size());
            processesByExecution.values().forEach(this::destroyProcess);
            processesByExecution.clear();
        } else {
            String key = executionId.value();
            Process process = processesByExecution.remove(key);
            if (process != null) {
                log.info("action=shell_cleanup executionId={}", key);
                destroyProcess(process);
            }
        }
    }

    // ── Core process execution (runs on Virtual Thread) ───────────────────────

    private TaskResult executeProcess(ProcessBuilder pb, String executionKey,
                                       String command, StepDefinition step,
                                       long startNanos, List<Integer> successCodes,
                                       long timeoutSeconds) {
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            log.error("action=shell_start_failed executionId={} command={} stepId={}",
                    executionKey, command, step.id().value(), e);
            return fail(step, startNanos, "Failed to start process: " + e.getMessage(), e);
        }

        processesByExecution.put(executionKey, process);

        ProcessOutput output;
        try {
            output = collectProcessOutputs(process, timeoutSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            processesByExecution.remove(executionKey);
            return fail(step, startNanos, "Process wait interrupted", e);
        }

        processesByExecution.remove(executionKey);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        if (output.timedOut()) {
            log.error("action=shell_timeout executionId={} command={} timeout={}s stepId={}",
                    executionKey, command, timeoutSeconds, step.id().value());
            return fail(step, startNanos, "Command timed out after " + timeoutSeconds + "s", null);
        }

        int exitCode = process.exitValue();

        log.info("action=shell_completed executionId={} command={} exitCode={} stdoutLength={} stderrLength={} duration={} stepId={}",
                executionKey, command, exitCode, output.stdout().length(), output.stderr().length(),
                formatDuration(elapsed), step.id().value());

        Map<String, Object> outputs = Map.of(
                OUTPUT_EXIT_CODE, exitCode,
                OUTPUT_STDOUT, output.stdout(),
                OUTPUT_STDERR, output.stderr()
        );

        if (successCodes.contains(exitCode)) {
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
        } else {
            // Include outputs even on failure so callers can inspect exitCode/stderr.
            return new TaskResult(step.id(), getSupportedTaskName(), TaskStatus.FAILED,
                    elapsed, outputs,
                    "Exit code " + exitCode + " not in success codes " + successCodes,
                    null, Instant.now());
        }
    }

    // ── Stream collection (extracted from executeProcess) ──────────────────────

    /**
     * Holds the result of concurrently reading stdout/stderr and waiting for
     * process completion.
     */
    private record ProcessOutput(String stdout, String stderr, boolean timedOut) {}

    /**
     * Reads stdout and stderr concurrently on Virtual Threads, waits for the
     * process to complete or timeout, and returns the captured output.
     * <p>
     * This block is extracted from {@link #executeProcess} to keep that method
     * focused on the overall process lifecycle (start, wait, build result).
     *
     * @param process        the started process
     * @param timeoutSeconds maximum time to wait for process completion
     * @return captured stdout, stderr, and whether the timeout was exceeded
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    private static ProcessOutput collectProcessOutputs(Process process, long timeoutSeconds)
            throws InterruptedException {
        String stdout;
        String stderr;
        boolean timedOut;
        try (var streamExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var outFuture = streamExecutor.submit(() -> readStream(process.getInputStream()));
            var errFuture = streamExecutor.submit(() -> readStream(process.getErrorStream()));

            // Native timeout on waitFor — reliable across all JVM thread models.
            timedOut = !process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (timedOut) {
                process.destroyForcibly();
                // Give streams a brief moment to flush any buffered data.
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Collect stream results — with a generous deadline since the
            // process has already terminated (or been killed).
            try {
                stdout = outFuture.get(5, TimeUnit.SECONDS);
                stderr = errFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                stdout = "";
                stderr = "Failed to read streams: " + e.getMessage();
            }
        }
        return new ProcessOutput(stdout, stderr, timedOut);
    }

    // ── Helpers: ProcessBuilder construction ───────────────────────────────────

    /**
     * Builds a {@link ProcessBuilder} from the resolved shell parameters.
     * <p>
     * Extracted from {@link #execute} to keep that method focused on validation
     * and orchestration rather than ProcessBuilder assembly details.
     *
     * @param params the resolved shell parameters
     * @return the configured ProcessBuilder, or {@code null} if the working
     *         directory does not exist or is not a directory
     */
    private static ProcessBuilder buildProcessBuilder(ShellParameters params) {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(params.command());
        if (params.args() != null) {
            fullCommand.addAll(params.args());
        }

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        if (params.workingDir() != null && !params.workingDir().isBlank()) {
            File dir = new File(params.workingDir());
            if (!dir.isDirectory()) {
                return null;
            }
            pb.directory(dir);
        }
        if (params.env() != null && !params.env().isEmpty()) {
            pb.environment().putAll(params.env());
        }
        pb.redirectErrorStream(false);
        return pb;
    }

    // ── Helpers: parameter extraction ─────────────────────────────────────────

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

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object rawValue) {
        if (rawValue instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static List<Integer> parseIntList(Object rawValue, List<Integer> defaultValue) {
        if (rawValue instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(obj -> {
                        if (obj instanceof Number n) {
                            return n.intValue();
                        }
                        return Integer.parseInt(obj.toString());
                    })
                    .toList();
        }
        return defaultValue;
    }

    // ── Helpers: stream reading & process cleanup ─────────────────────────────

    private static String readStream(java.io.InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void destroyProcess(Process process) {
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            log.warn("action=shell_destroy_error", e);
        }
    }

    // ── Helpers: result construction ──────────────────────────────────────────

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
