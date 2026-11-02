package com.performance.platform.app.api;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Optional;
import com.performance.platform.domain.execution.ExecutionTask;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;

@DisplayName("ExecutionController")
class ExecutionControllerTest {

    private final ListExecutionsUseCase listUseCase = mock(ListExecutionsUseCase.class);
    private final DeleteExecutionUseCase deleteUseCase = mock(DeleteExecutionUseCase.class);
    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final ExecutionProgressCalculator progressCalculator = mock(ExecutionProgressCalculator.class);
    private final ScenarioParsingUseCase parsingUseCase = mock(ScenarioParsingUseCase.class);
    private final SubmitScenarioUseCase submitUseCase = mock(SubmitScenarioUseCase.class);

    private MockMvc mockMvc;

    private static final String EXEC_ID = "exec-001";
    private static final String SCENARIO_ID = "sc-001";
    private static final Instant NOW = Instant.parse("2026-06-23T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(executionRepository.findAllTaskResults(any())).thenReturn(Map.of());
        when(progressCalculator.calculate(any(), any())).thenReturn(new ExecutionProgress(0, 0, 0, 0));
        var controller = new ExecutionController(
                listUseCase, deleteUseCase, executionRepository, progressCalculator,
                parsingUseCase, submitUseCase);
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
        void includesConfigurationForPendingStepsAndPerAgentMetricsForCompletedSteps() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            var completed = TaskId.of("load");
            var pending = TaskId.of("assert");
            var configuration = Map.<String, Object>of("parameters",
                    Map.of("protocol", "http", "loadModel", Map.of("type", "AT_ONCE", "users", 100)));
            var state = new ExecutionState(executionId, ScenarioId.of(SCENARIO_ID), ExecutionStatus.RUNNING,
                    Map.of(), ExecutionContext.initial(executionId, ScenarioId.of(SCENARIO_ID)), NOW, NOW, 2,
                    List.of(new ExecutionTask(completed, "gatling", Phase.INJECTION, true, configuration),
                            new ExecutionTask(pending, "assertion", Phase.ASSERTION, false, configuration)),
                    "# exact source\nscenario:\n  id: sc-001\n");
            when(executionRepository.findById(executionId)).thenReturn(Optional.of(state));
            when(executionRepository.findAllTaskResults(executionId)).thenReturn(Map.of(completed,
                    Map.of(AgentId.of("agent-1"), TaskResult.success(completed, "gatling",
                            Duration.ofMillis(1200), Map.of("result", Map.of("totalRequests", 1000))),
                           AgentId.of("agent-2"), TaskResult.success(completed, "gatling",
                            Duration.ofMillis(1300), Map.of("result", Map.of("totalRequests", 2000))))));

            mockMvc.perform(get("/api/v1/executions/{id}/tasks", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(3))
                    .andExpect(jsonPath("$.tasks[0].agentId").isNotEmpty())
                    .andExpect(jsonPath("$.tasks[0].durationMillis").isNumber())
                    .andExpect(jsonPath("$.tasks[0].completedAt").isNotEmpty())
                    .andExpect(jsonPath("$.tasks[0].outputs.result.totalRequests").isNumber())
                    .andExpect(jsonPath("$.tasks[0].configuration.parameters.protocol").value("http"))
                    .andExpect(jsonPath("$.tasks[2].status").value("PENDING"))
                    .andExpect(jsonPath("$.tasks[2].configuration.parameters.loadModel.users").value(100));

            mockMvc.perform(get("/api/v1/executions/{id}/scenario", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.yaml").value(state.scenarioYaml()));
        }

        @Test
        void scenarioSourceDistinguishesMissingExecutionFromUnavailableSource() throws Exception {
            when(executionRepository.findById(ExecutionId.of(EXEC_ID))).thenReturn(Optional.empty());
            mockMvc.perform(get("/api/v1/executions/{id}/scenario", EXEC_ID))
                    .andExpect(status().isNotFound());

            when(executionRepository.findById(ExecutionId.of(EXEC_ID)))
                    .thenReturn(Optional.of(buildState(ExecutionId.of(EXEC_ID), ExecutionStatus.COMPLETED)));
            mockMvc.perform(get("/api/v1/executions/{id}/scenario", EXEC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.yaml").doesNotExist());
        }

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

    @Nested
    @DisplayName("POST /executions/{id}/rerun")
    class RerunExecution {

        private static final String SOURCE_YAML = "scenario:\n  id: sc-001\n";

        @Test
        @DisplayName("should return 202 with the new execution id")
        void shouldReturn202WithNewExecutionId() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            var state = buildTerminalStateWithYaml(executionId, ExecutionStatus.COMPLETED, SOURCE_YAML);
            var scenario = scenarioDefinition(SOURCE_YAML);
            when(executionRepository.findById(executionId)).thenReturn(Optional.of(state));
            when(parsingUseCase.parse(SOURCE_YAML)).thenReturn(scenario);
            when(submitUseCase.submit(scenario)).thenReturn(ExecutionId.of("exec-002"));

            mockMvc.perform(post("/api/v1/executions/{id}/rerun", EXEC_ID))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-002"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));

            verify(parsingUseCase).parse(SOURCE_YAML);
            verify(submitUseCase).submit(scenario);
        }

        @Test
        @DisplayName("should return 404 when execution does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            when(executionRepository.findById(ExecutionId.of(EXEC_ID))).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/executions/{id}/rerun", EXEC_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("EXECUTION_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return 409 when execution is still active")
        void shouldReturn409WhenActive() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            when(executionRepository.findById(executionId))
                    .thenReturn(Optional.of(buildState(executionId, ExecutionStatus.RUNNING)));

            mockMvc.perform(post("/api/v1/executions/{id}/rerun", EXEC_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("EXECUTION_STILL_ACTIVE"));
        }

        @Test
        @DisplayName("should return 409 when the source YAML was not retained")
        void shouldReturn409WhenSourceUnavailable() throws Exception {
            var executionId = ExecutionId.of(EXEC_ID);
            when(executionRepository.findById(executionId))
                    .thenReturn(Optional.of(buildState(executionId, ExecutionStatus.COMPLETED)));

            mockMvc.perform(post("/api/v1/executions/{id}/rerun", EXEC_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SCENARIO_SOURCE_UNAVAILABLE"));
        }
    }

    // ---- Helpers ----

    private static ScenarioDefinition scenarioDefinition(String yaml) {
        return new ScenarioDefinition(
                ScenarioId.of(SCENARIO_ID), "scenario", "1.0.0",
                List.of(), Map.of(), ExecutionMode.LOCAL, List.of(), Map.of(), yaml);
    }

    private static ExecutionState buildTerminalStateWithYaml(ExecutionId executionId,
                                                             ExecutionStatus status,
                                                             String yaml) {
        return new ExecutionState(
                executionId,
                ScenarioId.of(SCENARIO_ID),
                status,
                Map.of(),
                ExecutionContext.initial(executionId, ScenarioId.of(SCENARIO_ID)),
                NOW,
                NOW,
                0,
                List.of(),
                yaml);
    }

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
