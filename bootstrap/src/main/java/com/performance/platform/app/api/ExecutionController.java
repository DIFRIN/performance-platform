package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.ExecutionSummaryResponse;
import com.performance.platform.app.api.dto.ProgressResponse;
import com.performance.platform.app.api.dto.TaskListResponse;
import com.performance.platform.app.api.dto.TaskSummaryResponse;
import com.performance.platform.app.api.dto.ScenarioSourceResponse;
import com.performance.platform.app.api.dto.SubmitResponse;
import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * REST controller exposing execution list, task summaries, and deletion.
 * <p>
 * Endpoints :
 * <ul>
 *   <li>{@code GET /api/v1/executions?limit=N} — liste des executions recentes avec progression</li>
 *   <li>{@code GET /api/v1/executions/{id}/tasks} — resumes des taches d'une execution</li>
 *   <li>{@code DELETE /api/v1/executions/{id}} — supprime une execution terminee (204)</li>
 * </ul>
 *
 * <p>La progression est calculee via {@link ExecutionProgressCalculator} a partir des
 * resultats de taches persistes (ISSUE-121).</p>
 *
 * <p>Conditionnel a la presence de {@link ExecutionRepository} — absent en mode
 * AGENT sans datasource.</p>
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    private final ListExecutionsUseCase listUseCase;
    private final DeleteExecutionUseCase deleteUseCase;
    private final ExecutionRepository executionRepository;
    private final ExecutionProgressCalculator progressCalculator;
    private final ScenarioParsingUseCase parsingUseCase;
    private final SubmitScenarioUseCase submitUseCase;

    public ExecutionController(
            ListExecutionsUseCase listUseCase,
            DeleteExecutionUseCase deleteUseCase,
            ExecutionRepository executionRepository,
            ExecutionProgressCalculator progressCalculator,
            ScenarioParsingUseCase parsingUseCase,
            SubmitScenarioUseCase submitUseCase) {
        this.listUseCase = listUseCase;
        this.deleteUseCase = deleteUseCase;
        this.executionRepository = executionRepository;
        this.progressCalculator = progressCalculator;
        this.parsingUseCase = parsingUseCase;
        this.submitUseCase = submitUseCase;
    }

    /**
     * Retourne les executions recentes avec progression.
     *
     * @param limit nombre max d'executions (defaut 50 si absent ou <= 0)
     * @return 200 OK avec la liste de resumes
     */
    @GetMapping("/executions")
    public ResponseEntity<List<ExecutionSummaryResponse>> listExecutions(
            @RequestParam(name = "limit", defaultValue = "0") int limit) {
        log.info("action=list_executions limit={}", limit);
        var states = listUseCase.list(limit);
        var summaries = states.stream()
                .map(state -> {
                    var taskResults = executionRepository.findAllTaskResults(state.id());
                    ExecutionProgress progress = progressCalculator.calculate(state, taskResults);
                    var progressResponse = new ProgressResponse(
                            progress.total(), progress.ok(), progress.ko(), progress.running());
                    return new ExecutionSummaryResponse(
                            state.id().value(),
                            state.scenarioId().value(),
                            state.status().name(),
                            state.startedAt().toString(),
                            state.updatedAt().toString(),
                            progressResponse);
                })
                .toList();
        log.info("action=list_executions_done count={}", summaries.size());
        return ResponseEntity.ok(summaries);
    }

    /**
     * Retourne les resumes de taches d'une execution.
     * L'{@code errorMessage} est present uniquement pour les taches KO.
     *
     * @param id identifiant de l'execution
     * @return 200 OK avec la liste des resumes de taches
     */
    @GetMapping("/executions/{id}/tasks")
    public ResponseEntity<TaskListResponse> listTasks(@PathVariable("id") String id) {
        log.info("action=list_tasks executionId={}", id);
        var executionId = ExecutionId.of(id);
        Map<TaskId, Map<AgentId, TaskResult>> allResults =
                executionRepository.findAllTaskResults(executionId);

        var state = executionRepository.findById(executionId);
        var taskSummaries = new ArrayList<TaskSummaryResponse>();
        var plannedIds = new HashSet<TaskId>();
        state.ifPresent(execution -> execution.tasks().forEach(task -> {
            plannedIds.add(task.id());
            var results = allResults.getOrDefault(task.id(), Map.of());
            if (results.isEmpty()) {
                taskSummaries.add(new TaskSummaryResponse(task.id().value(), task.taskName(), task.phase().name(),
                        task.started() ? "RUNNING" : "PENDING", null,
                        null, null, null, task.configuration(), Map.of()));
            } else {
                results.forEach((agentId, result) -> taskSummaries.add(
                        toTaskSummary(task.id(), agentId, result, task.phase().name(), task.configuration())));
            }
        }));
        allResults.entrySet().stream()
                .filter(entry -> !plannedIds.contains(entry.getKey()))
                .flatMap(entry -> {
                    var taskId = entry.getKey();
                    var agentResults = entry.getValue();
                    return agentResults.entrySet().stream()
                            .map(result -> toTaskSummary(taskId, result.getKey(), result.getValue(), null, Map.of()));
                })
                .forEach(taskSummaries::add);

        var response = new TaskListResponse(id, taskSummaries.size(), taskSummaries);
        log.info("action=list_tasks_done executionId={} count={}", id, taskSummaries.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/executions/{id}/scenario")
    public ResponseEntity<ScenarioSourceResponse> scenarioSource(@PathVariable("id") String id) {
        log.info("action=get_scenario_source executionId={}", id);
        return executionRepository.findById(ExecutionId.of(id))
                .map(state -> ResponseEntity.ok(new ScenarioSourceResponse(state.scenarioYaml())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Supprime une execution terminee.
     * Retourne 204 si l'execution est terminee (COMPLETED, FAILED, CANCELLED).
     * Retourne 409 si l'execution est active (STARTED, RUNNING) — ADR-020.
     *
     * @param id identifiant de l'execution
     * @return 204 No Content
     */
    @DeleteMapping("/executions/{id}")
    public ResponseEntity<Void> deleteExecution(@PathVariable("id") String id) {
        log.info("action=delete_execution executionId={}", id);
        deleteUseCase.delete(ExecutionId.of(id));
        log.info("action=delete_execution_done executionId={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Relance une execution terminee a partir de son YAML d'origine.
     * Le YAML source est celui conserve avec l'execution ; une nouvelle
     * execution est creee (l'ancienne n'est pas modifiee).
     *
     * @param id identifiant de l'execution a relancer
     * @return 202 Accepted avec le nouvel identifiant d'execution,
     *         404 si l'execution est inconnue, 409 si elle est encore active
     *         ou si son YAML source n'est pas disponible
     */
    @PostMapping("/executions/{id}/rerun")
    public ResponseEntity<?> rerun(@PathVariable("id") String id) {
        log.info("action=rerun_execution executionId={}", id);
        var executionId = ExecutionId.of(id);
        var existing = executionRepository.findById(executionId);
        if (existing.isEmpty()) {
            log.warn("action=rerun_execution_not_found executionId={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "EXECUTION_NOT_FOUND",
                            "message", "No execution with id '" + id + "'"));
        }

        var state = existing.get();
        if (state.status() == ExecutionStatus.STARTED || state.status() == ExecutionStatus.RUNNING) {
            log.warn("action=rerun_execution_active executionId={} status={}", id, state.status());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "EXECUTION_STILL_ACTIVE",
                            "message", "Cancel the execution before rerunning it"));
        }

        String sourceYaml = state.scenarioYaml();
        if (sourceYaml == null || sourceYaml.isBlank()) {
            log.warn("action=rerun_execution_source_unavailable executionId={}", id);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SCENARIO_SOURCE_UNAVAILABLE",
                            "message", "The original scenario YAML was not retained for this execution"));
        }

        var scenario = parsingUseCase.parse(sourceYaml);
        var newExecutionId = submitUseCase.submit(scenario);
        log.info("action=rerun_execution_accepted executionId={} newExecutionId={}",
                id, newExecutionId.value());
        return ResponseEntity.accepted()
                .body(new SubmitResponse(newExecutionId.value(), "ACCEPTED"));
    }

    // ---- Helpers ----

    private static TaskSummaryResponse toTaskSummary(TaskId taskId, AgentId agentId, TaskResult result,
                                                     String phase, Map<String, Object> configuration) {
        String errorMessage = isKo(result.status()) ? result.errorMessage() : null;
        return new TaskSummaryResponse(
                taskId.value(),
                result.taskName(),
                phase,
                result.status().name(),
                errorMessage, agentId.value(), result.duration().toMillis(), result.completedAt().toString(),
                configuration, result.outputs());
    }

    private static boolean isKo(TaskStatus status) {
        return status == TaskStatus.FAILED
                || status == TaskStatus.TIMEOUT
                || status == TaskStatus.SKIPPED;
    }
}
