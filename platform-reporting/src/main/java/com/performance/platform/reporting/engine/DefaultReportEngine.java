package com.performance.platform.reporting.engine;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.assertion.AssertionResult;
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
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.TaskReportEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implémentation par défaut du {@link ReportEngine}.
 * <p>
 * CC-02 : le pipeline de génération est un ensemble cohésif insécable —
 * {@code classifyTasks()} → {@code buildPreparationEntries()} →
 * {@code buildInjectionEntries()} → {@code buildAssertionEntries()} →
 * {@link VerdictCalculator} → {@code buildExecutionSummary()} →
 * {@code buildEnvironmentInfo()} → {@code buildVerdictReason()} —
 * formant une chaîne unique de transformation de l'{@link ExecutionState}
 * vers le {@link CampaignReport}. Les helpers de construction sont
 * indivisibles de l'orchestration.
 * <p>
 * Écoute l'événement {@link ScenarioFinished} pour déclencher la génération
 * du rapport. Construit le {@link CampaignReport} à partir de l'{@link ExecutionState}
 * en classifiant les tâches par phase (préparation / injection / assertion)
 * via les types de résultats présents dans le contexte d'exécution.
 * <p>
 * Délégue le calcul du verdict à {@link VerdictCalculator}.
 */
@Service
public class DefaultReportEngine implements ReportEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultReportEngine.class);

    private final ExecutionRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public DefaultReportEngine(ExecutionRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
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
     * CC-02 : pipeline cohésif d'assemblage — classification par phase →
     * construction des entrées (preparation/injection/assertion) →
     * calcul du verdict → résumé d'exécution → environnement →
     * assemblage du rapport final. Chaque étape dépend de la précédente
     * et ne peut être extraite sans rompre la cohérence des données.
     *
     * @param state l'état complet de l'exécution
     * @return le rapport de campagne agrégé
     * @throws ReportGenerationException si la génération échoue
     */
    @Override
    public CampaignReport generate(ExecutionState state) throws ReportGenerationException {
        try {
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
            var verdict = VerdictCalculator.calculate(assertionResults);

            // Construire le résumé d'exécution
            ExecutionSummary executionSummary = buildExecutionSummary(
                    preparationResults, injectionResults, assertionResults);

            // Construire les infos d'environnement
            EnvironmentInfo environment = buildEnvironmentInfo(allAgentIds);

            // Déterminer la durée totale
            Duration totalDuration = computeTotalDuration(state, preparationResults,
                    injectionResults, assertionResults);

            // Construire les tags depuis le contexte
            List<String> tags = extractTags(state);

            return new CampaignReport(
                    reportId, scenarioId, scenarioId.value(), "1.0",
                    tags, Map.of(),
                    environment, executionSummary,
                    preparationResults, injectionResults, assertionResults,
                    verdict, buildVerdictReason(assertionResults),
                    generatedAt, totalDuration
            );
        } catch (ReportGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate report for execution " +
                    state.id().value(), e);
        }
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

            // Essayer de classer via le type de résultat stocké
            Optional<InjectionResult> injOpt = state.context().getFirst(taskId, InjectionResult.class);
            if (injOpt.isPresent()) {
                injTaskIds.add(taskId);
                continue;
            }

            Optional<AssertionResult> assertOpt = state.context().getFirst(taskId, AssertionResult.class);
            if (assertOpt.isPresent()) {
                assertTaskIds.add(taskId);
                continue;
            }

            // Par défaut : préparation
            prepTaskIds.add(taskId);
        }
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
            Optional<InjectionResult> injOpt = state.context().getFirst(taskIdStr, InjectionResult.class);
            injOpt.ifPresent(inj -> {
                var taskId = new TaskId(taskIdStr);
                entries.add(new InjectionReportEntry(taskId, inj, inj.gatlingReportDirectory()));
            });
        }
        return entries;
    }

    private List<AssertionReportEntry> buildAssertionEntries(ExecutionState state,
                                                               List<String> taskIds) {
        List<AssertionReportEntry> entries = new ArrayList<>();
        for (String taskIdStr : taskIds) {
            Optional<AssertionResult> assertOpt = state.context().getFirst(taskIdStr, AssertionResult.class);
            assertOpt.ifPresent(ar -> {
                var taskId = new TaskId(taskIdStr);
                entries.add(new AssertionReportEntry(taskId, ar, ar.evidence()));
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
    private ExecutionSummary buildExecutionSummary(List<TaskReportEntry> prep,
                                                    List<InjectionReportEntry> inj,
                                                    List<AssertionReportEntry> assert_) {
        var total = new AtomicInteger();
        var successful = new AtomicInteger();
        var failed = new AtomicInteger();
        var skipped = new AtomicInteger();

        AtomicReference<Duration> prepDuration = new AtomicReference<>(Duration.ZERO);
        AtomicReference<Duration> injDuration = new AtomicReference<>(Duration.ZERO);
        AtomicReference<Duration> assertDuration = new AtomicReference<>(Duration.ZERO);

        // Préparation
        for (TaskReportEntry entry : prep) {
            total.incrementAndGet();
            switch (entry.status()) {
                case SUCCESS -> successful.incrementAndGet();
                case FAILED, TIMEOUT -> failed.incrementAndGet();
                case SKIPPED -> skipped.incrementAndGet();
            }
            prepDuration.set(prepDuration.get().plus(entry.duration()));
        }

        // Injection
        for (InjectionReportEntry entry : inj) {
            total.incrementAndGet();
            successful.incrementAndGet(); // Les résultats d'injection sont toujours SUCCESS si présents
            injDuration.set(injDuration.get().plus(entry.metrics().duration()));
        }

        // Assertion
        for (AssertionReportEntry entry : assert_) {
            total.incrementAndGet();
            switch (entry.result().status()) {
                case PASSED -> successful.incrementAndGet();
                case FAILED, ERROR -> failed.incrementAndGet();
                case SKIPPED -> skipped.incrementAndGet();
            }
            assertDuration.set(assertDuration.get().plus(entry.result().evaluationDuration()));
        }

        return new ExecutionSummary(total.get(), successful.get(), failed.get(), skipped.get(),
                prepDuration.get(), injDuration.get(), assertDuration.get());
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

    private Duration computeTotalDuration(ExecutionState state,
                                           List<TaskReportEntry> prep,
                                           List<InjectionReportEntry> inj,
                                           List<AssertionReportEntry> assert_) {
        Duration d = Duration.ZERO;
        for (TaskReportEntry e : prep) d = d.plus(e.duration());
        for (InjectionReportEntry e : inj) d = d.plus(e.metrics().duration());
        for (AssertionReportEntry e : assert_) d = d.plus(e.result().evaluationDuration());
        return d;
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
