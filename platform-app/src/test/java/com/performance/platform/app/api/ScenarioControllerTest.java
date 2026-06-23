package com.performance.platform.app.api;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.ports.in.CancelExecutionUseCase;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GenerateReportUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionProgress;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.scenario.usecase.ScenarioValidationException;
import com.performance.platform.scenario.validation.ValidationError;
import com.performance.platform.scenario.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ScenarioController")
class ScenarioControllerTest {

    private final ScenarioParsingUseCase parsingUseCase = mock(ScenarioParsingUseCase.class);
    private final ExecuteScenarioUseCase executeUseCase = mock(ExecuteScenarioUseCase.class);
    private final GetExecutionStatusUseCase statusUseCase = mock(GetExecutionStatusUseCase.class);
    private final CancelExecutionUseCase cancelUseCase = mock(CancelExecutionUseCase.class);
    private final GenerateReportUseCase reportUseCase = mock(GenerateReportUseCase.class);
    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final ExecutionProgressCalculator progressCalculator = mock(ExecutionProgressCalculator.class);

    private MockMvc mockMvc;

    private static final String VALID_YAML = """
            scenario:
              id: test-scenario
              name: Test
              version: "1.0.0"
              executionMode: LOCAL
              steps:
                - id: step1
                  phase: INJECTION
                  taskName: gatling
            """;

    @BeforeEach
    void setUp() {
        when(executionRepository.findAllTaskResults(any())).thenReturn(Map.of());
        when(progressCalculator.calculate(any(), any())).thenReturn(new ExecutionProgress(0, 0, 0, 0));
        var controller = new ScenarioController(
                parsingUseCase, executeUseCase, statusUseCase,
                cancelUseCase, reportUseCase, executionRepository, progressCalculator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /scenarios")
    class SubmitScenario {

        @Test
        @DisplayName("should accept valid YAML and return 202 with executionId")
        void shouldAcceptValidYaml() throws Exception {
            var executionId = ExecutionId.of("exec-001");
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(VALID_YAML))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-001"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("should return 400 with field-level errors when scenario validation fails")
        void shouldReturn400OnValidationFailure() throws Exception {
            var validationResult = ValidationResult.invalid(
                    List.of(new ValidationError("name", "must not be blank", "scenario.name")),
                    List.of());
            var validationEx = new ScenarioValidationException(validationResult);
            when(parsingUseCase.parse(any())).thenThrow(validationEx);

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("invalid yaml"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("name"))
                    .andExpect(jsonPath("$.details[0].message").value("must not be blank"))
                    .andExpect(jsonPath("$.details[0].path").value("scenario.name"));
        }

        @Test
        @DisplayName("should return 400 when YAML parsing fails")
        void shouldReturn400OnParsingFailure() throws Exception {
            var parsingEx = new ScenarioParsingException("Invalid YAML",
                    List.of("Unexpected token at line 3"));
            when(parsingUseCase.parse(any())).thenThrow(parsingEx);

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("bad: [yaml"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_PARSING_FAILED"))
                    .andExpect(jsonPath("$.details[0]").value("Unexpected token at line 3"));
        }

        @Test
        @DisplayName("should return 500 when execution fails")
        void shouldReturn500OnExecutionFailure() throws Exception {
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(any())).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new ExecutionException("Engine not available"));

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(VALID_YAML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("EXECUTION_FAILED"));
        }
    }

    @Nested
    @DisplayName("GET /executions/{id}")
    class GetStatus {

        @Test
        @DisplayName("should return 200 with full status when state is available")
        void shouldReturnFullStatus() throws Exception {
            var executionId = ExecutionId.of("exec-001");
            var now = Instant.parse("2026-06-20T12:00:00Z");
            var state = new ExecutionState(
                    executionId,
                    ScenarioId.of("sc-001"),
                    ExecutionStatus.RUNNING,
                    Map.of(Phase.PREPARATION, PhaseStatus.COMPLETED,
                            Phase.INJECTION, PhaseStatus.RUNNING,
                            Phase.ASSERTION, PhaseStatus.PENDING),
                    ExecutionContext.initial(executionId, ScenarioId.of("sc-001")),
                    now,
                    now);

            when(statusUseCase.getStatus(executionId)).thenReturn(ExecutionStatus.RUNNING);
            when(statusUseCase.getState(executionId)).thenReturn(Optional.of(state));
            when(progressCalculator.calculate(any(), any())).thenReturn(new ExecutionProgress(3, 1, 0, 2));

            mockMvc.perform(get("/api/v1/executions/exec-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value("exec-001"))
                    .andExpect(jsonPath("$.scenarioId").value("sc-001"))
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.phaseStatuses.PREPARATION").value("COMPLETED"))
                    .andExpect(jsonPath("$.phaseStatuses.INJECTION").value("RUNNING"))
                    .andExpect(jsonPath("$.phaseStatuses.ASSERTION").value("PENDING"))
                    .andExpect(jsonPath("$.progress.total").value(3))
                    .andExpect(jsonPath("$.progress.ok").value(1))
                    .andExpect(jsonPath("$.progress.ko").value(0))
                    .andExpect(jsonPath("$.progress.running").value(2));
        }

        @Test
        @DisplayName("should return 200 with basic status when state is absent")
        void shouldReturnBasicStatusWhenStateAbsent() throws Exception {
            var executionId = ExecutionId.of("exec-002");
            when(statusUseCase.getStatus(executionId)).thenReturn(ExecutionStatus.CANCELLED);
            when(statusUseCase.getState(executionId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/executions/exec-002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executionId").value("exec-002"))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    @Nested
    @DisplayName("POST /executions/{id}/cancel")
    class CancelExecution {

        @Test
        @DisplayName("should return 202 when cancellation is accepted")
        void shouldReturn202OnCancel() throws Exception {
            mockMvc.perform(post("/api/v1/executions/exec-001/cancel"))
                    .andExpect(status().isAccepted());

            verify(cancelUseCase).cancel(ExecutionId.of("exec-001"));
        }
    }

    @Nested
    @DisplayName("GET /executions/{id}/report")
    class GetReport {

        @Test
        @DisplayName("should return 200 with reportId when report is generated")
        void shouldReturnReportId() throws Exception {
            var executionId = ExecutionId.of("exec-001");
            var reportId = ReportId.generate();
            when(reportUseCase.generate(executionId)).thenReturn(reportId);

            mockMvc.perform(get("/api/v1/executions/exec-001/report"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportId").value(reportId.value()));
        }

        @Test
        @DisplayName("should return 500 when report generation fails")
        void shouldReturn500OnReportFailure() throws Exception {
            var executionId = ExecutionId.of("exec-001");
            when(reportUseCase.generate(executionId))
                    .thenThrow(new ReportGenerationException("Renderer not found", null));

            mockMvc.perform(get("/api/v1/executions/exec-001/report"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("REPORT_GENERATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("Exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("should return 503 when no agent is available")
        void shouldReturn503OnNoAgent() throws Exception {
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(any())).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new com.performance.platform.application.exception
                            .NoAvailableAgentException("gatling"));

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(VALID_YAML))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("NO_AGENT_AVAILABLE"));
        }

        @Test
        @DisplayName("should return 400 on generic invalid scenario")
        void shouldReturn400OnInvalidScenario() throws Exception {
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(any())).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new com.performance.platform.application.exception
                            .InvalidScenarioException("Missing required field"));

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(VALID_YAML))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_SCENARIO"));
        }

        @Test
        @DisplayName("should return 500 on unexpected exceptions")
        void shouldReturn500OnUnknownException() throws Exception {
            when(parsingUseCase.parse(any())).thenThrow(new RuntimeException("Boom"));

            mockMvc.perform(post("/api/v1/scenarios")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(VALID_YAML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"));
        }
    }

    private static ScenarioDefinition createMinimalScenario() {
        return new ScenarioDefinition(
                ScenarioId.of("test-scenario"),
                "Test",
                "1.0.0",
                List.of(),
                Map.of(),
                null,
                List.of(),
                Map.of());
    }
}
