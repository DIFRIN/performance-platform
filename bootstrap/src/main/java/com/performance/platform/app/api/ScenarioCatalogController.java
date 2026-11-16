package com.performance.platform.app.api;

import com.performance.platform.app.api.dto.CatalogScenarioResponse;
import com.performance.platform.app.api.dto.CatalogStepResponse;
import com.performance.platform.app.api.dto.SubmitResponse;
import com.performance.platform.app.api.dto.ValidationErrorResponse;
import com.performance.platform.app.catalog.CatalogScenario;
import com.performance.platform.app.catalog.ScenarioCatalogService;
import com.performance.platform.application.ports.in.SubmitScenarioUseCase;
import com.performance.platform.domain.scenario.StepDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller du catalogue de scenarios en lecture seule.
 * <p>
 * Endpoints :
 * <ul>
 *   <li>{@code GET /api/v1/catalog} — liste des scenarios du repertoire configure,
 *       avec leurs etapes (aucune execution)</li>
 *   <li>{@code POST /api/v1/catalog/{key}/run} — soumet le scenario du catalogue
 *       a execution et retourne {@code 202 Accepted}</li>
 * </ul>
 * <p>
 * Le repertoire source est defini par {@code platform.scenarios.directory}
 * ({@code SCENARIOS_DIR}). Le catalogue est re-scanne a chaque requete.
 * La soumission immediate de YAML reste disponible via {@code POST /api/v1/scenarios}.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/catalog")
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
public class ScenarioCatalogController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCatalogController.class);
    private static final String STATUS_ACCEPTED = "ACCEPTED";

    private final ScenarioCatalogService catalogService;
    private final SubmitScenarioUseCase submitUseCase;

    public ScenarioCatalogController(ScenarioCatalogService catalogService,
                                     SubmitScenarioUseCase submitUseCase) {
        this.catalogService = catalogService;
        this.submitUseCase = submitUseCase;
    }

    /**
     * Retourne les scenarios charges depuis le repertoire configure.
     * Aucun scenario n'est execute.
     *
     * @return 200 OK avec la liste des entrees et leurs etapes
     */
    @GetMapping
    public ResponseEntity<List<CatalogScenarioResponse>> list() {
        log.info("action=list_catalog");
        var scenarios = catalogService.list().stream()
                .map(ScenarioCatalogController::toResponse)
                .toList();
        log.info("action=list_catalog_done count={}", scenarios.size());
        return ResponseEntity.ok(scenarios);
    }

    /**
     * Soumet un scenario du catalogue a execution.
     *
     * @param key nom du fichier dans le catalogue
     * @return 202 Accepted avec l'identifiant d'execution, 404 si absent,
     *         400 si le fichier n'a pas pu etre parse
     */
    @PostMapping("/{key}/run")
    public ResponseEntity<?> run(@PathVariable("key") String key) {
        log.info("action=run_catalog_scenario key={}", key);

        var scenario = catalogService.find(key);
        if (scenario.isEmpty()) {
            log.warn("action=run_catalog_scenario_not_found key={}", key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "SCENARIO_NOT_FOUND",
                            "message", "No catalog scenario named '" + key + "'"));
        }

        CatalogScenario entry = scenario.get();
        if (!entry.valid()) {
            log.warn("action=run_catalog_scenario_invalid key={}", key);
            return ResponseEntity.badRequest()
                    .body(new ValidationErrorResponse(
                            "SCENARIO_INVALID",
                            entry.error(),
                            List.of(new ValidationErrorResponse.FieldError(
                                    "scenario", entry.error(), key))));
        }

        var executionId = submitUseCase.submit(entry.definition());
        log.info("action=run_catalog_scenario_accepted key={} executionId={}",
                key, executionId.value());
        return ResponseEntity.accepted()
                .body(new SubmitResponse(executionId.value(), STATUS_ACCEPTED));
    }

    private static CatalogScenarioResponse toResponse(CatalogScenario scenario) {
        var definition = scenario.definition();
        if (definition == null) {
            return new CatalogScenarioResponse(
                    scenario.key(), null, null, null, List.of(), 0,
                    false, scenario.error(), scenario.yaml(), List.of());
        }
        var steps = definition.steps().stream()
                .map(ScenarioCatalogController::toStepResponse)
                .toList();
        return new CatalogScenarioResponse(
                scenario.key(),
                definition.id().value(),
                definition.name(),
                definition.version(),
                definition.tags(),
                steps.size(),
                true,
                null,
                scenario.yaml(),
                steps);
    }

    private static CatalogStepResponse toStepResponse(StepDefinition step) {
        return new CatalogStepResponse(
                step.id().value(),
                step.taskName(),
                step.phase() == null ? null : step.phase().name(),
                step.during() == null ? null : step.during().name(),
                step.ref() == null ? null : step.ref().value(),
                step.fanOut().name(),
                step.dependsOn().stream().map(taskId -> taskId.value()).toList(),
                step.resolvedRequiredContexts(),
                step.timeout() == null ? null : step.timeout().toMillis(),
                step.parameters());
    }
}
