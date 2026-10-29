package com.performance.platform.e2e;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.ExecutionTask;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.infrastructure.persistence.ExecutionStateEntity;
import com.performance.platform.infrastructure.persistence.mapper.ExecutionStateMapper;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.reporting.ReportOutput;
import com.performance.platform.reporting.engine.DefaultReportEngine;
import com.performance.platform.scenario.parser.YamlScenarioParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.mock;

class LocalModeReportFixtureTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void allScenarioStepsRemainInTheReportAfterJsonRoundTrip(boolean includePlannedTasks) throws Exception {
        String yaml;
        try (var input = Objects.requireNonNull(
                getClass().getResourceAsStream("/e2e-local-multi-tasks.yaml"))) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var scenario = new YamlScenarioParser().parse(yaml);
        var id = ExecutionId.generate();
        var context = ExecutionContext.initial(id, scenario.id());
        var executors = Map.<String, TaskExecutor>of(
                "mock-server", new LocalModeAllTasksIT.StubTaskExecutor("mock-server",
                        Map.of("url", "http://localhost:0")),
                "gatling", LocalModeAllTasksIT.createGatlingStub(),
                "http-mock", new LocalModeAllTasksIT.StubAssertionExecutor("http-mock"),
                "gatling-metric", new LocalModeAllTasksIT.StubAssertionExecutor("gatling-metric"));
        for (var step : scenario.steps()) {
            var result = executors.get(step.taskName()).execute(context, step);
            context = context.with(step.id().value(), "agent-local", result);
        }
        var tasks = includePlannedTasks ? scenario.steps().stream()
                .map(step -> new ExecutionTask(step.id(), step.taskName(), step.phase(), true))
                .toList() : List.<ExecutionTask>of();
        var now = Instant.now();
        var state = new ExecutionState(id, scenario.id(), ExecutionStatus.COMPLETED, Map.of(),
                context, now, now, 4, tasks);
        var engine = new DefaultReportEngine(mock(ExecutionRepository.class), event -> { },
                mock(ReportOutput.class));
        LocalModeAllTasksIT.assertReportContainsAllScenarioSteps(engine.generate(state));

        var mapper = new ExecutionStateMapper();
        var entity = mapper.toEntity(state);
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        Map<String, Object> reloadedContext = json.readValue(
                json.writeValueAsBytes(entity.context()), new TypeReference<>() { });
        var reloaded = mapper.toDomain(new ExecutionStateEntity(
                entity.id(), entity.scenarioId(), entity.status(), entity.phases(), reloadedContext,
                entity.startedAt(), entity.updatedAt(), entity.plannedTaskCount(), entity.tasks(),
                entity.scenarioYaml(), entity.scenarioDetails()));

        LocalModeAllTasksIT.assertReportContainsAllScenarioSteps(engine.generate(reloaded));
    }
}
