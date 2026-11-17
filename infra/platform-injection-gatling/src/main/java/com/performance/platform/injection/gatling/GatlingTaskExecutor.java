package com.performance.platform.injection.gatling;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.injection.gatling.http.HttpInjectionProtocol;
import com.performance.platform.injection.gatling.protocol.InjectionProtocol;
import com.performance.platform.injection.gatling.protocol.PreparedInjection;
import com.performance.platform.injection.gatling.result.GatlingResultParser;
import com.performance.platform.injection.gatling.result.ResultParsingException;
import com.performance.platform.injection.gatling.worker.InjectionWorkerLauncher;
import com.performance.platform.injection.gatling.worker.InjectionWorkerProperties;
import com.performance.platform.injection.gatling.worker.RunDirectory;
import com.performance.platform.injection.gatling.worker.WorkerHandle;
import com.performance.platform.injection.gatling.worker.WorkerOutcome;
import com.performance.platform.injection.gatling.worker.WorkerRunSpec;
import com.performance.platform.plugin.ExecutorAvailability;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link TaskExecutor} pour l'injection de charge Gatling.
 * <p>
 * Assemble {@link InjectionWorkerLauncher} (JVM fille par etape, ADR-066) et
 * {@link GatlingResultParser} (parsing du {@code stats.json}) pour
 * produire un {@link InjectionResult} encapsule dans le
 * {@code outputs} du {@link TaskResult} sous la cle {@value #OUTPUT_RESULT}.
 * <p>
 * <strong>Parametres de step :</strong>
 * <ul>
 *   <li>{@code simulation} — optionnel : classe de simulation Gatling custom. Absent =
 *       injection HTTP generique construite depuis le scenario (voir ci-dessous)</li>
 *   <li>{@code protocol} — optionnel (sans {@code simulation}) : {@code http} (defaut) ou {@code kafka}</li>
 *   <li>{@code loadModel} — obligatoire : map contenant {@code type} (String)
 *       et {@code parameters} (Map) definissant le modele de charge</li>
 *   <li>{@code timeout} — optionnel : timeout en secondes (defaut 300)</li>
 *   <li>{@code systemProperties} — optionnel : proprietes systeme passees a Gatling</li>
 * </ul>
 * <p>
 * <strong>Injection generee (sans {@code simulation}) :</strong> le param {@code protocol}
 * (defaut {@code http}) designe un {@link InjectionProtocol} (bean Spring, auto-decouvert) qui
 * construit la simulation depuis le step et la configuration de l'application :
 * {@code http} (target/baseUrl, method, path, pathParams, queryParams, headers, body, expectedStatus)
 * ou {@code kafka} (cluster, topic, key, headers, payload). Chaque valeur accepte les
 * placeholders dynamiques ({@code DynamicValues}) resolus a chaque requete/message.
 * <p>
 * <strong>StatefulResourceCleaner :</strong> maintient une trace des simulations
 * actives par executionId. Le {@code cleanup()} est best-effort (la simulation
 * Gatling etant synchrone dans {@code execute()}, il n'y a generalement pas
 * de simulation a arreter entre deux appels).
 * <p>
 * <strong>CC-02 (taille &gt; 300 lignes) :</strong> cette classe encapsule le cycle
 * de vie complet d'une injection Gatling — validation des parametres, extraction
 * et conversion du {@code LoadModel} (3 variantes : Map, LoadModel, LoadModelType),
 * construction du {@code GatlingRunConfig}, delegation au {@link GatlingRunner},
 * parsing des resultats via {@link GatlingResultParser}, assemblage du
 * {@link TaskResult}, gestion d'erreurs sur 3 niveaux de catch, et cleanup
 * stateful ({@link StatefulResourceCleaner}). L'extraction d'une etape en
 * classe separee fragmenterait une unite naturellement cohesive (l'execution
 * end-to-end d'une simulation Gatling) et creerait une dependance circulaire
 * ou du CodeSpaghetti a travers l'etat partage ({@code startNanos},
 * {@code executionKey}, parametres extraits).
 */
@Injection(name = "gatling", description = "Gatling load injection")
@Component
public class GatlingTaskExecutor implements TaskExecutor, StatefulResourceCleaner, ExecutorAvailability {

    private static final Logger log = LoggerFactory.getLogger(GatlingTaskExecutor.class);

    // ── Parameter keys ────────────────────────────────────────────────────────

    static final String PARAM_SIMULATION = "simulation";
    static final String PARAM_LOAD_MODEL = "loadModel";
    static final String PARAM_PROTOCOL = "protocol";
    static final String DEFAULT_PROTOCOL = "http";
    static final String PARAM_TIMEOUT = "timeout";
    static final String PARAM_SYSTEM_PROPERTIES = "systemProperties";

    // ── Output keys ───────────────────────────────────────────────────────────

    static final String OUTPUT_RESULT = InjectionResult.OUTPUT_KEY;

    // ── Defaults ──────────────────────────────────────────────────────────────

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final String FALLBACK_EXECUTION_KEY = "default";

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<String, List<WorkerHandle>> activeExecutions = new ConcurrentHashMap<>();

    /** Injections en cours d'execution (ADR-066, regle interimaire). */
    private final AtomicInteger runningInjections = new AtomicInteger();
    private final int maxConcurrent;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final InjectionWorkerLauncher launcher;
    private final Duration stopGrace;
    private final GatlingResultParser parser;
    private final Map<String, InjectionProtocol> protocols;

    /** Protocols are Spring beans discovered by name: adding one needs no change here. */
    @Autowired
    public GatlingTaskExecutor(InjectionWorkerLauncher launcher, GatlingResultParser parser,
                               ObjectProvider<InjectionProtocol> protocols,
                               ObjectProvider<InjectionWorkerProperties> workerProperties) {
        this(launcher, parser, protocols.orderedStream().toList(),
                workerProperties.getIfAvailable(InjectionWorkerProperties::defaults));
    }

    private GatlingTaskExecutor(InjectionWorkerLauncher launcher, GatlingResultParser parser,
                                List<InjectionProtocol> protocols, InjectionWorkerProperties props) {
        this(launcher, parser, protocols, props.maxConcurrent(), props.stopGrace());
    }

    /** HTTP-only executor (no target resolver), for custom simulations and standalone use. */
    public GatlingTaskExecutor(InjectionWorkerLauncher launcher, GatlingResultParser parser) {
        this(launcher, parser, List.of(new HttpInjectionProtocol((com.performance.platform.plugin.resource.EndpointResolver) null)));
    }

    GatlingTaskExecutor(InjectionWorkerLauncher launcher, GatlingResultParser parser,
                        List<InjectionProtocol> protocols) {
        this(launcher, parser, protocols, InjectionWorkerProperties.DEFAULT_MAX_CONCURRENT);
    }

    GatlingTaskExecutor(InjectionWorkerLauncher launcher, GatlingResultParser parser,
                        List<InjectionProtocol> protocols, int maxConcurrent) {
        this(launcher, parser, protocols, maxConcurrent, InjectionWorkerProperties.DEFAULT_STOP_GRACE);
    }

    GatlingTaskExecutor(InjectionWorkerLauncher launcher, GatlingResultParser parser,
                        List<InjectionProtocol> protocols, int maxConcurrent, Duration stopGrace) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.stopGrace = Objects.requireNonNull(stopGrace, "stopGrace must not be null");
        this.launcher = Objects.requireNonNull(launcher, "launcher must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        var byName = new LinkedHashMap<String, InjectionProtocol>();
        protocols.forEach(p -> byName.put(p.name(), p));
        this.protocols = Map.copyOf(byName);
    }

    // ── ExecutorAvailability contract ─────────────────────────────────────────

    @Override
    public boolean canAcceptNewTask() {
        return runningInjections.get() < maxConcurrent;
    }

    // ── TaskExecutor contract ─────────────────────────────────────────────────

    @Override
    public String getSupportedTaskName() {
        return "gatling";
    }

    /**
     * Pipeline d'execution Gatling en 5 etapes sequentielles coherentes
     * ({@code extract → build config → run → parse → assemble result}).
     * CC-02 : l'extraction en sous-methodes artificielles nuirait a la
     * lisibilite (toutes les etapes partagent les memes parametres extraits
     * et l'extraction fragmenterait un flux lineaire naturel).
     */
    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");

        long startNanos = System.nanoTime();

        // 1. Extraire les parametres obligatoires
        String simulation = paramString(step, PARAM_SIMULATION);
        boolean customSimulation = simulation != null && !simulation.isBlank();
        String executionKey = context.executionId() != null
                ? context.executionId().value() : FALLBACK_EXECUTION_KEY;

        InjectionProtocol protocol = null;
        if (customSimulation) {
            log.info("action=gatling_custom_simulation simulation={} stepId={}", simulation, step.id().value());
        } else {
            String protocolName = Objects.requireNonNullElse(paramString(step, PARAM_PROTOCOL), DEFAULT_PROTOCOL)
                    .trim().toLowerCase();
            protocol = protocols.get(protocolName);
            if (protocol == null) {
                return fail(step, startNanos, "Unsupported injection protocol '" + protocolName
                        + "' (available: " + protocols.keySet() + ")");
            }
        }

        LoadModel loadModel = extractLoadModel(step);
        if (loadModel == null) {
            return fail(step, startNanos,
                    "Required parameter '" + PARAM_LOAD_MODEL + "' is missing, invalid, "
                    + "or missing required 'type' field");
        }

        long timeoutSeconds = paramLong(step, PARAM_TIMEOUT, DEFAULT_TIMEOUT_SECONDS);
        if (timeoutSeconds <= 0) {
            return fail(step, startNanos,
                    "timeout must be positive: " + timeoutSeconds + "s");
        }

        Map<String, String> systemProperties = paramMap(step, PARAM_SYSTEM_PROPERTIES);

        // 2. Preparer l'injection du protocole (simulation generee) puis construire la configuration Gatling
        PreparedInjection prepared = null;
        if (protocol != null) {
            try {
                prepared = protocol.prepare(executionKey, step.id().value(), step.parameters());
            } catch (IllegalArgumentException e) {
                return fail(step, startNanos, e.getMessage());
            }
            simulation = prepared.simulationClass();
        }
        final String simulationClass = simulation;
        final PreparedInjection preparedInjection = prepared;

        if (runningInjections.incrementAndGet() > maxConcurrent) {
            runningInjections.decrementAndGet();
            if (prepared != null) prepared.close();
            log.warn("action=gatling_rejected executionId={} stepId={} maxConcurrent={}",
                    executionKey, step.id().value(), maxConcurrent);
            return fail(step, startNanos, "Injection concurrency limit reached (injection.worker.max-concurrent="
                    + maxConcurrent + ")");
        }
        WorkerHandle handle = null;
        try {
            // 3. Lancer le worker (JVM fille) et attendre sa sortie
            Path runDir = Files.createTempDirectory("gatling-" + step.id().value() + "-").toAbsolutePath().normalize();
            var spec = new WorkerRunSpec(runDir, step.id().value(), simulationClass, systemProperties,
                    Map.of(), Duration.ofSeconds(timeoutSeconds));
            new RunDirectory(runDir).writeInputs(spec, loadModel);
            log.info("action=gatling_execute_start executionId={} simulation={} stepId={}",
                    executionKey, simulationClass, step.id().value());
            handle = launcher.start(spec);
            activeExecutions.computeIfAbsent(executionKey, k -> new CopyOnWriteArrayList<>()).add(handle);
            WorkerOutcome outcome = handle.await();
            if (!outcome.completed()) {
                var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
                String reason = outcome.stoppedByPlatform()
                        ? "Gatling worker stopped by platform"
                        : "Gatling worker failed (exitCode=" + outcome.exitCode() + ")";
                log.error("action=gatling_execute_failed executionId={} stepId={} exitCode={}",
                        executionKey, step.id().value(), outcome.exitCode());
                return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                        "Gatling execution failed: " + reason + "\n" + outcome.logTail(), null);
            }
            Path gatlingResultsDir = outcome.resultsDirectory();
            log.info("action=gatling_execute_complete executionId={} resultsDir={} stepId={}",
                    executionKey, gatlingResultsDir, step.id().value());

            // 4. Parser les resultats
            InjectionResult injectionResult = parser.parse(gatlingResultsDir, step.id());
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            log.info("action=gatling_execute_success executionId={} totalRequests={} "
                    + "errorRate={}% duration={} stepId={}",
                    executionKey, injectionResult.totalRequests(),
                    String.format("%.2f", injectionResult.errorRate()),
                    formatDuration(elapsed), step.id().value());

            // 5. Assembler le TaskResult
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_RESULT, injectionResult));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (handle != null) handle.stop(stopGrace);
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Gatling execution interrupted", e);
        } catch (ResultParsingException e) {
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            log.error("action=gatling_parse_failed executionId={} stepId={} error={}",
                    executionKey, step.id().value(), e.getMessage());
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Result parsing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            log.error("action=gatling_unexpected_error executionId={} stepId={}",
                    executionKey, step.id().value(), e);
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Unexpected error: " + e.getMessage(), e);
        } finally {
            if (preparedInjection != null) preparedInjection.close();
            if (handle != null) {
                var list = activeExecutions.get(executionKey);
                if (list != null) {
                    list.remove(handle);
                    if (list.isEmpty()) activeExecutions.remove(executionKey, list);
                }
            }
            runningInjections.decrementAndGet();
        }
    }

    // ── StatefulResourceCleaner contract ──────────────────────────────────────

    /** {@inheritDoc} Arrete reellement les workers de l'execution (ADR-066). */
    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=gatling_cleanup_all activeExecutions={}", activeExecutions.size());
            activeExecutions.values().forEach(this::stopAll);
            activeExecutions.clear();
        } else {
            var handles = activeExecutions.remove(executionId.value());
            if (handles != null) {
                log.info("action=gatling_cleanup executionId={} workers={}", executionId.value(), handles.size());
                stopAll(handles);
            }
        }
    }

    private void stopAll(List<WorkerHandle> handles) {
        handles.forEach(h -> h.stop(stopGrace));
    }

    // ── Parameter extraction ──────────────────────────────────────────────────

    /**
     * Extrait le {@link LoadModel} depuis les parametres du step.
     * <p>
     * Format attendu dans {@code step.parameters()["loadModel"]} :
     * <pre>{@code
     * {
     *   "type": "CONSTANT",
     *   "parameters": {
     *     "users": 10,
     *     "duration": "60s"
     *   }
     * }
     * }</pre>
     *
     * @return le LoadModel extrait, ou {@code null} si absent/invalide
     */
    @SuppressWarnings("unchecked")
    private LoadModel extractLoadModel(StepDefinition step) {
        Object raw = step.parameters().get(PARAM_LOAD_MODEL);
        if (raw == null) return null;

        if (raw instanceof LoadModel lm) return lm;

        if (raw instanceof Map<?, ?> map) {
            Object typeObj = map.get("type");
            if (typeObj == null) return null;

            LoadModelType type;
            if (typeObj instanceof LoadModelType lmt) {
                type = lmt;
            } else {
                try {
                    type = LoadModelType.valueOf(typeObj.toString().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("action=invalid_load_model_type type={} stepId={}",
                            typeObj, step.id().value());
                    return null;
                }
            }

            Object paramsObj = map.get("parameters");
            Map<String, Object> params;
            if (paramsObj instanceof Map<?, ?> pm) {
                params = ((Map<String, Object>) pm);
            } else {
                params = Map.of();
            }

            return new LoadModel(type, params);
        }

        return null;
    }

    // ── Parameter helpers ─────────────────────────────────────────────────────

    private static String paramString(StepDefinition step, String key) {
        Object value = step.parameters().get(key);
        if (value == null) return null;
        return value.toString();
    }

    private static long paramLong(StepDefinition step, String key, long defaultValue) {
        Object value = step.parameters().get(key);
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static Map<String, String> paramMap(StepDefinition step, String key) {
        Object value = step.parameters().get(key);
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> e.getValue().toString()));
        }
        return Map.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }
}
