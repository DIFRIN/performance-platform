package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.ValidationErrorResponse;
import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.exception.InvalidScenarioException;
import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.application.usecase.ExecutionNotDeletableException;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.scenario.usecase.ScenarioValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Centralized exception handling for the REST API.
 * Translates domain/application exceptions into appropriate HTTP responses
 * with structured error payloads.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Handles scenario validation failures (blocking errors).
     * Returns 400 with field-level error details (CF-05).
     * Uses structured DTO {@link ValidationErrorResponse} producing the same JSON shape
     * as before: {@code {error, message, details:[{field, message, path}]}}.
     */
    @ExceptionHandler(ScenarioValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(ScenarioValidationException ex) {
        log.warn("action=validation_failed errors={}", ex.getResult().errors().size());
        List<ValidationErrorResponse.FieldError> errors = ex.getResult().errors().stream()
                .map(e -> new ValidationErrorResponse.FieldError(e.field(), e.message(), e.path()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("SCENARIO_VALIDATION_FAILED", ex.getMessage(), errors));
    }

    /**
     * Handles generic scenario parsing failures (non-validation).
     */
    @ExceptionHandler(ScenarioParsingException.class)
    public ResponseEntity<Map<String, Object>> handleParsing(ScenarioParsingException ex) {
        log.warn("action=parsing_failed errors={}", ex.getErrors().size());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "SCENARIO_PARSING_FAILED",
                        "message", ex.getMessage(),
                        "details", ex.getErrors()));
    }

    /**
     * Handles invalid scenario errors from domain-level validation.
     */
    @ExceptionHandler(InvalidScenarioException.class)
    public ResponseEntity<Map<String, String>> handleInvalidScenario(InvalidScenarioException ex) {
        log.warn("action=invalid_scenario message={}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", "INVALID_SCENARIO",
                        "message", ex.getMessage()));
    }

    /**
     * Handles cases where no agent is available to execute a task.
     */
    @ExceptionHandler(NoAvailableAgentException.class)
    public ResponseEntity<Map<String, String>> handleNoAgent(NoAvailableAgentException ex) {
        log.warn("action=no_agent_available message={}", ex.getMessage());
        return ResponseEntity.status(503)
                .body(Map.of(
                        "error", "NO_AGENT_AVAILABLE",
                        "message", ex.getMessage()));
    }

    /**
     * Handles generic execution failures.
     */
    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<Map<String, String>> handleExecution(ExecutionException ex) {
        String execId = ex.getExecutionId().map(ExecutionId::value).orElse("unknown");
        log.error("action=execution_failed executionId={} message={}", execId, ex.getMessage(), ex);
        return ResponseEntity.status(500)
                .body(Map.of(
                        "error", "EXECUTION_FAILED",
                        "message", ex.getMessage()));
    }

    /**
     * Handles report generation failures.
     */
    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<Map<String, String>> handleReportGeneration(ReportGenerationException ex) {
        String execId = ex.getExecutionId().map(ExecutionId::value).orElse("unknown");
        log.error("action=report_generation_failed executionId={} message={}", execId, ex.getMessage(), ex);
        return ResponseEntity.status(500)
                .body(Map.of(
                        "error", "REPORT_GENERATION_FAILED",
                        "message", ex.getMessage()));
    }

    /**
     * Handles deletion of active executions (STARTED or RUNNING) — ADR-020.
     * Returns 409 Conflict to indicate the execution must be cancelled before deletion.
     */
    @ExceptionHandler(ExecutionNotDeletableException.class)
    public ResponseEntity<Map<String, String>> handleNotDeletable(ExecutionNotDeletableException ex) {
        String execId = ex.getExecutionId().value();
        log.warn("action=delete_rejected executionId={} status={}", execId, ex.getCurrentStatus());
        return ResponseEntity.status(409)
                .body(Map.of(
                        "error", "EXECUTION_NOT_DELETABLE",
                        "message", ex.getMessage()));
    }

    /**
     * Fallback for unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnknown(Exception ex) {
        log.error("action=unknown_error message={}", ex.getMessage(), ex);
        return ResponseEntity.status(500)
                .body(Map.of(
                        "error", "INTERNAL_SERVER_ERROR",
                        "message", "An unexpected error occurred"));
    }
}
