package com.performance.platform.injection.gatling.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lance le worker via {@link ProcessBuilder} (ADR-066) : meme binaire Java, classpath du parent
 * + repertoire plugins (ADR-007), stdout/stderr redirige vers {@code worker.log} du run directory.
 * Les secrets ({@link WorkerRunSpec#environment()}) ne transitent que par l'environnement du fils.
 */
@Component
public class ProcessInjectionWorkerLauncher implements InjectionWorkerLauncher, DisposableBean {

    public static final String LOG_FILE = "worker.log";
    public static final int LOG_TAIL_LINES = 50;
    /** Marge accordee au worker au-dela de son propre timeout avant arret force. */
    static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(ProcessInjectionWorkerLauncher.class);

    private final InjectionWorkerProperties properties;
    private final Path pluginsDir;
    private final String javaBinary;
    private final String classpath;
    private final Set<ProcessWorkerHandle> live = ConcurrentHashMap.newKeySet();

    @Autowired
    public ProcessInjectionWorkerLauncher(InjectionWorkerProperties properties,
                                          @Value("${platform.plugins.dir:./plugins}") String pluginsDir) {
        this(properties, pluginsDir == null || pluginsDir.isBlank() ? null : Path.of(pluginsDir),
                ProcessHandle.current().info().command().orElse("java"),
                System.getProperty("java.class.path", ""));
    }

    ProcessInjectionWorkerLauncher(InjectionWorkerProperties properties, Path pluginsDir,
                                   String javaBinary, String classpath) {
        this.properties = properties;
        this.pluginsDir = pluginsDir;
        this.javaBinary = javaBinary;
        this.classpath = classpath;
    }

    @Override
    public WorkerHandle start(WorkerRunSpec spec) {
        return start(spec, InjectionWorkerMain.class.getName());
    }

    WorkerHandle start(WorkerRunSpec spec, String mainClass) {
        Path logFile = spec.runDirectory().resolve(LOG_FILE);
        var builder = new ProcessBuilder(command(spec, mainClass))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
        builder.environment().putAll(spec.environment());
        try {
            Process process = builder.start();
            log.info("action=worker_started simulationId={} pid={} runDirectory={}",
                    spec.simulationId(), process.pid(), spec.runDirectory());
            var handle = new ProcessWorkerHandle(process, spec, logFile);
            live.add(handle);
            process.onExit().thenRun(() -> live.remove(handle));
            return handle;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start injection worker: " + e.getMessage(), e);
        }
    }

    /** Arrete tous les workers vivants a l'arret de l'agent (ADR-066). */
    @Override
    public void destroy() {
        stopAllWorkers();
    }

    public void stopAllWorkers() {
        var grace = properties.stopGrace();
        live.forEach(h -> h.stop(grace));
    }

    List<String> command(WorkerRunSpec spec, String mainClass) {
        var cmd = new ArrayList<String>();
        cmd.add(javaBinary);
        cmd.addAll(properties.jvmArgs());
        spec.systemProperties().forEach((k, v) -> cmd.add("-D" + k + "=" + v));
        cmd.add("-cp");
        cmd.add(childClasspath());
        cmd.add(mainClass);
        cmd.add(spec.runDirectory().toString());
        return cmd;
    }

    private String childClasspath() {
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) return classpath;
        var cp = new StringBuilder(classpath);
        try (var jars = Files.list(pluginsDir)) {
            jars.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted()
                    .forEach(p -> cp.append(java.io.File.pathSeparator).append(p.toAbsolutePath()));
        } catch (IOException e) {
            log.warn("action=worker_plugins_dir_unreadable dir={} error={}", pluginsDir, e.getMessage());
        }
        return cp.toString();
    }

    static String tail(Path logFile, int maxLines) {
        Deque<String> lines = new ArrayDeque<>();
        try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == maxLines) lines.removeFirst();
                lines.addLast(line);
            }
        } catch (IOException e) {
            return "";
        }
        return String.join("\n", lines);
    }

    private final class ProcessWorkerHandle implements WorkerHandle {
        private final Process process;
        private final WorkerRunSpec spec;
        private final Path logFile;
        private final AtomicBoolean stopped = new AtomicBoolean();

        ProcessWorkerHandle(Process process, WorkerRunSpec spec, Path logFile) {
            this.process = process;
            this.spec = spec;
            this.logFile = logFile;
        }

        @Override
        public WorkerOutcome await() throws InterruptedException {
            if (!spec.timeout().isZero()) {
                Duration limit = spec.timeout().plus(TIMEOUT_MARGIN);
                if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("action=worker_timeout simulationId={} limit={}", spec.simulationId(), limit);
                    stop(properties.stopGrace());
                }
            }
            int exit = process.waitFor();
            var runDir = new RunDirectory(spec.runDirectory());
            boolean byPlatform = stopped.get();
            Path results = runDir.resultsDirectory();
            var health = com.performance.platform.domain.injection.GeneratorHealth.unknown();
            if (Files.isRegularFile(runDir.resultFile())) {
                try {
                    var written = runDir.readResult();
                    health = written.generatorHealth();
                    if (written.resultsDirectory() != null) results = written.resultsDirectory();
                } catch (IOException | RuntimeException e) {
                    log.warn("action=worker_result_unreadable simulationId={} error={}",
                            spec.simulationId(), e.getMessage());
                }
            }
            log.info("action=worker_exited simulationId={} exitCode={} stoppedByPlatform={}",
                    spec.simulationId(), exit, byPlatform);
            return new WorkerOutcome(exit, exit == 0 && !byPlatform, byPlatform, results,
                    runDir.resultFile(), tail(logFile, LOG_TAIL_LINES), health);
        }

        @Override
        public void stop(Duration grace) {
            if (!process.isAlive()) return;
            stopped.set(true);
            process.destroy();
            boolean forced = false;
            try {
                if (!process.waitFor(grace.toMillis(), TimeUnit.MILLISECONDS)) {
                    forced = true;
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                forced = true;
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("action=worker_stopped executionId={} taskId={} forced={}",
                    "n/a", spec.simulationId(), forced);
        }

        @Override
        public boolean alive() {
            return process.isAlive();
        }
    }
}
