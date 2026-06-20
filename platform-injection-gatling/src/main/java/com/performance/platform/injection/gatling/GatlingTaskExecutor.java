package com.performance.platform.injection.gatling;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.injection.gatling.result.GatlingResultParser;
import com.performance.platform.injection.gatling.result.ResultParsingException;
import com.performance.platform.injection.gatling.runner.GatlingExecutionException;
import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import com.performance.platform.injection.gatling.runner.GatlingRunner;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.plugin.TaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link TaskExecutor} pour l'injection de charge Gatling.
 * <p>
 * Assemble {@link GatlingRunner} (lancement de la simulation) et
 * {@link GatlingResultParser} (parsing du {@code stats.json}) pour
 * produire un {@link InjectionResult} encapsule dans le
 * {@code outputs} du {@link TaskResult} sous la cle {@value #OUTPUT_RESULT}.
 * <p>
 * <strong>Parametres de step :</strong>
 * <ul>
 *   <li>{@code simulation} — obligatoire : la classe de simulation Gatling</li>
 *   <li>{@code loadModel} — obligatoire : map contenant {@code type} (String)
 *       et {@code parameters} (Map) definissant le modele de charge</li>
 *   <li>{@code timeout} — optionnel : timeout en secondes (defaut 300)</li>
 *   <li>{@code systemProperties} — optionnel : proprietes systeme passees a Gatling</li>
 * </ul>
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
public class GatlingTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(GatlingTaskExecutor.class);

    // ── Parameter keys ────────────────────────────────────────────────────────

    static final String PARAM_SIMULATION = "simulation";
    static final String PARAM_LOAD_MODEL = "loadModel";
    static final String PARAM_TIMEOUT = "timeout";
    static final String PARAM_SYSTEM_PROPERTIES = "systemProperties";

    // ── Output keys ───────────────────────────────────────────────────────────

    static final String OUTPUT_RESULT = "result";

    // ── Defaults ──────────────────────────────────────────────────────────────

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final String FALLBACK_EXECUTION_KEY = "default";

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<String, Boolean> activeExecutions = new ConcurrentHashMap<>();

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final GatlingRunner runner;
    private final GatlingResultParser parser;

    public GatlingTaskExecutor(GatlingRunner runner, GatlingResultParser parser) {
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
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
        if (simulation == null || simulation.isBlank()) {
            return fail(step, startNanos,
                    "Required parameter '" + PARAM_SIMULATION + "' is missing or blank");
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

        Path resultsDir = buildResultsDir(step);

        String executionKey = context.executionId() != null
                ? context.executionId().value() : FALLBACK_EXECUTION_KEY;

        // 2. Construire la configuration Gatling
        GatlingRunConfig config = new GatlingRunConfig(
                simulation,
                loadModel,
                systemProperties,
                resultsDir,
                step.id().value(),
                Duration.ofSeconds(timeoutSeconds)
        );

        activeExecutions.put(executionKey, true);
        try {
            // 3. Lancer la simulation Gatling
            log.info("action=gatling_execute_start executionId={} simulation={} stepId={}",
                    executionKey, simulation, step.id().value());
            Path gatlingResultsDir = runner.run(config);
            log.info("action=gatling_execute_complete executionId={} resultsDir={} stepId={}",
                    executionKey, gatlingResultsDir, step.id().value());

            // 4. Parser les resultats
            InjectionResult injectionResult = parser.parse(gatlingResultsDir, step.id());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            log.info("action=gatling_execute_success executionId={} totalRequests={} "
                    + "errorRate={}% duration={} stepId={}",
                    executionKey, injectionResult.totalRequests(),
                    String.format("%.2f", injectionResult.errorRate()),
                    formatDuration(elapsed), step.id().value());

            // 5. Assembler le TaskResult
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed,
                    Map.of(OUTPUT_RESULT, injectionResult));

        } catch (GatlingExecutionException e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            log.error("action=gatling_execute_failed executionId={} stepId={} error={}",
                    executionKey, step.id().value(), e.getMessage());
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Gatling execution failed: " + e.getMessage(), e);
        } catch (ResultParsingException e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            log.error("action=gatling_parse_failed executionId={} stepId={} error={}",
                    executionKey, step.id().value(), e.getMessage());
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Result parsing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            log.error("action=gatling_unexpected_error executionId={} stepId={}",
                    executionKey, step.id().value(), e);
            return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                    "Unexpected error: " + e.getMessage(), e);
        } finally {
            activeExecutions.remove(executionKey);
        }
    }

    // ── StatefulResourceCleaner contract ──────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * L'execution Gatling etant synchrone (bloquante dans {@link #execute}),
     * il n'y a generalement pas de simulation active a arreter entre deux appels.
     * Le cleanup est best-effort et logge les executions orphelines eventuelles.
     */
    @Override
    public void cleanup(ExecutionId executionId) {
        if (executionId == null) {
            log.info("action=gatling_cleanup_all activeExecutions={}",
                    activeExecutions.size());
            activeExecutions.clear();
        } else {
            String key = executionId.value();
            if (activeExecutions.remove(key) != null) {
                log.info("action=gatling_cleanup executionId={}", key);
            }
        }
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

    /**
     * Resout le repertoire de resultats Gatling.
     * Cree un repertoire temporaire si non specifie.
     */
    private Path buildResultsDir(StepDefinition step) {
        try {
            Path dir = java.nio.file.Files.createTempDirectory(
                    "gatling-" + step.id().value() + "-");
            dir.toFile().deleteOnExit();
            return dir.toAbsolutePath().normalize();
        } catch (java.io.IOException e) {
            log.warn("action=create_temp_dir_failed stepId={} using_default",
                    step.id().value());
            return Path.of(System.getProperty("java.io.tmpdir"),
                    "gatling-" + step.id().value());
        }
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
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed, message, null);
    }

    private static String formatDuration(Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return String.format("%.3fs", seconds);
    }
}
