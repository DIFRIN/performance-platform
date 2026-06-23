package com.performance.platform.app.api;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionNotDeletableException;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ExecutionController")
class ExecutionControllerTest {

    private final ListExecutionsUseCase listUseCase = mock(ListExecutionsUseCase.class);
    private final DeleteExecutionUseCase deleteUseCase = mock(DeleteExecutionUseCase.class);
    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final ExecutionProgressCalculator progressCalculator = mock(ExecutionProgressCalculator.class);

    private MockMvc mockMvc;

    private static final String EXEC_ID = "exec-001";
    private static final String SCENARIO_ID = "sc-001";
    private static final Instant NOW = Instant.parse("2026-06-23T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(executionRepository.findAllTaskResults(any())).thenReturn(Map.of());
        when(progressCalculator.calculate(any(), any())).thenReturn(new ExecutionProgress(0, 0, 0, 0));
        var controller = new ExecutionController(
                listUseCase, deleteUseCase, executionRepository, progressCalculator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /executions")
    class ListExecutions {

        @Test
        @DisplayName("should return 200 with execution summaries including progress")
        void shouldReturnSummariesWithProgress() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            var state = buildState(executionId, ExecutionStatus.COMPLETED);
            when(listUseCase.list(10)).thenReturn(List.of(state));
            when(progressCalculator.calculate(any(), any()))
                    .thenReturn(new ExecutionProgress(3, 2, 1, 0));

            mockMvc.perform(get("/api/v1/executions").param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].executionId").value(EXEC_ID))
                    .andExpect(jsonPath("$[0].scenarioId").value(SCENARIO_ID))
                    .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                    .andExpect(jsonPath("$[0].startedAt").isNotEmpty())
                    .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
                    .andExpect(jsonPath("$[0].progress.total").value(3))
                    .andExpect(jsonPath("$[0].progress.ok").value(2))
                    .andExpect(jsonPath("$[0].progress.ko").value(1))
                    .andExpect(jsonPath("$[0].progress.running").value(0));
        }

        @Test
        @DisplayName("should return 200 with empty list when no executions")
        void shouldReturnEmptyList() throws Exception {
            when(listUseCase.list(0)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/executions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("should delegate with limit=0 when no param given")
        void shouldDelegateWithDefaultLimit() throws Exception {
            when(listUseCase.list(0)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/executions"))
                    .andExpect(status().isOk());

            verify(listUseCase).list(0);
        }

        @Test
        @DisplayName("should return multiple summaries")
        void shouldReturnMultipleSummaries() throws Exception {
            var id1 = ExecutionId.of("exec-001");
            var id2 = ExecutionId.of("exec-002");
            when(listUseCase.list(5)).thenReturn(List.of(
                    buildState(id1, ExecutionStatus.COMPLETED),
                    buildState(id2, ExecutionStatus.FAILED)));

            mockMvc.perform(get("/api/v1/executions").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].executionId").value("exec-001"))
                    .andExpect(jsonPath("$[1].executionId").value("exec-002"));
        }
    }

    @Nested
    @DisplayName("GET /executions/{id}/tasks")
    class ListTasks {

        @Test
        @DisplayName("should return 200 with task summaries when tasks exist")
        void shouldReturnTaskSummaries() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            var taskId = TaskId.of("task-001");
            var agentId = AgentId.of("agent-local");
            var taskResult = TaskResult.success(
                    taskId, "gatling", Duration.ofMillis(500), Map.of());

            when(executionRepository.findAllTaskResults(executionId))
                    .thenReturn(Map.of(taskId, Map.of(agentId, taskResult)));

            mockMvc.perform(get("/api/v1/executions/{id}/tasks", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(EXEC_ID))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.tasks[0].taskId").value("task-001"))
                    .andExpect(jsonPath("$.tasks[0].taskName").value("gatling"))
                    .andExpect(jsonPath("$.tasks[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.tasks[0].errorMessage").doesNotExist());
        }

        @Test
        @DisplayName("should include errorMessage for KO tasks")
        void shouldIncludeErrorMessageForKoTasks() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            var taskId = TaskId.of("task-002");
            var agentId = AgentId.of("agent-local");
            var taskResult = TaskResult.failed(
                    taskId, "load-test", Duration.ofMillis(200), "Timeout exceeded", null);

            when(executionRepository.findAllTaskResults(executionId))
                    .thenReturn(Map.of(taskId, Map.of(agentId, taskResult)));

            mockMvc.perform(get("/api/v1/executions/{id}/tasks", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.tasks[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.tasks[0].errorMessage").value("Timeout exceeded"));
        }

        @Test
        @DisplayName("should return empty tasks list when no task results exist")
        void shouldReturnEmptyTasks() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            when(executionRepository.findAllTaskResults(executionId)).thenReturn(Map.of());

            mockMvc.perform(get("/api/v1/executions/{id}/tasks", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value(EXEC_ID))
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.tasks").isArray())
                    .andExpect(jsonPath("$.tasks").isEmpty());
        }

        @Test
        @DisplayName("should not include errorMessage for SUCCESS tasks")
        void shouldNotIncludeErrorMessageForSuccessTasks() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            var taskId = TaskId.of("task-ok");
            var agentId = AgentId.of("agent-local");
            var taskResult = TaskResult.success(
                    taskId, "setup", Duration.ofMillis(100), Map.of());

            when(executionRepository.findAllTaskResults(executionId))
                    .thenReturn(Map.of(taskId, Map.of(agentId, taskResult)));

            mockMvc.perform(get("/api/v1/executions/{id}/tasks", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tasks[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.tasks[0].errorMessage").doesNotExist());
        }
    }

    @Nested
    @DisplayName("DELETE /executions/{id}")
    class DeleteExecution {

        @Test
        @DisplayName("should return 204 when execution is successfully deleted")
        void shouldReturn204OnSuccess() throws Exception {
            doNothing().when(deleteUseCase).delete(eq(ExecutionId.of(EXEC_ID)));

            mockMvc.perform(delete("/api/v1/executions/{id}", EXEC_ID))
                    .andExpect(status().isNoContent());

            verify(deleteUseCase).delete(ExecutionId.of(EXEC_ID));
        }

        @Test
        @DisplayName("should return 409 when execution is active (STARTED/RUNNING) — ADR-020")
        void shouldReturn409WhenExecutionIsActive() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            doThrow(new ExecutionNotDeletableException(executionId, ExecutionStatus.RUNNING))
                    .when(deleteUseCase).delete(executionId);

            mockMvc.perform(delete("/api/v1/executions/{id}", EXEC_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("EXECUTION_NOT_DELETABLE"));
        }

        @Test
        @DisplayName("should return 409 when execution is STARTED")
        void shouldReturn409WhenExecutionIsStarted() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            doThrow(new ExecutionNotDeletableException(executionId, ExecutionStatus.STARTED))
                    .when(deleteUseCase).delete(executionId);

            mockMvc.perform(delete("/api/v1/executions/{id}", EXEC_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("EXECUTION_NOT_DELETABLE"));
        }

        @Test
        @DisplayName("should return 500 when execution is not found")
        void shouldReturn500WhenNotFound() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            doThrow(new ExecutionException("Execution not found: " + executionId))
                    .when(deleteUseCase).delete(executionId);

            mockMvc.perform(delete("/api/v1/executions/{id}", EXEC_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("EXECUTION_FAILED"));
        }
    }

    // ---- Helpers ----

    private static ExecutionState buildState(ExecutionId executionId, ExecutionStatus status) {
        return new ExecutionState(
                executionId,
                ScenarioId.of(SCENARIO_ID),
                status,
                Map.of(),
                ExecutionContext.initial(executionId, ScenarioId.of(SCENARIO_ID)),
                NOW,
                NOW);
    }
}
