package com.performance.platform.reporting.engine;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.event.ReportGenerated;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.reporting.ReportEngine;
import com.performance.platform.reporting.output.ReportFileWriter;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.TaskReportEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.domain.execution.ExecutionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implémentation par défaut du {@link ReportEngine}.
 * <p>
 * Écoute l'événement {@link ScenarioFinished} pour déclencher la génération
 * du rapport. Construit le {@link CampaignReport} à partir de l'{@link ExecutionState}
 * avec contexte du scenario, mesures, criteres et limites de validation.
 * Les sorties typees sont restaurees apres lecture JSONB.
 * <p>
 * Le cycle evenementiel ecrit les formats avant de publier {@link ReportGenerated}.
 */
@Service
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
public class DefaultReportEngine implements ReportEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultReportEngine.class);
    private static final ObjectMapper RESULT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .addMixIn(AssertionSummary.class, DerivedAssertionProperties.class)
            .addMixIn(AssertionResult.class, DerivedAssertionProperties.class);

    // isPassed() is serialized by Jackson but is derived from verdict/status on reload.
    @JsonIgnoreProperties(value = "passed", allowGetters = true)
    private abstract static class DerivedAssertionProperties {}

    private final ExecutionRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportFileWriter fileWriter;

    public DefaultReportEngine(ExecutionRepository repository, ApplicationEventPublisher eventPublisher,
                               ReportFileWriter fileWriter) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.fileWriter = fileWriter;
    }

    /**
     * Écoute la fin d'un scénario et déclenche la génération du rapport.
     *
     * @param event l'événement de fin de scénario
     */
    @EventListener
    public void onScenarioFinished(ScenarioFinished event) {
        ExecutionId executionId = event.executionId();
        log.info("action=scenario_finished executionId={} verdict={}", executionId.value(), event.verdict());

        Optional<ExecutionState> stateOpt = repository.findById(executionId);
        if (stateOpt.isEmpty()) {
            log.warn("action=report_skipped executionId={} reason=state_not_found", executionId.value());
            return;
        }

        try {
            CampaignReport report = generate(stateOpt.get());
            fileWriter.write(executionId, report);
            log.info("action=report_generated executionId={} reportId={} verdict={}",
                    executionId.value(), report.id().value(), report.verdict());
            eventPublisher.publishEvent(new ReportGenerated(executionId, report.id(), Instant.now()));
        } catch (Exception e) {
            log.error("action=report_generation_failed executionId={}", executionId.value(), e);
        }
    }

    /**
     * Génère le CampaignReport complet à partir d'un ExecutionState.
     * <p>
     * Assemblage uniquement : l'ecriture est geree par le cycle evenementiel.
     *
     * @param state l'état complet de l'exécution
     * @return le rapport de campagne agrégé
     * @throws ReportGenerationException si la génération échoue
     */
    @Override
    public CampaignReport generate(ExecutionState state) throws ReportGenerationException {
        try {
            state = restoreTypedOutputs(state);
            var reportId = ReportId.generate();
            ScenarioId scenarioId = state.scenarioId();
            var generatedAt = Instant.now();

            // Collecter les taskIds et classer par phase
            List<String> prepTaskIds = new ArrayList<>();
            List<String> injTaskIds = new ArrayList<>();
            List<String> assertTaskIds = new ArrayList<>();
            Set<String> allAgentIds = new HashSet<>();

            classifyTasks(state, prepTaskIds, injTaskIds, assertTaskIds, allAgentIds);

            // Construire les entrées par phase
            List<TaskReportEntry> preparationResults = buildPreparationEntries(state, prepTaskIds);
            List<InjectionReportEntry> injectionResults = buildInjectionEntries(state, injTaskIds);
            List<AssertionReportEntry> assertionResults = buildAssertionEntries(state, assertTaskIds);

            // Calculer le verdict
            var performance = PerformanceAssessmentBuilder.build(state, injectionResults, assertionResults);
            var verdict = performance.validation().status();

            // Construire le résumé d'exécution
            ExecutionSummary executionSummary = buildExecutionSummary(
                    state, prepTaskIds, injTaskIds);

            // Construire les infos d'environnement
            EnvironmentInfo environment = buildEnvironmentInfo(allAgentIds);

            // Déterminer la durée totale
            Duration totalDuration = Duration.between(state.startedAt(), state.updatedAt());

            // Construire les tags depuis le contexte
            List<String> tags = extractTags(state);

            var details = state.scenarioDetails();
            return new CampaignReport(
                    reportId, scenarioId, details != null && details.name() != null ? details.name() : scenarioId.value(),
                    details != null && details.version() != null ? details.version() : "Not provided",
                    details != null ? details.tags() : tags, details != null ? details.metadata() : Map.of(),
                    environment, executionSummary,
                    preparationResults, injectionResults, assertionResults,
                    verdict, buildVerdictReason(assertionResults) + "; Performance validation: " + verdict + "; "
                            + performance.validation().limitations().size() + " missing requirements",
                    generatedAt, totalDuration, performance
            );
        } catch (ReportGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate report for execution " +
                    state.id().value(), e);
        }
    }

    private ExecutionState restoreTypedOutputs(ExecutionState state) {
        var context = ExecutionContext.initial(state.id(), state.scenarioId());
        for (var task : state.context().store().entrySet()) {
            for (var agent : task.getValue().entrySet()) {
                var result = agent.getValue();
                var outputs = new java.util.LinkedHashMap<>(result.outputs());
                Object injection = outputs.get(InjectionResult.OUTPUT_KEY);
                if (injection instanceof Map<?, ?> map && map.containsKey("totalRequests")) {
                    outputs.put(InjectionResult.OUTPUT_KEY, RESULT_MAPPER.convertValue(injection, InjectionResult.class));
                }
                Object assertion = outputs.get(AssertionSummary.OUTPUT_KEY);
                if (assertion instanceof Map<?, ?>) {
                    outputs.put(AssertionSummary.OUTPUT_KEY, RESULT_MAPPER.convertValue(assertion, AssertionSummary.class));
                }
                Object legacy = outputs.get("assertionResult");
                if (legacy instanceof Map<?, ?>) {
                    outputs.put("assertionResult", RESULT_MAPPER.convertValue(legacy, AssertionResult.class));
                }
                context = context.with(task.getKey(), agent.getKey(), new TaskResult(result.taskId(), result.taskName(),
                        result.status(), result.duration(), outputs, result.errorMessage(), result.cause(), result.completedAt()));
            }
        }
        return new ExecutionState(state.id(), state.scenarioId(), state.status(), state.phaseStatuses(), context,
                state.startedAt(), state.updatedAt(), state.plannedTaskCount(), state.tasks(), state.scenarioYaml(), state.scenarioDetails());
    }

    // ──────────────────────────────────────────────
    // Classification des tâches par phase
    // ──────────────────────────────────────────────

    /**
     * Classe les tâches de l'état d'exécution en trois phases
     * en inspectant les types de résultats stockés dans le contexte.
     * <p>
     * CC-02 : pipeline cohésif d'itération des tâches du store
     * et classification par type de sortie.
     */
    private void classifyTasks(ExecutionState state,
                                List<String> prepTaskIds,
                                List<String> injTaskIds,
                                List<String> assertTaskIds,
                                Set<String> allAgentIds) {
        var store = state.context().store();
        for (var entry : store.entrySet()) {
            String taskId = entry.getKey();
            Map<String, TaskResult> agentResults = entry.getValue();
            allAgentIds.addAll(agentResults.keySet());
            var planned = state.tasks().stream().filter(task -> task.id().value().equals(taskId)).findFirst();
            if (planned.isPresent()) {
                switch (planned.get().phase()) {
                    case INJECTION -> injTaskIds.add(taskId);
                    case ASSERTION -> assertTaskIds.add(taskId);
                    case PREPARATION -> prepTaskIds.add(taskId);
                }
                continue;
            }

            // Essayer de classer via le type de résultat stocké
            Optional<InjectionResult> injOpt = state.context().getFirstOutput(taskId, InjectionResult.OUTPUT_KEY, InjectionResult.class);
            if (injOpt.isPresent()) {
                injTaskIds.add(taskId);
                continue;
            }

            Optional<AssertionResult> assertOpt = resolveAssertionResult(state.context(), taskId);
            if (assertOpt.isPresent()) {
                assertTaskIds.add(taskId);
                continue;
            }

            // Par défaut : préparation
            prepTaskIds.add(taskId);
        }
    }

    /**
     * Resout un resultat d'assertion : d'abord la cle contractuelle
     * {@code "assertion"} ({@link AssertionSummary}), puis l'ancien lookup
     * type-erasé pour les producteurs historiques/fixtures.
     */
    private static Optional<AssertionResult> resolveAssertionResult(
            com.performance.platform.domain.execution.ExecutionContext context,
            String taskId) {
        Optional<AssertionSummary> summary = context
                .getFirstOutput(taskId, AssertionSummary.OUTPUT_KEY, AssertionSummary.class);
        if (summary.isPresent()) {
            var s = summary.get();
            return Optional.of(new AssertionResult(
                    s.assertionId(), s.verdict(), s.description(),
                    null, s.evaluationDuration(), s.evaluatedAt()));
        }
        // Producteurs historiques / fixtures : cle legacy "assertionResult".
        return context.getFirstOutput(taskId, "assertionResult", AssertionResult.class);
    }

    // ──────────────────────────────────────────────
    // Construction des entrées par phase
    // ──────────────────────────────────────────────

    private List<TaskReportEntry> buildPreparationEntries(ExecutionState state,
                                                           List<String> taskIds) {
        List<TaskReportEntry> entries = new ArrayList<>();
        for (String taskIdStr : taskIds) {
            Map<String, TaskResult> agentResults = state.context().getAll(taskIdStr);
            for (TaskResult result : agentResults.values()) {
                entries.add(new TaskReportEntry(
                        result.taskId(), result.taskName(), result.status(),
                        result.duration(), result.outputs()
                ));
            }
        }
        return entries;
    }

    private List<InjectionReportEntry> buildInjectionEntries(ExecutionState state,
                                                               List<String> taskIds) {
        List<InjectionReportEntry> entries = new ArrayList<>();
        for (String taskIdStr : taskIds) {
            state.context().getAll(taskIdStr).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(agent -> {
                Object output = agent.getValue().outputs().get(InjectionResult.OUTPUT_KEY);
                if (output instanceof InjectionResult inj) {
                    entries.add(new InjectionReportEntry(new TaskId(taskIdStr), inj, inj.gatlingReportDirectory(), agent.getKey()));
                }
            });
        }
        return entries;
    }

    private List<AssertionReportEntry> buildAssertionEntries(ExecutionState state,
                                                               List<String> taskIds) {
        List<AssertionReportEntry> entries = new ArrayList<>();
        for (String taskIdStr : taskIds) {
            state.context().getAll(taskIdStr).forEach((agent, result) -> {
                var context = ExecutionContext.initial(state.id(), state.scenarioId()).with(taskIdStr, agent, result);
                resolveAssertionResult(context, taskIdStr).ifPresent(ar ->
                        entries.add(new AssertionReportEntry(new TaskId(taskIdStr), ar, ar.evidence())));
            });
        }
        return entries;
    }

    // ──────────────────────────────────────────────
    // Résumé d'exécution
    // ──────────────────────────────────────────────

    /**
     * Construit le résumé quantitatif : compteurs de tâches et durées par phase.
     * <p>
     * CC-02 : pipeline cohésif d'itération sur les trois phases — les boucles
     * prep → injection → assertion partagent la même structure (incrémentation
     * compteurs + cumul durées) et forment un bloc de synthèse indivisible.
     */
    private ExecutionSummary buildExecutionSummary(ExecutionState state, List<String> prep, List<String> inj) {
        int total = 0, successful = 0, failed = 0, skipped = 0;
        Duration preparation = Duration.ZERO, injection = Duration.ZERO, assertion = Duration.ZERO;
        for (var task : state.context().store().entrySet()) {
            for (var agent : task.getValue().entrySet()) {
                total++;
                var result = agent.getValue();
                var context = ExecutionContext.initial(state.id(), state.scenarioId())
                        .with(task.getKey(), agent.getKey(), result);
                var assertionResult = resolveAssertionResult(context, task.getKey());
                if (assertionResult.isPresent()) {
                    switch (assertionResult.get().status()) {
                        case PASSED -> successful++;
                        case FAILED, ERROR -> failed++;
                        case SKIPPED -> skipped++;
                    }
                } else {
                    switch (result.status()) {
                        case SUCCESS -> successful++;
                        case FAILED, TIMEOUT -> failed++;
                        case SKIPPED -> skipped++;
                    }
                }
                if (prep.contains(task.getKey())) preparation = preparation.plus(result.duration());
                else if (inj.contains(task.getKey())) injection = injection.plus(result.duration());
                else assertion = assertion.plus(result.duration());
            }
        }
        return new ExecutionSummary(total, successful, failed, skipped, preparation, injection, assertion);
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private EnvironmentInfo buildEnvironmentInfo(Set<String> agentIds) {
        return new EnvironmentInfo(
                List.copyOf(agentIds),
                System.getProperty("java.version", "unknown"),
                Map.of()
        );
    }

    private List<String> extractTags(ExecutionState state) {
        return List.of();
    }

    private String buildVerdictReason(List<AssertionReportEntry> assertionResults) {
        long passed = assertionResults.stream()
                .filter(e -> e.result().status() == com.performance.platform.domain.assertion.AssertionStatus.PASSED)
                .count();
        long failed = assertionResults.stream()
                .filter(e -> e.result().status() == com.performance.platform.domain.assertion.AssertionStatus.FAILED)
                .count();
        long errors = assertionResults.stream()
                .filter(e -> e.result().status() == com.performance.platform.domain.assertion.AssertionStatus.ERROR)
                .count();
        long skipped = assertionResults.stream()
                .filter(e -> e.result().status() == com.performance.platform.domain.assertion.AssertionStatus.SKIPPED)
                .count();
        return String.format("%d passed, %d failed, %d error, %d skipped", passed, failed, errors, skipped);
    }
}
