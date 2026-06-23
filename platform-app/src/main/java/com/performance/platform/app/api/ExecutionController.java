package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.ExecutionSummaryResponse;
import com.performance.platform.app.api.dto.ProgressResponse;
import com.performance.platform.app.api.dto.TaskListResponse;
import com.performance.platform.app.api.dto.TaskSummaryResponse;
import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
 */
@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    private final ListExecutionsUseCase listUseCase;
    private final DeleteExecutionUseCase deleteUseCase;
    private final ExecutionRepository executionRepository;
    private final ExecutionProgressCalculator progressCalculator;

    public ExecutionController(
            ListExecutionsUseCase listUseCase,
            DeleteExecutionUseCase deleteUseCase,
            ExecutionRepository executionRepository,
            ExecutionProgressCalculator progressCalculator) {
        this.listUseCase = listUseCase;
        this.deleteUseCase = deleteUseCase;
        this.executionRepository = executionRepository;
        this.progressCalculator = progressCalculator;
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

        var taskSummaries = allResults.entrySet().stream()
                .flatMap(entry -> {
                    var taskId = entry.getKey();
                    var agentResults = entry.getValue();
                    return agentResults.values().stream()
                            .map(result -> toTaskSummary(taskId, result));
                })
                .toList();

        var response = new TaskListResponse(id, taskSummaries.size(), taskSummaries);
        log.info("action=list_tasks_done executionId={} count={}", id, taskSummaries.size());
        return ResponseEntity.ok(response);
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

    // ---- Helpers ----

    private static TaskSummaryResponse toTaskSummary(TaskId taskId, TaskResult result) {
        String errorMessage = isKo(result.status()) ? result.errorMessage() : null;
        return new TaskSummaryResponse(
                taskId.value(),
                result.taskName(),
                null,   // phase not stored in persisted task results
                result.status().name(),
                errorMessage);
    }

    private static boolean isKo(TaskStatus status) {
        return status == TaskStatus.FAILED
                || status == TaskStatus.TIMEOUT
                || status == TaskStatus.SKIPPED;
    }
}
