package com.performance.platform.app.api;

import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.campaign.ExecutionEngine;
import com.performance.platform.campaign.plan.DefaultExecutionPlanBuilder;
import com.performance.platform.scenario.parser.YamlScenarioParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScenarioSubmissionTest {

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void bothSubmissionEndpointsReturnAcceptedBeforeExecutionCompletes() throws Exception {
        String yaml = """
                scenario:
                  id: async-web
                  name: Async web
                  version: "1.0.0"
                  execution: {mode: LOCAL}
                  steps:
                    - id: load
                      task: inject
                      phase: INJECTION
                    - id: verify
                      task: check
                      phase: ASSERTION
                """;
        var parsing = mock(ScenarioParsingUseCase.class);
        when(parsing.parse(yaml)).thenReturn(new YamlScenarioParser().parse(yaml));
        var repository = mock(ExecutionRepository.class);
        Map<String, ExecutionState> states = new ConcurrentHashMap<>();
        doAnswer(call -> {
            ExecutionState state = call.getArgument(0);
            states.put(state.id().value(), state);
            return null;
        }).when(repository).save(any());
        when(repository.findById(any())).thenAnswer(call -> {
            ExecutionId id = call.getArgument(0);
            return Optional.ofNullable(states.get(id.value()));
        });
        when(repository.findAllTaskResults(any())).thenReturn(Map.of());
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(2);
        var engine = new ExecutionEngine(new DefaultExecutionPlanBuilder(), repository, event -> {
            if (event instanceof ScenarioFinished) finished.countDown();
        }, signal -> {}, (step, context) -> {
            if (step.step().taskName().equals("inject")) {
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Execution not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return TaskResult.success(step.step().id(), step.step().taskName(), Duration.ZERO, Map.of());
        }, (id, outcomes, phase) -> {});
        var mvc = MockMvcBuilders.standaloneSetup(
                new ScenarioController(parsing, engine, null, engine, repository, new ExecutionProgressCalculator()),
                new ScenarioUploadController(parsing, engine),
                new ExecutionController(null, null, repository, new ExecutionProgressCalculator())).build();

        try {
            mvc.perform(post("/api/v1/scenarios").contentType(MediaType.TEXT_PLAIN).content(yaml))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").isNotEmpty());
            mvc.perform(post("/api/v1/scenarios/upload").param("yaml", yaml))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").isNotEmpty());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(2, states.size());
            for (var state : states.values()) {
                assertEquals(ExecutionStatus.RUNNING, state.status());
                mvc.perform(get("/api/v1/executions/" + state.id().value() + "/tasks"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tasks[0].status").value("RUNNING"))
                        .andExpect(jsonPath("$.tasks[1].status").value("PENDING"));
            }
        } finally {
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
        }
    }
}
