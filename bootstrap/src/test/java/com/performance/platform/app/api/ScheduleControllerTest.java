package com.performance.platform.app.api;

import com.performance.platform.application.ports.in.CancelScheduledRunUseCase;
import com.performance.platform.application.ports.in.ListScheduledRunsUseCase;
import com.performance.platform.application.ports.in.ManageScheduledRunUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.ScheduleScenarioUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ScheduleCalculator;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.application.schedule.ScheduleKind;
import com.performance.platform.application.schedule.ScheduledRun;
import com.performance.platform.infrastructure.scheduling.SpringScheduleCalculator;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ScheduleController")
class ScheduleControllerTest {

    private static final String VALID_YAML = """
            scenario:
              id: test-scenario
              name: Test
              version: "1.0.0"
              steps:
                - id: step1
                  phase: INJECTION
                  taskName: gatling
            """;

    private final ScenarioParsingUseCase parsingUseCase = mock(ScenarioParsingUseCase.class);
    private final SubmitScenarioUseCase submitUseCase = mock(SubmitScenarioUseCase.class);
    private final ScheduleScenarioUseCase scheduleUseCase = mock(ScheduleScenarioUseCase.class);
    private final ListScheduledRunsUseCase listUseCase = mock(ListScheduledRunsUseCase.class);
    private final CancelScheduledRunUseCase cancelUseCase = mock(CancelScheduledRunUseCase.class);
    private final ManageScheduledRunUseCase manageUseCase = mock(ManageScheduledRunUseCase.class);
    private final ScheduleCalculator calculator = new SpringScheduleCalculator();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new ScheduleController(parsingUseCase, submitUseCase, scheduleUseCase,
                listUseCase, cancelUseCase, manageUseCase, calculator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        when(parsingUseCase.parse(VALID_YAML)).thenReturn(scenario());
    }

    @Nested
    @DisplayName("POST /schedules")
    class Submit {

        @Test
        @DisplayName("runs immediately when the expression is empty or 'now'")
        void shouldRunImmediately() throws Exception {
            when(submitUseCase.submit(any())).thenReturn(ExecutionId.of("exec-001"));

            mockMvc.perform(post("/api/v1/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("now")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-001"))
                    .andExpect(jsonPath("$.scheduleId").doesNotExist())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("persists a deferred run and returns its schedule id and next occurrence")
        void shouldScheduleDeferredRun() throws Exception {
            var run = ScheduledRun.pending(ScheduledRunId.of("sched-001"), ScenarioId.of("test-scenario"),
                    VALID_YAML, ScheduleKind.ONCE, "in 10m",
                    Instant.parse("2099-01-01T00:00:00Z"), Instant.parse("2026-11-12T20:00:00Z"));
            when(scheduleUseCase.schedule(any(), any())).thenReturn(run);

            mockMvc.perform(post("/api/v1/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("in 10m")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.scheduleId").value("sched-001"))
                    .andExpect(jsonPath("$.status").value("SCHEDULED"))
                    .andExpect(jsonPath("$.nextRunAt").value("2099-01-01T00:00:00Z"));
        }

        @Test
        @DisplayName("returns 400 when the schedule expression is invalid")
        void shouldRejectInvalidSchedule() throws Exception {
            mockMvc.perform(post("/api/v1/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("in soon")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
        }

        @Test
        @DisplayName("returns 400 when the scenario YAML is missing")
        void shouldRejectMissingYaml() throws Exception {
            mockMvc.perform(post("/api/v1/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"schedule\":\"in 10m\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
        }
    }

    @Nested
    @DisplayName("GET /schedules")
    class ListRuns {

        @Test
        @DisplayName("returns the scheduled runs")
        void shouldListScheduledRuns() throws Exception {
            var run = ScheduledRun.pending(ScheduledRunId.of("sched-001"), ScenarioId.of("test-scenario"),
                    VALID_YAML, ScheduleKind.CRON, "0 0 2 * * *",
                    Instant.parse("2099-01-01T00:00:00Z"), Instant.parse("2026-11-12T20:00:00Z"));
            when(listUseCase.list(0)).thenReturn(List.of(run));

            mockMvc.perform(get("/api/v1/schedules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].scheduleId").value("sched-001"))
                    .andExpect(jsonPath("$[0].kind").value("CRON"))
                    .andExpect(jsonPath("$[0].schedule").value("0 0 2 * * *"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("DELETE /schedules/{id}")
    class Cancel {

        @Test
        @DisplayName("returns 204 and delegates the cancellation")
        void shouldCancelScheduledRun() throws Exception {
            mockMvc.perform(delete("/api/v1/schedules/sched-001"))
                    .andExpect(status().isNoContent());

            verify(cancelUseCase).cancel(eq(ScheduledRunId.of("sched-001")));
        }
    }

    @Nested
    @DisplayName("PUT /schedules/{id}")
    class Reschedule {

        @Test
        @DisplayName("returns the updated run")
        void shouldReschedule() throws Exception {
            var run = ScheduledRun.pending(ScheduledRunId.of("sched-001"), ScenarioId.of("test-scenario"),
                    VALID_YAML, ScheduleKind.CRON, "0 0 2 * * *",
                    Instant.parse("2099-01-01T00:00:00Z"), Instant.parse("2026-11-12T20:00:00Z"));
            when(manageUseCase.reschedule(ScheduledRunId.of("sched-001"), "cron:0 0 2 * * *"))
                    .thenReturn(run);

            mockMvc.perform(put("/api/v1/schedules/sched-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"schedule\":\"cron:0 0 2 * * *\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scheduleId").value("sched-001"))
                    .andExpect(jsonPath("$.schedule").value("0 0 2 * * *"))
                    .andExpect(jsonPath("$.nextRunAt").value("2099-01-01T00:00:00Z"));
        }
    }

    @Nested
    @DisplayName("POST /schedules/{id}/run")
    class RunNow {

        @Test
        @DisplayName("returns the created execution id")
        void shouldRunNow() throws Exception {
            when(manageUseCase.runNow(ScheduledRunId.of("sched-001"))).thenReturn("exec-001");

            mockMvc.perform(post("/api/v1/schedules/sched-001/run"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.executionId").value("exec-001"))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }
    }

    private static String body(String schedule) {
        return "{\"scenarioYaml\":\"" + VALID_YAML.replace("\n", "\\n").replace("\"", "\\\"")
                + "\",\"schedule\":\"" + schedule + "\"}";
    }

    private static ScenarioDefinition scenario() {
        return new ScenarioDefinition(
                ScenarioId.of("test-scenario"), "Test", "1.0.0",
                List.of(), Map.of(), List.of(), VALID_YAML, null);
    }
}
