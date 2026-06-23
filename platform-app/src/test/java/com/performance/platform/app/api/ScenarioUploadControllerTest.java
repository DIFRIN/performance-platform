package com.performance.platform.app.api;

import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.exception.InvalidScenarioException;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.scenario.usecase.ScenarioValidationException;
import com.performance.platform.scenario.validation.ValidationError;
import com.performance.platform.scenario.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ScenarioUploadController")
class ScenarioUploadControllerTest {

    private final ScenarioParsingUseCase parsingUseCase = mock(ScenarioParsingUseCase.class);
    private final ExecuteScenarioUseCase executeUseCase = mock(ExecuteScenarioUseCase.class);

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
        var controller = new ScenarioUploadController(parsingUseCase, executeUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /scenarios/upload — multipart file")
    class MultipartUpload {

        @Test
        @DisplayName("should accept valid YAML file and return 202 with executionId")
        void shouldAcceptValidYamlFile() throws Exception {
            var executionId = ExecutionId.of("exec-001");
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);

            var file = new MockMultipartFile("file", "scenario.yaml",
                    MediaType.TEXT_PLAIN_VALUE, VALID_YAML.getBytes());

            mockMvc.perform(multipart("/api/v1/scenarios/upload")
                            .file(file))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-001"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("should return 400 with field-level errors when YAML file validation fails")
        void shouldReturn400OnFileValidationFailure() throws Exception {
            var validationResult = ValidationResult.invalid(
                    List.of(new ValidationError("name", "must not be blank", "scenario.name")),
                    List.of());
            var validationEx = new ScenarioValidationException(validationResult);
            when(parsingUseCase.parse(any())).thenThrow(validationEx);

            var file = new MockMultipartFile("file", "invalid.yaml",
                    MediaType.TEXT_PLAIN_VALUE, "invalid yaml".getBytes());

            mockMvc.perform(multipart("/api/v1/scenarios/upload")
                            .file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("name"))
                    .andExpect(jsonPath("$.details[0].message").value("must not be blank"))
                    .andExpect(jsonPath("$.details[0].path").value("scenario.name"));
        }

        @Test
        @DisplayName("should return 400 when YAML file parsing fails")
        void shouldReturn400OnFileParsingFailure() throws Exception {
            var parsingEx = new ScenarioParsingException("Invalid YAML",
                    List.of("Unexpected token at line 3"));
            when(parsingUseCase.parse(any())).thenThrow(parsingEx);

            var file = new MockMultipartFile("file", "bad.yaml",
                    MediaType.TEXT_PLAIN_VALUE, "bad: [yaml".getBytes());

            mockMvc.perform(multipart("/api/v1/scenarios/upload")
                            .file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_PARSING_FAILED"))
                    .andExpect(jsonPath("$.details[0]").value("Unexpected token at line 3"));
        }
    }

    @Nested
    @DisplayName("POST /scenarios/upload — textarea yaml parameter")
    class TextareaUpload {

        @Test
        @DisplayName("should accept valid YAML text parameter and return 202 with executionId")
        void shouldAcceptValidYamlText() throws Exception {
            var executionId = ExecutionId.of("exec-002");
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario);
            when(executeUseCase.execute(scenario)).thenReturn(executionId);

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", VALID_YAML))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-002"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("should return 400 with field-level errors when text YAML validation fails")
        void shouldReturn400OnTextValidationFailure() throws Exception {
            var validationResult = ValidationResult.invalid(
                    List.of(new ValidationError("version", "must match semver pattern", "scenario.version")),
                    List.of());
            var validationEx = new ScenarioValidationException(validationResult);
            when(parsingUseCase.parse(any())).thenThrow(validationEx);

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", "scenario:\n  version: not-semver"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("version"))
                    .andExpect(jsonPath("$.details[0].message").value("must match semver pattern"))
                    .andExpect(jsonPath("$.details[0].path").value("scenario.version"));
        }

        @Test
        @DisplayName("should return 400 when text YAML parsing fails")
        void shouldReturn400OnTextParsingFailure() throws Exception {
            var parsingEx = new ScenarioParsingException("Invalid YAML",
                    List.of("Cannot parse"));
            when(parsingUseCase.parse(any())).thenThrow(parsingEx);

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", "bad: [yaml"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_PARSING_FAILED"));
        }
    }

    @Nested
    @DisplayName("POST /scenarios/upload — missing input")
    class MissingInput {

        @Test
        @DisplayName("should return 400 when neither file nor yaml is provided")
        void shouldReturn400WhenNoInput() throws Exception {
            mockMvc.perform(post("/api/v1/scenarios/upload"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("input"))
                    .andExpect(jsonPath("$.details[0].message").value("Either 'file' or 'yaml' parameter is required"));
        }

        @Test
        @DisplayName("should return 400 when yaml parameter is blank")
        void shouldReturn400WhenYamlBlank() throws Exception {
            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", "   "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("input"));
        }
    }

    @Nested
    @DisplayName("POST /scenarios/upload — exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("should return 500 when execution fails after valid upload")
        void shouldReturn500OnExecutionFailure() throws Exception {
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(any())).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new ExecutionException("Engine not available"));

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", VALID_YAML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("EXECUTION_FAILED"));
        }

        @Test
        @DisplayName("should return 503 when no agent is available")
        void shouldReturn503OnNoAgent() throws Exception {
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(any())).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new NoAvailableAgentException("gatling"));

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", VALID_YAML))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("NO_AGENT_AVAILABLE"));
        }

        @Test
        @DisplayName("should return 400 on invalid scenario")
        void shouldReturn400OnInvalidScenario() throws Exception {
            var scenario = createMinimalScenario();
            when(parsingUseCase.parse(any())).thenReturn(scenario);
            when(executeUseCase.execute(scenario))
                    .thenThrow(new InvalidScenarioException("Missing required field"));

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", VALID_YAML))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_SCENARIO"));
        }

        @Test
        @DisplayName("should return 500 on unexpected exceptions")
        void shouldReturn500OnUnknownException() throws Exception {
            when(parsingUseCase.parse(any())).thenThrow(new RuntimeException("Boom"));

            mockMvc.perform(post("/api/v1/scenarios/upload")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("yaml", VALID_YAML))
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
