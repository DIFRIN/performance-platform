package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.RescheduleScheduleRequest;
import com.performance.platform.app.api.dto.ScheduleSubmissionRequest;
import com.performance.platform.app.api.dto.ScheduleSubmissionResponse;
import com.performance.platform.app.api.dto.ScheduledRunResponse;
import com.performance.platform.application.exception.InvalidScheduleException;
import com.performance.platform.application.ports.in.CancelScheduledRunUseCase;
import com.performance.platform.application.ports.in.ListScheduledRunsUseCase;
import com.performance.platform.application.ports.in.ManageScheduledRunUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.in.ScheduleScenarioUseCase;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.application.ports.out.ScheduleCalculator;
import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.application.schedule.ScheduledRun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Run immediat ou planifie depuis une expression libre.
 * <p>
 * Endpoints :
 * <ul>
 *   <li>{@code POST /api/v1/schedules} — execute immediatement si l'expression
 *       est vide/{@code now}, sinon persiste un run planifie (202)</li>
 *   <li>{@code GET /api/v1/schedules?limit=N} — liste des runs planifies</li>
 *   <li>{@code DELETE /api/v1/schedules/{id}} — annule un run planifie en attente</li>
 * </ul>
 * Les actions de run existantes ({@code /catalog/{key}/run}, {@code /scenarios/upload},
 * {@code /executions/{id}/rerun}) restent disponibles sans planification.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/schedules")
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    private final ScenarioParsingUseCase parsingUseCase;
    private final SubmitScenarioUseCase submitUseCase;
    private final ScheduleScenarioUseCase scheduleUseCase;
    private final ListScheduledRunsUseCase listUseCase;
    private final CancelScheduledRunUseCase cancelUseCase;
    private final ManageScheduledRunUseCase manageUseCase;
    private final ScheduleCalculator calculator;

    public ScheduleController(ScenarioParsingUseCase parsingUseCase,
                              SubmitScenarioUseCase submitUseCase,
                              ScheduleScenarioUseCase scheduleUseCase,
                              ListScheduledRunsUseCase listUseCase,
                              CancelScheduledRunUseCase cancelUseCase,
                              ManageScheduledRunUseCase manageUseCase,
                              ScheduleCalculator calculator) {
        this.parsingUseCase = parsingUseCase;
        this.submitUseCase = submitUseCase;
        this.scheduleUseCase = scheduleUseCase;
        this.listUseCase = listUseCase;
        this.cancelUseCase = cancelUseCase;
        this.manageUseCase = manageUseCase;
        this.calculator = calculator;
    }

    /**
     * Soumet un scenario immediatement ou pour un declenchement differe.
     *
     * @param request YAML du scenario et expression de planification
     * @return 202 avec l'identifiant d'execution (immediat) ou du run planifie
     */
    @PostMapping
    public ResponseEntity<ScheduleSubmissionResponse> submit(
            @RequestBody ScheduleSubmissionRequest request) {
        if (request == null || request.scenarioYaml() == null || request.scenarioYaml().isBlank()) {
            throw new InvalidScheduleException("scenarioYaml is required");
        }

        var scenario = parsingUseCase.parse(request.scenarioYaml());
        var spec = calculator.parse(request.schedule());

        if (!spec.scheduled()) {
            var executionId = submitUseCase.submit(scenario);
            log.info("action=schedule_submit_immediate executionId={}", executionId.value());
            return ResponseEntity.accepted()
                    .body(new ScheduleSubmissionResponse(
                            executionId.value(), null, "ACCEPTED", null));
        }

        ScheduledRun run = scheduleUseCase.schedule(scenario, spec);
        log.info("action=schedule_submit_deferred scheduleId={} nextRunAt={}",
                run.id().value(), run.nextRunAt());
        return ResponseEntity.accepted()
                .body(new ScheduleSubmissionResponse(
                        null, run.id().value(), "SCHEDULED", run.nextRunAt().toString()));
    }

    /** @param limit nombre maximal de runs (defaut 50) */
    @GetMapping
    public ResponseEntity<List<ScheduledRunResponse>> list(
            @RequestParam(name = "limit", defaultValue = "0") int limit) {
        var runs = listUseCase.list(limit).stream()
                .map(ScheduleController::toResponse)
                .toList();
        return ResponseEntity.ok(runs);
    }

    /**
     * Annule un run planifie encore en attente.
     *
     * @param id identifiant du run planifie
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable("id") String id) {
        log.info("action=cancel_schedule scheduleId={}", id);
        cancelUseCase.cancel(ScheduledRunId.of(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * Replanifie un run en attente avec une nouvelle expression.
     *
     * @param id      identifiant du run planifie
     * @param request nouvelle expression de planification
     * @return 200 avec le run mis a jour, 400 si l'expression est invalide,
     *         404 si le run est inconnu
     */
    @PutMapping("/{id}")
    public ResponseEntity<ScheduledRunResponse> reschedule(
            @PathVariable("id") String id,
            @RequestBody RescheduleScheduleRequest request) {
        log.info("action=reschedule_schedule scheduleId={}", id);
        var run = manageUseCase.reschedule(ScheduledRunId.of(id),
                request == null ? null : request.schedule());
        return ResponseEntity.ok(toResponse(run));
    }

    /**
     * Declenche immediatement le scenario d'un run en attente.
     *
     * @param id identifiant du run planifie
     * @return 202 avec l'identifiant d'execution cree, 400 si le run n'est
     *         plus en attente, 404 si le run est inconnu
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<ScheduleSubmissionResponse> runNow(@PathVariable("id") String id) {
        log.info("action=run_schedule_now scheduleId={}", id);
        String executionId = manageUseCase.runNow(ScheduledRunId.of(id));
        return ResponseEntity.accepted()
                .body(new ScheduleSubmissionResponse(executionId, null, "ACCEPTED", null));
    }

    private static ScheduledRunResponse toResponse(ScheduledRun run) {
        return new ScheduledRunResponse(
                run.id().value(),
                run.scenarioId().value(),
                run.kind().name(),
                run.expression(),
                run.nextRunAt().toString(),
                run.status().name(),
                run.lastExecutionId(),
                run.createdAt().toString(),
                run.updatedAt().toString());
    }
}
