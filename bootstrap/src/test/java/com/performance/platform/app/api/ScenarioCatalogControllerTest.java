package com.performance.platform.app.api;

import com.performance.platform.app.catalog.CatalogScenario;
import com.performance.platform.app.catalog.ScenarioCatalogService;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ScenarioCatalogController")
class ScenarioCatalogControllerTest {

    private final ScenarioCatalogService catalogService = mock(ScenarioCatalogService.class);
    private final SubmitScenarioUseCase submitUseCase = mock(SubmitScenarioUseCase.class);

    private MockMvc mockMvc;

    private static final String KEY = "scenario.yaml";
    private static final String SCENARIO_ID = "sc-001";

    @BeforeEach
    void setUp() {
        var controller = new ScenarioCatalogController(catalogService, submitUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /catalog")
    class ListCatalog {

        @Test
        @DisplayName("should return scenarios with their steps without executing them")
        void shouldReturnScenariosWithSteps() throws Exception {
            var definition = definition();
            when(catalogService.list()).thenReturn(List.of(
                    new CatalogScenario(KEY, "yaml", definition, null)));

            mockMvc.perform(get("/api/v1/catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].key").value(KEY))
                    .andExpect(jsonPath("$[0].scenarioId").value(SCENARIO_ID))
                    .andExpect(jsonPath("$[0].name").value("Scenario"))
                    .andExpect(jsonPath("$[0].version").value("1.0.0"))
                    .andExpect(jsonPath("$[0].executionMode").value("LOCAL"))
                    .andExpect(jsonPath("$[0].stepCount").value(1))
                    .andExpect(jsonPath("$[0].valid").value(true))
                    .andExpect(jsonPath("$[0].yaml").value("yaml"))
                    .andExpect(jsonPath("$[0].steps[0].id").value("prep"))
                    .andExpect(jsonPath("$[0].steps[0].taskName").value("mock-server"))
                    .andExpect(jsonPath("$[0].steps[0].phase").value("PREPARATION"))
                    .andExpect(jsonPath("$[0].steps[0].timeoutMillis").value(30000))
                    .andExpect(jsonPath("$[0].steps[0].parameters.port").value(8090));
        }

        @Test
        @DisplayName("should return invalid entries with their error and no steps")
        void shouldReturnInvalidEntries() throws Exception {
            when(catalogService.list()).thenReturn(List.of(
                    new CatalogScenario("broken.yaml", "yaml", null, "scenario.id is required")));

            mockMvc.perform(get("/api/v1/catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].key").value("broken.yaml"))
                    .andExpect(jsonPath("$[0].valid").value(false))
                    .andExpect(jsonPath("$[0].error").value("scenario.id is required"))
                    .andExpect(jsonPath("$[0].yaml").value("yaml"))
                    .andExpect(jsonPath("$[0].steps").isEmpty());
        }

        @Test
        @DisplayName("should return an empty list when the catalog is empty")
        void shouldReturnEmptyList() throws Exception {
            when(catalogService.list()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/catalog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /catalog/{key}/run")
    class RunCatalogScenario {

        @Test
        @DisplayName("should submit the catalog scenario and return 202")
        void shouldSubmitScenario() throws Exception {
            var definition = definition();
            when(catalogService.find(KEY))
                    .thenReturn(Optional.of(new CatalogScenario(KEY, "yaml", definition, null)));
            when(submitUseCase.submit(definition)).thenReturn(ExecutionId.of("exec-001"));

            mockMvc.perform(post("/api/v1/catalog/{key}/run", KEY))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-001"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));

            verify(submitUseCase).submit(definition);
        }

        @Test
        @DisplayName("should return 404 when the key is unknown")
        void shouldReturn404WhenUnknown() throws Exception {
            when(catalogService.find("missing.yaml")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/catalog/{key}/run", "missing.yaml"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SCENARIO_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return 400 when the catalog entry is invalid")
        void shouldReturn400WhenInvalid() throws Exception {
            when(catalogService.find(KEY)).thenReturn(Optional.of(
                    new CatalogScenario(KEY, "yaml", null, "scenario.id is required")));

            mockMvc.perform(post("/api/v1/catalog/{key}/run", KEY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SCENARIO_INVALID"))
                    .andExpect(jsonPath("$.message").value("scenario.id is required"));
        }
    }

    private static ScenarioDefinition definition() {
        var step = new StepDefinition(
                TaskId.of("prep"),
                "mock-server",
                Phase.PREPARATION,
                Map.of("port", 8090),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                null);
        return new ScenarioDefinition(
                ScenarioId.of(SCENARIO_ID),
                "Scenario",
                "1.0.0",
                List.of("test"),
                Map.of(),
                ExecutionMode.LOCAL,
                List.of(step),
                Map.of(),
                "yaml");
    }
}
