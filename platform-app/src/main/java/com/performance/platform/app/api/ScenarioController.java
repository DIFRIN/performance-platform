package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.ExecutionStatusResponse;
import com.performance.platform.app.api.dto.SubmitResponse;
import com.performance.platform.application.ports.in.CancelExecutionUseCase;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GenerateReportUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.domain.id.ExecutionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing scenario submission, execution status, cancellation,
 * and report generation.
 * <p>
 * Delegates to application-layer use cases (ports in).
 */
@RestController
@RequestMapping("/api/v1")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);
    private static final String STATUS_ACCEPTED = "ACCEPTED";

    private final ScenarioParsingUseCase parsingUseCase;
    private final ExecuteScenarioUseCase executeUseCase;
    private final GetExecutionStatusUseCase statusUseCase;
    private final CancelExecutionUseCase cancelUseCase;
    private final GenerateReportUseCase reportUseCase;

    public ScenarioController(
            ScenarioParsingUseCase parsingUseCase,
            ExecuteScenarioUseCase executeUseCase,
            GetExecutionStatusUseCase statusUseCase,
            CancelExecutionUseCase cancelUseCase,
            GenerateReportUseCase reportUseCase) {
        this.parsingUseCase = parsingUseCase;
        this.executeUseCase = executeUseCase;
        this.statusUseCase = statusUseCase;
        this.cancelUseCase = cancelUseCase;
        this.reportUseCase = reportUseCase;
    }

    /**
     * Submits a YAML scenario for execution.
     * Validates the scenario (CF-05); returns 400 with detailed errors if invalid.
     *
     * @param yaml the scenario YAML content
     * @return 202 Accepted with the execution identifier
     */
    @PostMapping("/scenarios")
    public ResponseEntity<SubmitResponse> submitScenario(@RequestBody String yaml) {
        log.info("action=submit_scenario");
        var scenario = parsingUseCase.parse(yaml);
        log.info("action=scenario_parsed scenarioId={}", scenario.id().value());
        var executionId = executeUseCase.execute(scenario);
        log.info("action=scenario_accepted executionId={}", executionId.value());
        return ResponseEntity.accepted()
                .body(new SubmitResponse(executionId.value(), STATUS_ACCEPTED));
    }

    /**
     * Retrieves the status and state of an execution.
     *
     * @param id the execution identifier
     * @return 200 OK with the execution status details
     */
    @GetMapping("/executions/{id}")
    public ResponseEntity<ExecutionStatusResponse> getStatus(@PathVariable("id") String id) {
        log.info("action=get_status executionId={}", id);
        var executionId = ExecutionId.of(id);
        var status = statusUseCase.getStatus(executionId);
        var state = statusUseCase.getState(executionId);

        var response = state.map(s -> new ExecutionStatusResponse(
                s.id().value(),
                s.scenarioId().value(),
                s.status().name(),
                s.phaseStatuses().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().name(),
                                e -> e.getValue().name())),
                s.startedAt().toString(),
                s.updatedAt().toString()))
                .orElseGet(() -> new ExecutionStatusResponse(
                        id, null, status.name(), Map.of(), null, null));

        return ResponseEntity.ok(response);
    }

    /**
     * Cancels an in-progress execution.
     *
     * @param id the execution identifier
     * @return 202 Accepted
     */
    @PostMapping("/executions/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("id") String id) {
        log.info("action=cancel_execution executionId={}", id);
        cancelUseCase.cancel(ExecutionId.of(id));
        return ResponseEntity.accepted().build();
    }

    /**
     * Generates the report for a completed execution.
     *
     * @param id the execution identifier
     * @return 200 OK with the report identifier
     */
    @GetMapping("/executions/{id}/report")
    public ResponseEntity<Map<String, String>> getReport(@PathVariable("id") String id) {
        log.info("action=generate_report executionId={}", id);
        var reportId = reportUseCase.generate(ExecutionId.of(id));
        return ResponseEntity.ok(Map.of("reportId", reportId.value()));
    }
}
