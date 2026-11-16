package com.performance.platform.campaign.schedule;

import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ScheduleCalculator;
import com.performance.platform.application.ports.out.ScheduledRunRepository;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.application.schedule.ScheduleKind;
import com.performance.platform.application.schedule.ScheduledRun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Declenche les runs planifies arrives a echeance.
 * <p>
 * Le dispatcher interroge periodiquement la persistance (et non une file en
 * memoire) : les planifications survivent ainsi a un redemarrage de
 * l'orchestrateur. Chaque declenchement soumet le scenario via
 * {@link SubmitScenarioUseCase} sur un virtual thread du moteur.
 */
@Component
@ConditionalOnExpression(
    "'${runtime.mode:LOCAL}'.equals('LOCAL') || ('${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
    + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR'))")
public class ScheduledRunDispatcher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRunDispatcher.class);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final int BATCH_SIZE = 10;

    private final ScheduledRunRepository repository;
    private final ScheduleCalculator calculator;
    private final ScenarioParsingUseCase parsingUseCase;
    private final SubmitScenarioUseCase submitUseCase;

    private volatile boolean running;
    private Thread worker;

    public ScheduledRunDispatcher(ScheduledRunRepository repository,
                                  ScheduleCalculator calculator,
                                  ScenarioParsingUseCase parsingUseCase,
                                  SubmitScenarioUseCase submitUseCase) {
        this.repository = repository;
        this.calculator = calculator;
        this.parsingUseCase = parsingUseCase;
        this.submitUseCase = submitUseCase;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        worker = Thread.ofVirtual()
                .name("scheduled-run-dispatcher")
                .start(this::loop);
        log.info("action=scheduled_run_dispatcher_started pollIntervalMs={}", POLL_INTERVAL.toMillis());
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        log.info("action=scheduled_run_dispatcher_stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                dispatchDue();
            } catch (RuntimeException e) {
                log.error("action=scheduled_run_dispatch_failed", e);
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Dispatches every due run once (package-private for tests). */
    void dispatchDue() {
        Instant now = Instant.now();
        List<ScheduledRun> due = repository.findDue(now, BATCH_SIZE);
        for (ScheduledRun run : due) {
            dispatch(run);
        }
    }

    private void dispatch(ScheduledRun run) {
        log.info("action=scheduled_run_fire scheduleId={} scenarioId={} kind={}",
                run.id().value(), run.scenarioId().value(), run.kind());
        try {
            ScenarioDefinition scenario = parsingUseCase.parse(run.scenarioYaml());
            var executionId = submitUseCase.submit(scenario);
            Instant now = Instant.now();

            if (run.kind() == ScheduleKind.CRON) {
                Instant next = calculator.nextRun(run.spec(), now);
                repository.save(run.rescheduled(next, executionId.value(), now));
                log.info("action=scheduled_run_rescheduled scheduleId={} executionId={} nextRunAt={}",
                        run.id().value(), executionId.value(), next);
            } else {
                repository.save(run.completed(executionId.value(), now));
                log.info("action=scheduled_run_completed scheduleId={} executionId={}",
                        run.id().value(), executionId.value());
            }
        } catch (RuntimeException e) {
            Instant now = Instant.now();
            if (run.kind() == ScheduleKind.CRON) {
                try {
                    Instant next = calculator.nextRun(run.spec(), now);
                    repository.save(run.rescheduled(next, run.lastExecutionId(), now));
                    log.warn("action=scheduled_run_fire_failed_retry scheduleId={} nextRunAt={} error={}",
                            run.id().value(), next, e.getMessage(), e);
                } catch (RuntimeException nextFailure) {
                    repository.save(run.failed(now));
                    log.error("action=scheduled_run_fire_failed scheduleId={} error={}",
                            run.id().value(), e.getMessage(), e);
                }
            } else {
                repository.save(run.failed(now));
                log.error("action=scheduled_run_fire_failed scheduleId={} error={}",
                        run.id().value(), e.getMessage(), e);
            }
        }
    }
}
