package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.SubmitResponse;
import com.performance.platform.app.api.dto.ValidationErrorResponse;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST controller for uploading scenario files or YAML text for immediate execution.
 * <p>
 * Accepts either a multipart file ({@code file}) or a form text parameter ({@code yaml}).
 * Validates the scenario and executes it immediately, returning {@code 202 Accepted}
 * with the execution identifier. No scenario catalog is maintained.
 */
@RestController
@RequestMapping("/api/v1")
public class ScenarioUploadController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioUploadController.class);
    private static final String STATUS_ACCEPTED = "ACCEPTED";

    private final ScenarioParsingUseCase parsingUseCase;
    private final ExecuteScenarioUseCase executeUseCase;

    public ScenarioUploadController(
            ScenarioParsingUseCase parsingUseCase,
            ExecuteScenarioUseCase executeUseCase) {
        this.parsingUseCase = parsingUseCase;
        this.executeUseCase = executeUseCase;
    }

    /**
     * Uploads a scenario for immediate execution.
     * Accepts either a multipart {@code file} or a form field {@code yaml}.
     * If neither is provided, returns {@code 400} with a validation error.
     *
     * @param request the HTTP request
     * @return 202 Accepted with execution identifier, or 400 on validation failure
     */
    @PostMapping(value = "/scenarios/upload")
    public ResponseEntity<?> upload(HttpServletRequest request) {
        log.info("action=upload_scenario");

        String yamlContent = extractYamlContent(request);
        if (yamlContent == null) {
            log.warn("action=upload_scenario_missing_input");
            return ResponseEntity.badRequest()
                    .body(new ValidationErrorResponse(
                            "SCENARIO_VALIDATION_FAILED",
                            "Either 'file' or 'yaml' parameter is required",
                            List.of(new ValidationErrorResponse.FieldError(
                                    "input", "Either 'file' or 'yaml' parameter is required", null))));
        }

        var scenario = parsingUseCase.parse(yamlContent);
        log.info("action=scenario_parsed scenarioId={}", scenario.id().value());
        var executionId = executeUseCase.execute(scenario);
        log.info("action=scenario_accepted executionId={}", executionId.value());
        return ResponseEntity.accepted()
                .body(new SubmitResponse(executionId.value(), STATUS_ACCEPTED));
    }

    private String extractYamlContent(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            return extractFromMultipart(request);
        }
        return extractFromFormParam(request);
    }

    private String extractFromMultipart(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
            log.warn("action=upload_scenario_multipart_not_supported");
            return null;
        }
        try {
            MultipartFile file = multipartRequest.getFile("file");
            if (file != null && !file.isEmpty()) {
                var content = new String(file.getBytes(), StandardCharsets.UTF_8);
                if (!content.isBlank()) {
                    log.info("action=upload_scenario source=file originalFilename={}",
                            file.getOriginalFilename());
                    return content;
                }
            }
        } catch (Exception e) {
            log.error("action=upload_scenario_read_failed message={}", e.getMessage(), e);
        }
        return null;
    }

    private String extractFromFormParam(HttpServletRequest request) {
        var yaml = request.getParameter("yaml");
        if (yaml != null && !yaml.isBlank()) {
            log.info("action=upload_scenario source=textarea");
            return yaml;
        }
        return null;
    }
}
