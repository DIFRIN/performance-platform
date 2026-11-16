package com.performance.platform.campaign.schedule;

import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ScheduleCalculator;
import com.performance.platform.application.ports.out.ScheduledRunRepository;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.application.schedule.ScheduleKind;
import com.performance.platform.application.schedule.ScheduleSpec;
import com.performance.platform.application.schedule.ScheduledRun;
import com.performance.platform.application.schedule.ScheduledRunStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ScheduledRunDispatcher")
class ScheduledRunDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-12T20:00:00Z");
    private static final String YAML = """
            scenario:
              id: scheduled
              name: Scheduled
              version: "1.0"
            """;

    private final ScheduledRunRepository repository = mock(ScheduledRunRepository.class);
    private final ScheduleCalculator calculator = mock(ScheduleCalculator.class);
    private final ScenarioParsingUseCase parsingUseCase = mock(ScenarioParsingUseCase.class);
    private final SubmitScenarioUseCase submitUseCase = mock(SubmitScenarioUseCase.class);
    private final ScheduledRunDispatcher dispatcher =
            new ScheduledRunDispatcher(repository, calculator, parsingUseCase, submitUseCase);

    @Test
    @DisplayName("a due one-shot run is submitted and marked COMPLETED")
    void shouldSubmitOneShotRun() {
        var run = ScheduledRun.pending(ScheduledRunId.of("sched-1"), ScenarioId.of("scheduled"),
                YAML, ScheduleKind.ONCE, "in 1m", NOW.minusSeconds(30), NOW.minusSeconds(60));
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(run));
        when(parsingUseCase.parse(YAML)).thenReturn(scenario());
        when(submitUseCase.submit(any())).thenReturn(ExecutionId.of("exec-9"));

        dispatcher.dispatchDue();

        var saved = ArgumentCaptor.forClass(ScheduledRun.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ScheduledRunStatus.COMPLETED);
        assertThat(saved.getValue().lastExecutionId()).isEqualTo("exec-9");
    }

    @Test
    @DisplayName("a due cron run is rescheduled to its next occurrence")
    void shouldRescheduleCronRun() {
        var run = ScheduledRun.pending(ScheduledRunId.of("sched-2"), ScenarioId.of("scheduled"),
                YAML, ScheduleKind.CRON, "0 0 2 * * *", NOW.minusSeconds(30), NOW.minusSeconds(60));
        var spec = ScheduleSpec.cron("0 0 2 * * *");
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(run));
        when(parsingUseCase.parse(YAML)).thenReturn(scenario());
        when(submitUseCase.submit(any())).thenReturn(ExecutionId.of("exec-10"));
        when(calculator.nextRun(any(), any())).thenReturn(NOW.plusSeconds(3600));

        dispatcher.dispatchDue();

        var saved = ArgumentCaptor.forClass(ScheduledRun.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ScheduledRunStatus.PENDING);
        assertThat(saved.getValue().nextRunAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(spec.expression()).isEqualTo("0 0 2 * * *");
    }

    @Test
    @DisplayName("a failing submission marks the run FAILED")
    void shouldMarkFailedRun() {
        var run = ScheduledRun.pending(ScheduledRunId.of("sched-3"), ScenarioId.of("scheduled"),
                YAML, ScheduleKind.ONCE, "in 1m", NOW.minusSeconds(30), NOW.minusSeconds(60));
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(run));
        when(parsingUseCase.parse(YAML)).thenReturn(scenario());
        when(submitUseCase.submit(any())).thenThrow(new IllegalStateException("engine down"));

        dispatcher.dispatchDue();

        var saved = ArgumentCaptor.forClass(ScheduledRun.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ScheduledRunStatus.FAILED);
    }

    private static ScenarioDefinition scenario() {
        return new ScenarioDefinition(ScenarioId.of("scheduled"), "Scheduled", "1.0",
                List.of(), Map.of(), List.of(), YAML, null);
    }
}
