package com.performance.platform.campaign.schedule;

import com.performance.platform.application.exception.InvalidScheduleException;
import com.performance.platform.application.exception.ScheduleNotFoundException;
import com.performance.platform.application.ports.in.CancelScheduledRunUseCase;
import com.performance.platform.application.ports.in.ListScheduledRunsUseCase;
import com.performance.platform.application.ports.in.ManageScheduledRunUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.ScheduleScenarioUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ScheduleCalculator;
import com.performance.platform.application.ports.out.ScheduledRunRepository;
import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.application.schedule.ScheduleKind;
import com.performance.platform.application.schedule.ScheduleSpec;
import com.performance.platform.application.schedule.ScheduledRun;
import com.performance.platform.application.schedule.ScheduledRunStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Persiste les runs planifies et expose leur consultation/annulation.
 * Le declenchement effectif est assure par {@link ScheduledRunDispatcher}.
 */
@Service
@ConditionalOnExpression(
    "'${runtime.mode:LOCAL}'.equals('LOCAL') || ('${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
    + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR'))")
public class ScheduleRunService implements ScheduleScenarioUseCase,
        ListScheduledRunsUseCase, CancelScheduledRunUseCase, ManageScheduledRunUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduleRunService.class);
    private static final int DEFAULT_LIMIT = 50;

    private final ScheduledRunRepository repository;
    private final ScheduleCalculator calculator;
    private final ScenarioParsingUseCase parsingUseCase;
    private final SubmitScenarioUseCase submitUseCase;

    public ScheduleRunService(ScheduledRunRepository repository,
                              ScheduleCalculator calculator,
                              ScenarioParsingUseCase parsingUseCase,
                              SubmitScenarioUseCase submitUseCase) {
        this.repository = repository;
        this.calculator = calculator;
        this.parsingUseCase = parsingUseCase;
        this.submitUseCase = submitUseCase;
    }

    @Override
    public ScheduledRun schedule(ScenarioDefinition scenario, ScheduleSpec spec) {
        if (spec == null || !spec.scheduled()) {
            throw new InvalidScheduleException("A deferred schedule is required to create a scheduled run");
        }
        if (scenario.sourceYaml() == null || scenario.sourceYaml().isBlank()) {
            throw new InvalidScheduleException(
                    "Scenario source YAML is required to schedule a run");
        }

        Instant now = Instant.now();
        Instant nextRunAt = spec.kind() == ScheduleKind.ONCE
                ? spec.runAt()
                : calculator.nextRun(spec, now);

        var run = ScheduledRun.pending(ScheduledRunId.generate(), scenario.id(),
                scenario.sourceYaml(), spec.kind(), spec.expression(), nextRunAt, now);
        repository.save(run);

        log.info("action=schedule_run scheduleId={} scenarioId={} kind={} nextRunAt={} expression={}",
                run.id().value(), run.scenarioId().value(), run.kind(), nextRunAt, run.expression());
        return run;
    }

    @Override
    public List<ScheduledRun> list(int limit) {
        return repository.findRecent(limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public void cancel(ScheduledRunId id) {
        var run = repository.findById(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));
        if (run.status() != ScheduledRunStatus.PENDING) {
            log.info("action=cancel_schedule_ignored scheduleId={} status={}", id.value(), run.status());
            return;
        }
        repository.save(run.cancelled(Instant.now()));
        log.info("action=schedule_cancelled scheduleId={}", id.value());
    }

    @Override
    public ScheduledRun reschedule(ScheduledRunId id, String expression) {
        var run = repository.findById(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));
        if (run.status() != ScheduledRunStatus.PENDING) {
            throw new InvalidScheduleException(
                    "Only pending scheduled runs can be rescheduled (status=" + run.status() + ")");
        }

        var spec = calculator.parse(expression);
        if (!spec.scheduled()) {
            throw new InvalidScheduleException(
                    "A deferred schedule is required; use 'Run now' for an immediate execution");
        }

        Instant now = Instant.now();
        Instant nextRunAt = spec.kind() == ScheduleKind.ONCE
                ? spec.runAt()
                : calculator.nextRun(spec, now);

        var updated = run.withSchedule(spec.kind(), spec.expression(), nextRunAt, now);
        repository.save(updated);
        log.info("action=reschedule_run scheduleId={} kind={} nextRunAt={} expression={}",
                id.value(), spec.kind(), nextRunAt, spec.expression());
        return updated;
    }

    @Override
    public String runNow(ScheduledRunId id) {
        var run = repository.findById(id)
                .orElseThrow(() -> new ScheduleNotFoundException(id));
        if (run.status() != ScheduledRunStatus.PENDING) {
            throw new InvalidScheduleException(
                    "Only pending scheduled runs can be triggered (status=" + run.status() + ")");
        }

        var scenario = parsingUseCase.parse(run.scenarioYaml());
        var executionId = submitUseCase.submit(scenario);
        Instant now = Instant.now();

        if (run.kind() == ScheduleKind.CRON) {
            Instant next = calculator.nextRun(run.spec(), now);
            repository.save(run.rescheduled(next, executionId.value(), now));
            log.info("action=run_scheduled_now scheduleId={} executionId={} nextRunAt={}",
                    id.value(), executionId.value(), next);
        } else {
            repository.save(run.completed(executionId.value(), now));
            log.info("action=run_scheduled_now scheduleId={} executionId={} completed=true",
                    id.value(), executionId.value());
        }
        return executionId.value();
    }
}
