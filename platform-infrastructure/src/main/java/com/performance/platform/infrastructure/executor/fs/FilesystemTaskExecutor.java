package com.performance.platform.infrastructure.executor.fs;

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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskExecutor for filesystem operations: CREATE, DELETE, UPLOAD, and CLEANUP.
 * <p>
 * All I/O executes under Virtual Threads via the execution engine.
 * Implements {@link StatefulResourceCleaner} to delete created paths
 * on scenario restart.
 * <p>
 * Parameters:
 * <ul>
 *   <li>{@code operation} — CREATE, DELETE, UPLOAD, or CLEANUP</li>
 *   <li>{@code path} — target path (required)</li>
 *   <li>{@code source} — source path for UPLOAD (required for UPLOAD)</li>
 *   <li>{@code recursive} — delete directories recursively (default {@code false})</li>
 * </ul>
 * <p>
 * Outputs: {@code {path: "...", filesAffected: N}}.
 */
@Preparation(name = "filesystem", version = "1.0.0", description = "FS create/delete/upload/cleanup")
@Component
public class FilesystemTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(FilesystemTaskExecutor.class);

    // ── Parameter keys ────────────────────────────────────────────────────

    static final String PARAM_OPERATION = "operation";
    static final String PARAM_PATH = "path";
    static final String PARAM_SOURCE = "source";
    static final String PARAM_RECURSIVE = "recursive";

    // ── Output keys ───────────────────────────────────────────────────────

    static final String OUTPUT_PATH = "path";
    static final String OUTPUT_FILES_AFFECTED = "filesAffected";

    // ── Operation constants ───────────────────────────────────────────────

    private static final String OP_CREATE = "CREATE";
    private static final String OP_DELETE = "DELETE";
    private static final String OP_UPLOAD = "UPLOAD";
    private static final String OP_CLEANUP = "CLEANUP";

    private static final String FALLBACK_EXECUTION_KEY = "default";

    // ── State ─────────────────────────────────────────────────────────────

    /** Tracks created files/dirs per execution for cleanup. */
    private final Map<String, Set<Path>> pathsByExecution = new ConcurrentHashMap<>();

    // ── TaskExecutor contract ─────────────────────────────────────────────

    @Override
    public String getSupportedTaskName() {
        return "filesystem";
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();
        String operation = paramString(step, PARAM_OPERATION, OP_CREATE)
                .toUpperCase(Locale.ROOT);
        String pathStr = paramString(step, PARAM_PATH, null);

        if (pathStr == null || pathStr.isBlank()) {
            return fail(step, startNanos, "Required parameter 'path' is missing or blank");
        }
        Path path = Path.of(pathStr);
        String executionKey = context.executionId() != null
                ? context.executionId().value() : FALLBACK_EXECUTION_KEY;

        return switch (operation) {
            case OP_CREATE -> executeCreate(step, startNanos, path, executionKey);
            case OP_DELETE -> executeDelete(step, startNanos, path, executionKey);
            case OP_UPLOAD -> executeUpload(step, startNanos, path, executionKey);
            case OP_CLEANUP -> executeCleanup(step, startNanos, path, executionKey);
            default -> fail(step, startNanos,
                    "Unknown operation: " + operation +
                    " (expected CREATE, DELETE, UPLOAD, or CLEANUP)");
        };
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    private TaskResult executeCreate(StepDefinition step, long startNanos,
                                      Path path, String executionKey) {
        try {
            Path created = Files.createDirectories(path);
            pathsByExecution.computeIfAbsent(executionKey, k -> new HashSet<>())
                    .add(created);
            log.info("action=fs_create executionKey={} path={} stepId={}",
                    executionKey, created, step.id().value());

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_PATH, created.toString(), OUTPUT_FILES_AFFECTED, 1));
        } catch (IOException e) {
            log.error("action=fs_create_failed executionKey={} path={} stepId={}",
                    executionKey, path, step.id().value(), e);
            return fail(step, startNanos, "Failed to create directory: " + e.getMessage(), e);
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    private TaskResult executeDelete(StepDefinition step, long startNanos,
                                      Path path, String executionKey) {
        boolean recursive = paramBool(step, PARAM_RECURSIVE, false);

        try {
            if (!Files.exists(path)) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
                log.info("action=fs_delete_noop executionKey={} path={} stepId={}",
                        executionKey, path, step.id().value());
                return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                        Map.of(OUTPUT_PATH, path.toString(), OUTPUT_FILES_AFFECTED, 0));
            }

            int count;
            if (recursive && Files.isDirectory(path)) {
                count = deleteRecursively(path);
            } else {
                Files.delete(path);
                count = 1;
            }

            log.info("action=fs_delete executionKey={} path={} filesAffected={} recursive={} stepId={}",
                    executionKey, path, count, recursive, step.id().value());

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_PATH, path.toString(), OUTPUT_FILES_AFFECTED, count));
        } catch (IOException e) {
            log.error("action=fs_delete_failed executionKey={} path={} stepId={}",
                    executionKey, path, step.id().value(), e);
            return fail(step, startNanos, "Failed to delete: " + e.getMessage(), e);
        }
    }

    // ── UPLOAD ────────────────────────────────────────────────────────────

    private TaskResult executeUpload(StepDefinition step, long startNanos,
                                      Path destPath, String executionKey) {
        String sourceStr = paramString(step, PARAM_SOURCE, null);
        if (sourceStr == null || sourceStr.isBlank()) {
            return fail(step, startNanos, "Required parameter 'source' is missing or blank");
        }
        Path source = Path.of(sourceStr);

        try {
            Path parent = destPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, destPath, StandardCopyOption.REPLACE_EXISTING);
            pathsByExecution.computeIfAbsent(executionKey, k -> new HashSet<>())
                    .add(destPath);

            log.info("action=fs_upload executionKey={} source={} dest={} stepId={}",
                    executionKey, source, destPath, step.id().value());

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_PATH, destPath.toString(), OUTPUT_FILES_AFFECTED, 1));
        } catch (IOException e) {
            log.error("action=fs_upload_failed executionKey={} source={} dest={} stepId={}",
                    executionKey, source, destPath, step.id().value(), e);
            return fail(step, startNanos, "Failed to upload: " + e.getMessage(), e);
        }
    }

    // ── CLEANUP ───────────────────────────────────────────────────────────

    private TaskResult executeCleanup(StepDefinition step, long startNanos,
                                       Path path, String executionKey) {
        try {
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
                log.info("action=fs_cleanup_noop executionKey={} path={} stepId={}",
                        executionKey, path, step.id().value());
                return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                        Map.of(OUTPUT_PATH, path.toString(), OUTPUT_FILES_AFFECTED, 0));
            }

            int count = deleteDirectoryContents(path);

            log.info("action=fs_cleanup executionKey={} path={} filesAffected={} stepId={}",
                    executionKey, path, count, step.id().value());

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_PATH, path.toString(), OUTPUT_FILES_AFFECTED, count));
        } catch (IOException e) {
            log.error("action=fs_cleanup_failed executionKey={} path={} stepId={}",
                    executionKey, path, step.id().value(), e);
            return fail(step, startNanos, "Failed to cleanup: " + e.getMessage(), e);
        }
    }

    // ── StatefulResourceCleaner ───────────────────────────────────────────

    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=fs_cleanup_all executions={}", pathsByExecution.size());
            pathsByExecution.values().forEach(paths ->
                    paths.forEach(this::deleteQuietly));
            pathsByExecution.clear();
        } else {
            String key = executionId.value();
            Set<Path> paths = pathsByExecution.remove(key);
            if (paths != null) {
                log.info("action=fs_cleanup executionKey={} paths={}", key, paths.size());
                paths.forEach(this::deleteQuietly);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Deletes a file, or recursively deletes a directory tree. */
    private static int deleteRecursively(Path path) throws IOException {
        AtomicInteger count = new AtomicInteger();
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                count.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                count.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    /** Deletes all contents of a directory without removing the directory itself. */
    private static int deleteDirectoryContents(Path dir) throws IOException {
        AtomicInteger count = new AtomicInteger();
        try (var stream = Files.list(dir)) {
            stream.forEach(entry -> {
                try {
                    if (Files.isDirectory(entry)) {
                        count.addAndGet(deleteRecursively(entry));
                    } else {
                        Files.delete(entry);
                        count.incrementAndGet();
                    }
                } catch (IOException e) {
                    throw new UncheckedIoException(e);
                }
            });
        } catch (UncheckedIoException e) {
            throw e.getCause();
        }
        return count.get();
    }

    private void deleteQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) {
                deleteRecursively(path);
            } else if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.warn("action=fs_delete_quietly_failed path={}", path, e);
        }
    }

    // ── Parameter extraction ──────────────────────────────────────────────

    private static String paramString(StepDefinition step, String key, String defaultValue) {
        Object value = step.parameters().get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    private static boolean paramBool(StepDefinition step, String key, boolean defaultValue) {
        Object value = step.parameters().get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    // ── Result helpers ────────────────────────────────────────────────────

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message,
                            Throwable cause) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, cause);
    }

    // ── Internal exception ────────────────────────────────────────────────

    /** Wraps an IOException so it can propagate through a lambda. */
    private static class UncheckedIoException extends RuntimeException {
        UncheckedIoException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
