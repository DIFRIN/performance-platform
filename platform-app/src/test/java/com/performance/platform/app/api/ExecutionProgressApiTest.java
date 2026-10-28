package com.performance.platform.app.api;

import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExecutionProgressApiTest {

    @Test
    void listsCompletedRunningAndPendingTasksWithTheirPhases() throws Exception {
        var id = ExecutionId.generate();
        var scenarioId = ScenarioId.of("device-api-local");
        var now = Instant.now();
        var tasks = List.of(
                new com.performance.platform.domain.execution.ExecutionTask(
                        TaskId.of("warmup"), "http-client", Phase.PREPARATION, true),
                new com.performance.platform.domain.execution.ExecutionTask(
                        TaskId.of("load-test"), "gatling", Phase.INJECTION, true),
                new com.performance.platform.domain.execution.ExecutionTask(
                        TaskId.of("assert-db-intact"), "database-assertion", Phase.ASSERTION, false));
        var state = new ExecutionState(id, scenarioId, ExecutionStatus.RUNNING, Map.of(),
                ExecutionContext.initial(id, scenarioId), now, now, 3, tasks);
        var repository = mock(ExecutionRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(state));
        when(repository.findAllTaskResults(id)).thenReturn(Map.of(TaskId.of("warmup"),
                Map.of(AgentId.of("local"), TaskResult.success(
                        TaskId.of("warmup"), "http-client", Duration.ZERO, Map.of()))));
        var mvc = MockMvcBuilders.standaloneSetup(
                new ExecutionController(null, null, repository, new ExecutionProgressCalculator())).build();

        mvc.perform(get("/api/v1/executions/" + id.value() + "/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.tasks[0].taskId").value("warmup"))
                .andExpect(jsonPath("$.tasks[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.tasks[0].phase").value("PREPARATION"))
                .andExpect(jsonPath("$.tasks[1].taskId").value("load-test"))
                .andExpect(jsonPath("$.tasks[1].status").value("RUNNING"))
                .andExpect(jsonPath("$.tasks[1].phase").value("INJECTION"))
                .andExpect(jsonPath("$.tasks[2].status").value("PENDING"))
                .andExpect(jsonPath("$.tasks[2].phase").value("ASSERTION"));
    }

    @Test
    void detailAndListIncludeStepsWithoutResultsWhileInjectionIsRunning() throws Exception {
        var id = ExecutionId.generate();
        var scenarioId = ScenarioId.of("device-api-local");
        var now = Instant.now();
        var state = new ExecutionState(id, scenarioId, ExecutionStatus.RUNNING,
                Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED, Phase.INJECTION, PhaseStatus.RUNNING,
                        Phase.ASSERTION, PhaseStatus.PENDING),
                ExecutionContext.initial(id, scenarioId), now, now, 7);
        Map<TaskId, Map<AgentId, TaskResult>> results = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            var taskId = TaskId.of("prep-" + i);
            results.put(taskId, Map.of(AgentId.of("local"),
                    TaskResult.success(taskId, "preparation", Duration.ZERO, Map.of())));
        }
        var repository = mock(ExecutionRepository.class);
        when(repository.findAllTaskResults(id)).thenReturn(results);
        var statusUseCase = mock(GetExecutionStatusUseCase.class);
        when(statusUseCase.getStatus(id)).thenReturn(ExecutionStatus.RUNNING);
        when(statusUseCase.getState(id)).thenReturn(Optional.of(state));
        var listUseCase = mock(ListExecutionsUseCase.class);
        when(listUseCase.list(0)).thenReturn(List.of(state));
        var calculator = new ExecutionProgressCalculator();
        var mvc = MockMvcBuilders.standaloneSetup(
                new ScenarioController(null, null, statusUseCase, null, repository, calculator),
                new ExecutionController(listUseCase, null, repository, calculator)).build();

        mvc.perform(get("/api/v1/executions/" + id.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phaseStatuses.INJECTION").value("RUNNING"))
                .andExpect(jsonPath("$.phaseStatuses.ASSERTION").value("PENDING"))
                .andExpect(jsonPath("$.progress.total").value(7))
                .andExpect(jsonPath("$.progress.ok").value(4))
                .andExpect(jsonPath("$.progress.running").value(3));
        mvc.perform(get("/api/v1/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].progress.total").value(7))
                .andExpect(jsonPath("$[0].progress.ok").value(4))
                .andExpect(jsonPath("$[0].progress.running").value(3));
    }
}
