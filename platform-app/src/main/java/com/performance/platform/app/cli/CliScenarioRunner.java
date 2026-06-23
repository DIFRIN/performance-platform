package com.performance.platform.app.cli;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter entrant CLI pour le mode headless run-and-exit (ADR-021).
 * <p>
 * Active uniquement quand l'argument {@code --scenario=} est present sur
 * la ligne de commande ({@code @ConditionalOnProperty(name = "scenario")}).
 * Appelle les ports in de {@code platform-application}
 * ({@link ScenarioParsingUseCase}, {@link ExecuteScenarioUseCase},
 * {@link GetExecutionStatusUseCase}) — symetrique du {@code ScenarioController}
 * pour le mode API.
 * <p>
 * Le resume est imprime sur {@code System.out} (pas le logger) pour
 * rester pilotable en script/CI. Les codes de sortie suivent ADR-021 :
 * <ul>
 *   <li>{@code 0} — execution COMPLETED, toutes les assertions passees</li>
 *   <li>{@code 1} — execution FAILED, assertion en echec, ou timeout</li>
 *   <li>{@code 2} — arguments invalides (fichier introuvable, YAML invalide,
 *       {@code --scenario=} vide)</li>
 * </ul>
 * <p>
 * Implémente {@link ExitCodeGenerator} pour que {@code SpringApplication.exit()}
 * dans {@code main()} recupere le code de sortie.
 */
@Component
@ConditionalOnProperty(name = "scenario")
public class CliScenarioRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(CliScenarioRunner.class);

    static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    static final Duration MAX_POLL_WAIT = Duration.ofMinutes(5);

    private final ScenarioParsingUseCase parsingUseCase;
    private final ExecuteScenarioUseCase executeUseCase;
    private final GetExecutionStatusUseCase statusUseCase;
    private final ExecutionRepository executionRepository;
    private final ExecutionProgressCalculator progressCalculator;
    private final ResourceLoader resourceLoader;

    @Value("${scenario}")
    private String scenarioPath;

    private int exitCode;

    public CliScenarioRunner(
            ScenarioParsingUseCase parsingUseCase,
            ExecuteScenarioUseCase executeUseCase,
            GetExecutionStatusUseCase statusUseCase,
            ExecutionRepository executionRepository,
            ExecutionProgressCalculator progressCalculator,
            ResourceLoader resourceLoader) {
        this.parsingUseCase = Objects.requireNonNull(parsingUseCase, "parsingUseCase required");
        this.executeUseCase = Objects.requireNonNull(executeUseCase, "executeUseCase required");
        this.statusUseCase = Objects.requireNonNull(statusUseCase, "statusUseCase required");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository required");
        this.progressCalculator = Objects.requireNonNull(progressCalculator, "progressCalculator required");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader required");
    }

    /**
     * Returns the exit code determined during {@link #run(ApplicationArguments)}.
     */
    @Override
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Spring Boot callback — executes the scenario synchronously and stores the exit code.
     * Does NOT call {@code System.exit()} (handled by {@code main()} via
     * {@code SpringApplication.exit()}).
     */
    @Override
    public void run(ApplicationArguments args) {
        exitCode = executeScenario();
    }

    /**
     * Core execution logic: validate path, load YAML, parse, execute, poll, print resume.
     *
     * @return exit code (0 success, 1 failure, 2 invalid args)
     */
    int executeScenario() {
        // 1. Validate scenario path
        if (scenarioPath == null || scenarioPath.isBlank()) {
            return printResume(2, "-", "-", "-", "INVALID_ARGS", 0, 0, 0, "-");
        }

        // 2. Load YAML content from path (filesystem or classpath via ResourceLoader, ADR-013)
        String yamlContent;
        try {
            Resource resource = resourceLoader.getResource(scenarioPath);
            if (!resource.exists()) {
                return printResume(2, "-", "-", "-", "FILE_NOT_FOUND", 0, 0, 0, "-");
            }
            yamlContent = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("action=file_read_failed path={}", scenarioPath, e);
            return printResume(2, "-", "-", "-", "FILE_NOT_FOUND", 0, 0, 0, "-");
        }

        // 3. Parse YAML → ScenarioDefinition
        ScenarioDefinition scenario;
        try {
            scenario = parsingUseCase.parse(yamlContent);
        } catch (ScenarioParsingException e) {
            log.error("action=parsing_failed errors={}", e.getErrors().size());
            return printResume(2, "-", "-", "-", "PARSE_ERROR", 0, 0, 0, "-");
        } catch (RuntimeException e) {
            log.error("action=parsing_failed message={}", e.getMessage());
            return printResume(2, "-", "-", "-", "PARSE_ERROR", 0, 0, 0, "-");
        }

        String scenarioName = displayName(scenario);
        String scenarioId = scenario.id().value();

        // 4. Execute scenario
        ExecutionId executionId;
        try {
            executionId = executeUseCase.execute(scenario);
        } catch (RuntimeException e) {
            log.error("action=execution_failed", e);
            return printResume(1, scenarioName, scenarioId, "-", "FAILED", 0, 0, 0, "-");
        }

        String execIdValue = executionId.value();

        // 5. Poll until terminal status
        ExecutionStatus finalStatus = pollUntilTerminal(executionId);

        // 6. Gather execution state and calculate progress
        Optional<ExecutionState> stateOpt = statusUseCase.getState(executionId);
        int ok = 0, ko = 0, total = 0;
        if (stateOpt.isPresent()) {
            Map<com.performance.platform.domain.id.TaskId,
                    Map<com.performance.platform.domain.id.AgentId,
                            com.performance.platform.domain.task.TaskResult>> taskResults =
                    executionRepository.findAllTaskResults(executionId);
            if (taskResults != null) {
                ExecutionProgress progress = progressCalculator.calculate(
                        stateOpt.get(), taskResults);
                ok = progress.ok();
                ko = progress.ko();
                total = progress.total();
            }
        }

        // 7. Determine exit code
        int code = determineExitCode(finalStatus, stateOpt);

        return printResume(code, scenarioName, scenarioId, execIdValue,
                finalStatus.name(), ok, ko, total, "-");
    }

    /**
     * Polls {@link GetExecutionStatusUseCase#getStatus(ExecutionId)} until
     * a terminal status ({@code COMPLETED}, {@code FAILED}, {@code CANCELLED})
     * is reached or the maximum wait time elapses.
     * <p>
     * Timeout is treated as a failure (code 1).
     */
    ExecutionStatus pollUntilTerminal(ExecutionId executionId) {
        long deadline = System.currentTimeMillis() + MAX_POLL_WAIT.toMillis();

        while (System.currentTimeMillis() < deadline) {
            ExecutionStatus status = statusUseCase.getStatus(executionId);
            if (status == ExecutionStatus.COMPLETED
                    || status == ExecutionStatus.FAILED
                    || status == ExecutionStatus.CANCELLED) {
                return status;
            }

            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("action=poll_interrupted executionId={}", executionId.value());
                return ExecutionStatus.FAILED;
            }
        }

        log.warn("action=poll_timeout executionId={}", executionId.value());
        return ExecutionStatus.FAILED;
    }

    /**
     * Determines the exit code based on execution status and assertion results.
     * <p>
     * ADR-021: code 0 only if COMPLETED with all assertions passed.
     * ASSERTION phase missing or COMPLETED = assertions passed.
     * ASSERTION phase FAILED = exit code 1 even if overall status is COMPLETED.
     */
    static int determineExitCode(ExecutionStatus status, Optional<ExecutionState> stateOpt) {
        if (status != ExecutionStatus.COMPLETED) {
            return 1; // FAILED, CANCELLED, or timeout
        }

        // COMPLETED — verify assertions
        if (stateOpt.isPresent()) {
            PhaseStatus assertionPhase = stateOpt.get().phaseStatuses().get(Phase.ASSERTION);
            if (assertionPhase != null && assertionPhase != PhaseStatus.COMPLETED) {
                return 1; // assertion phase explicitly failed
            }
            // No ASSERTION phase or ASSERTION COMPLETED → success
        }

        return 0;
    }

    /**
     * Prints the structured resume on {@code System.out} (not the logger)
     * and returns the exit code.
     * <p>
     * Format per ADR-021:
     * <pre>
     * scenario   : &lt;name&gt; (&lt;id&gt;)
     * execution  : &lt;executionId&gt;
     * status     : COMPLETED | FAILED
     * tasks      : &lt;ok&gt; ok / &lt;ko&gt; ko / &lt;total&gt; total
     * report     : &lt;chemin rapport si genere, sinon "-"&gt;
     * exit       : &lt;code&gt;
     * </pre>
     */
    static int printResume(int code, String scenarioName, String scenarioId,
                           String executionId, String status, int ok, int ko, int total,
                           String report) {

        System.out.println("scenario   : " + (scenarioName != null && !scenarioName.equals("-")
                ? scenarioName + " (" + scenarioId + ")"
                : "-"));
        System.out.println("execution  : " + executionId);
        System.out.println("status     : " + status);
        System.out.println("tasks      : " + ok + " ok / " + ko + " ko / " + total + " total");
        System.out.println("report     : " + report);
        System.out.println("exit       : " + code);

        return code;
    }

    /**
     * Returns the display name for the scenario: {@code name} if present,
     * otherwise falls back to the scenario identifier.
     */
    private static String displayName(ScenarioDefinition scenario) {
        if (scenario.name() != null && !scenario.name().isBlank()) {
            return scenario.name();
        }
        return scenario.id().value();
    }
}