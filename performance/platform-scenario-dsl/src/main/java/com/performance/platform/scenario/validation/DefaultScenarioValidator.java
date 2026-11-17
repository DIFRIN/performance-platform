package com.performance.platform.scenario.validation;

import com.performance.platform.domain.assertion.MetricCatalog;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implementation par defaut de {@link ScenarioValidator}.
 * Valide la structure, la coherence du DAG intra-phase, les contextes requis
 * et les modeles de charge inline (ADR-053, ADR-054).
 * 0 dependance Spring.
 */
public class DefaultScenarioValidator implements ScenarioValidator {

    private static final Pattern SEMVER_PATTERN =
        Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private static final Pattern ID_PATTERN =
        Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]{0,99}$");

    private final WorkloadPlanValidator workloadPlanValidator = new WorkloadPlanValidator();
    private final IntegrityBlockValidator integrityBlockValidator = new IntegrityBlockValidator();
    private final RequestMixValidator requestMixValidator = new RequestMixValidator();

    private static final String GATLING_TASK_NAME = "gatling";
    private static final String METADATA_OWNER_KEY = "owner";

    /**
     * CC-02 note: method exceeds 40 lines (~60) because it orchestrates
     * multiple sub-validation phases (scenario-level, step-level, dependsOn,
     * DAG cycle, requiredContexts, loadModel refs) sharing a single pair of
     * error/warning collectors. Extracting each phase into a separately-called
     * method would scatter the orchestration flow without reducing complexity.
     */
    @Override
    public ValidationResult validate(ScenarioDefinition scenario) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        if (scenario == null) {
            errors.add(new ValidationError("scenario", "Scenario is null", null));
            return new ValidationResult(false, errors, warnings);
        }

        // --- Validations niveau scenario ---
        validateScenarioId(scenario, errors);
        validateVersion(scenario, errors);

        // --- Validations niveau steps ---
        if (scenario.steps().isEmpty()) {
            errors.add(new ValidationError("steps", "Scenario must have at least one step", "steps"));
            return new ValidationResult(false, errors, warnings);
        }

        Map<String, StepDefinition> stepsById = new LinkedHashMap<>();
        boolean hasAssertion = false;

        for (int i = 0; i < scenario.steps().size(); i++) {
            StepDefinition step = scenario.steps().get(i);
            String stepPath = "steps[" + i + "]";

            if (step == null) {
                errors.add(new ValidationError("steps", "Step at index " + i + " is null", stepPath));
                continue;
            }

            // Unicite de l'id
            if (step.id() != null) {
                String stepId = step.id().value();
                if (stepsById.containsKey(stepId)) {
                    errors.add(new ValidationError("step.id",
                        "Duplicate step id '" + stepId + "'. Step ids must be unique within a scenario.",
                        stepPath + ".id"));
                }
                stepsById.put(stepId, step);
            }

            validateTaskName(step, errors, stepPath);
            validatePhase(step, errors, stepPath);

            if (step.phase() == Phase.INJECTION) {
                if (step.timeout() == null) {
                    warnings.add(new ValidationWarning(stepPath + ".timeout",
                        "No timeout specified for INJECTION step. Default timeout will be used."));
                }
            }
            if (step.phase() == Phase.ASSERTION) {
                hasAssertion = true;
                validateAssertionParameters(step, errors, warnings, stepPath);
            }
        }

        // Warning: aucun step ASSERTION
        if (!hasAssertion) {
            warnings.add(new ValidationWarning("steps",
                "No ASSERTION step in scenario. Performance assertions may be missing."));
        }

        // Warning: pas de metadata.owner
        if (scenario.metadata() == null || !scenario.metadata().containsKey(METADATA_OWNER_KEY)) {
            warnings.add(new ValidationWarning("metadata",
                "No 'owner' in metadata. Recommended for traceability."));
        }

        // --- Validation dependsOn (references existantes) ---
        validateDependsOnReferences(scenario, stepsById, errors);

        // --- Detection de cycle DAG ---
        Map<String, List<String>> dependsOnMap = buildDependsOnMap(scenario);
        var dagResult = DagCycleDetector.analyze(dependsOnMap);

        if (dagResult.hasCycle()) {
            errors.add(new ValidationError("dependsOn",
                "Cycle detected in the step dependency graph. The dependsOn edges do not form a valid DAG.",
                "steps"));
        }

        // --- Validation requiredContexts (utilise la transitivite du DAG) ---
        validateRequiredContexts(scenario, dependsOnMap, dagResult.hasCycle(), stepsById, errors);

        // --- Validation du plan de charge (INJECTION gatling, ADR-064) ---
        validateWorkloadPlans(scenario, errors);

        // --- Validation du bloc integrity (ADR-069) ---
        validateIntegrityBlocks(scenario, errors);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ======================== Helpers ========================

    private void validateScenarioId(ScenarioDefinition scenario, List<ValidationError> errors) {
        if (scenario.id() == null || scenario.id().value() == null || scenario.id().value().isBlank()) {
            errors.add(new ValidationError("scenario.id",
                "Scenario id is required", "scenario.id"));
            return;
        }
        String idValue = scenario.id().value();
        if (idValue.length() > 100) {
            errors.add(new ValidationError("scenario.id",
                "Scenario id exceeds maximum length of 100 characters (got " + idValue.length() + ")",
                "scenario.id"));
        }
        if (!ID_PATTERN.matcher(idValue).matches()) {
            errors.add(new ValidationError("scenario.id",
                "Scenario id must be alphanumeric with dashes only: '" + idValue + "'",
                "scenario.id"));
        }
    }

    private void validateVersion(ScenarioDefinition scenario, List<ValidationError> errors) {
        if (scenario.version() == null || scenario.version().isBlank()) {
            errors.add(new ValidationError("scenario.version",
                "Scenario version is required (semver format: x.y.z)", "scenario.version"));
            return;
        }
        if (!SEMVER_PATTERN.matcher(scenario.version()).matches()) {
            errors.add(new ValidationError("scenario.version",
                "Scenario version must be strict semver (x.y.z): '" + scenario.version() + "'",
                "scenario.version"));
        }
    }

    private void validateTaskName(StepDefinition step, List<ValidationError> errors, String stepPath) {
        if (step.taskName() == null || step.taskName().isBlank()) {
            errors.add(new ValidationError("step.task",
                "Task name is required (cannot be empty)", stepPath + ".task"));
        }
    }

    /**
     * Validation parse-time des assertions du nouveau modele (ADR-049).
     * <p>
     * Objectif : une metrique inconnue, une unite incompatible ou un {@code EQ}
     * flottant sans tolerance deviennent une erreur de scenario (HTTP 400), pas
     * un {@code ERROR} d'execution. Seules les familles declaratives sont
     * validees ici.
     */
    private void validateAssertionParameters(StepDefinition step, List<ValidationError> errors,
                                             List<ValidationWarning> warnings, String stepPath) {
        String taskName = step.taskName();
        if (taskName == null) {
            return;
        }
        Map<String, Object> parameters = step.parameters();

        boolean newModel = "direct-assertion".equals(taskName)
                || "performance-thresholds".equals(taskName)
                || "panel-assertion".equals(taskName);
        if (!newModel) {
            return;
        }
        if (!parameters.containsKey("description")) {
            warnings.add(new ValidationWarning(stepPath + ".description",
                    "No textual SLI/SLO described for this assertion. "
                            + "Recommended for report context (title, sli, slo, rationale)."));
        }

        if ("direct-assertion".equals(taskName)) {
            Object metricRaw = parameters.get("metric");
            if (metricRaw == null || metricRaw.toString().isBlank()) {
                errors.add(new ValidationError("step.metric",
                        "direct-assertion requires a 'metric' parameter", stepPath + ".parameters.metric"));
                return;
            }
            var definition = MetricCatalog.find(metricRaw.toString());
            if (definition.isEmpty()) {
                errors.add(new ValidationError("step.metric",
                        "Unknown metric '" + metricRaw + "'. Known metrics: " + MetricCatalog.knownNames(),
                        stepPath + ".parameters.metric"));
                return;
            }
            var metric = definition.get();
            if (metric.kind() != com.performance.platform.domain.assertion.MetricKind.STATE) {
                if (parameters.get("operator") == null || parameters.get("threshold") == null) {
                    errors.add(new ValidationError("step.threshold",
                            "direct-assertion requires 'operator' and 'threshold' for metric "
                                    + metric.id(),
                            stepPath + ".parameters.threshold"));
                } else {
                    validateTolerance(metric, parameters, errors, stepPath);
                }
            }
            return;
        }

        if ("performance-thresholds".equals(taskName)) {
            boolean declared = parameters.containsKey("latency")
                    || parameters.containsKey("errorRate")
                    || parameters.containsKey("throughput")
                    || parameters.containsKey("checks");
            if (!declared) {
                errors.add(new ValidationError("step.parameters",
                        "performance-thresholds requires at least one of latency, errorRate, "
                                + "throughput or checks",
                        stepPath + ".parameters"));
            }
        }
    }

    private void validateTolerance(com.performance.platform.domain.assertion.MetricDefinition metric,
                                   Map<String, Object> parameters, List<ValidationError> errors,
                                   String stepPath) {
        String operator = String.valueOf(parameters.get("operator")).toUpperCase();
        boolean equality = operator.equals("EQ") || operator.equals("==") || operator.equals("=")
                || operator.equals("NEQ") || operator.equals("!=");
        if (!equality || !metric.requiresToleranceForEquality()) {
            return;
        }
        double tolerance = 0;
        Object raw = parameters.get("tolerance");
        if (raw instanceof Number number) {
            tolerance = number.doubleValue();
        } else if (raw != null) {
            try {
                tolerance = Double.parseDouble(raw.toString());
            } catch (NumberFormatException ignored) {
                tolerance = 0;
            }
        }
        if (tolerance <= 0) {
            errors.add(new ValidationError("step.tolerance",
                    "EQ/NEQ on continuous metric " + metric.id()
                            + " requires a strictly positive 'tolerance'",
                    stepPath + ".parameters.tolerance"));
        }
    }

    private void validatePhase(StepDefinition step, List<ValidationError> errors, String stepPath) {
        if (step.hasLifetime()) {
            if (step.drain().isNegative()) {
                errors.add(new ValidationError("step.drain",
                        "drain must not be negative", stepPath + ".drain"));
            }
            return;
        }
        if (step.phase() == null) {
            errors.add(new ValidationError("step.phase",
                "Phase is required. Expected PREPARATION, INJECTION, or ASSERTION", stepPath + ".phase"));
        }
    }

    private void validateDependsOnReferences(ScenarioDefinition scenario,
                                              Map<String, StepDefinition> stepsById,
                                              List<ValidationError> errors) {
        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null) continue;
            String stepId = step.id().value();

            for (TaskId dep : step.dependsOn()) {
                String depId = dep.value();
                StepDefinition depStep = stepsById.get(depId);
                if (depStep == null) {
                    errors.add(new ValidationError("step.dependsOn",
                        "Step '" + stepId + "' depends on '" + depId + "' which does not exist",
                        "steps"));
                    continue;
                }
                if (depId.equals(stepId)) {
                    errors.add(new ValidationError("step.dependsOn",
                        "Step '" + stepId + "' cannot depend on itself", "steps"));
                    continue;
                }
                if (depStep.hasLifetime()) {
                    errors.add(new ValidationError("step.dependsOn",
                        "Step '" + stepId + "' cannot depend on service step '" + depId
                            + "'. Use 'ref' to read a service result.", "steps"));
                    continue;
                }
                // ADR-053 : dependsOn ordonne uniquement a l'interieur d'une phase.
                // Une reference a une phase anterieure est acceptee et ignoree pour
                // l'ordonnancement ; une reference a une phase posterieure est une erreur.
                if (step.phase() != null && phaseOrder(depStep) > phaseOrder(step)) {
                    errors.add(new ValidationError("step.dependsOn",
                        "Step '" + stepId + "' (" + step.phase() + ") depends on '" + depId
                            + "' (" + depStep.phase() + ") from a later phase. "
                            + "dependsOn orders steps inside a phase only.",
                        "steps"));
                }
                // dependsOn vers une phase anterieure : accepte, sans effet d'ordre.
            }

            if (step.ref() != null) {
                if (!stepsById.containsKey(step.ref().value())) {
                    errors.add(new ValidationError("step.ref",
                        "Step '" + stepId + "' references '" + step.ref().value()
                            + "' which does not exist", "steps"));
                } else if (step.ref().value().equals(stepId)) {
                    errors.add(new ValidationError("step.ref",
                        "Step '" + stepId + "' cannot reference itself", "steps"));
                }
            }
        }
    }

    private static int phaseOrder(StepDefinition step) {
        if (step.phase() == null) {
            return -1;
        }
        return switch (step.phase()) {
            case PREPARATION -> 0;
            case INJECTION -> 1;
            case ASSERTION -> 2;
        };
    }

    /**
     * Construit la map dependsOn intra-phase : stepId -> [stepIds dont cette etape depend].
     * Les references a une phase anterieure sont ignorees pour l'ordonnancement (ADR-053).
     */
    private Map<String, List<String>> buildDependsOnMap(ScenarioDefinition scenario) {
        Map<String, List<String>> dependsOnMap = new LinkedHashMap<>();
        Map<String, StepDefinition> byId = new LinkedHashMap<>();
        for (StepDefinition step : scenario.steps()) {
            if (step.id() != null) {
                byId.put(step.id().value(), step);
            }
        }
        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null || step.hasLifetime()) continue;
            String stepId = step.id().value();
            List<TaskId> deps = step.dependsOn();
            if (deps == null || deps.isEmpty()) {
                dependsOnMap.put(stepId, List.of());
            } else {
                List<String> depIds = new ArrayList<>();
                for (TaskId dep : deps) {
                    StepDefinition depStep = byId.get(dep.value());
                    if (depStep == null || depStep.hasLifetime()) continue;
                    if (phaseOrder(depStep) != phaseOrder(step)) continue;
                    depIds.add(dep.value());
                }
                dependsOnMap.put(stepId, depIds);
            }
        }
        return dependsOnMap;
    }

    /**
     * Valide les contextes requis effectifs (ADR-053) : override explicite, sinon
     * dependsOn plus ref. Chaque cle doit designer une etape anterieure disponible.
     */
    private void validateRequiredContexts(ScenarioDefinition scenario,
                                          Map<String, List<String>> dependsOnMap,
                                          boolean hasCycle,
                                          Map<String, StepDefinition> stepsById,
                                          List<ValidationError> errors) {
        Map<String, StepDefinition> byId = stepsById;
        Map<String, Set<String>> forwardGraph = hasCycle ? Map.of() : buildForwardGraph(dependsOnMap);

        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null) continue;
            String stepId = step.id().value();
            List<String> requiredContexts = step.resolvedRequiredContexts();
            if (requiredContexts.isEmpty()) continue;

            for (String ctxStepId : requiredContexts) {
                if (ctxStepId == null || ctxStepId.isBlank()) {
                    errors.add(new ValidationError("step.requiredContexts",
                        "Step '" + stepId + "' has a blank requiredContext entry",
                        "steps"));
                    continue;
                }

                StepDefinition ctxStep = byId.get(ctxStepId);
                if (ctxStep == null) {
                    errors.add(new ValidationError("step.requiredContexts",
                        "Step '" + stepId + "' requires context from '" + ctxStepId
                            + "' which does not exist",
                        "steps"));
                    continue;
                }
                if (ctxStepId.equals(stepId)) {
                    continue;
                }
                if (!isPriorContext(ctxStep, step, forwardGraph)) {
                    errors.add(new ValidationError("step.requiredContexts",
                        "Step '" + stepId + "' requires context from '" + ctxStepId
                            + "' which is not available before it runs.",
                        "steps"));
                }
            }
        }
    }

    /**
     * Une etape anterieure est disponible si elle appartient a une phase anterieure,
     * ou si elle est un ancetre DAG dans la meme phase. Une service step
     * {@code during: injection} est disponible des la phase ASSERTION (SON resultat
     * est publie au STOP, ADR-054).
     */
    private boolean isPriorContext(StepDefinition contextStep, StepDefinition step,
                                   Map<String, Set<String>> forwardGraph) {
        if (contextStep.hasLifetime()) {
            return step.phase() == Phase.ASSERTION
                && contextStep.during() == com.performance.platform.domain.scenario.ServiceScope.INJECTION;
        }
        if (step.phase() == null) {
            return false;
        }
        if (phaseOrder(contextStep) < phaseOrder(step)) {
            return true;
        }
        if (phaseOrder(contextStep) > phaseOrder(step)) {
            return false;
        }
        return isReachable(contextStep.id().value(), step.id().value(), forwardGraph);
    }

    /**
     * Construit le graphe forward : dep -> [steps qui dependent de dep].
     */
    private Map<String, Set<String>> buildForwardGraph(Map<String, List<String>> dependsOnMap) {
        Map<String, Set<String>> forwardGraph = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : dependsOnMap.entrySet()) {
            String step = entry.getKey();
            List<String> deps = entry.getValue();
            forwardGraph.computeIfAbsent(step, k -> new LinkedHashSet<>());
            if (deps == null) continue;

            for (String dep : deps) {
                forwardGraph.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(step);
            }
        }
        return forwardGraph;
    }

    /**
     * Verifie si le noeud cible est atteignable depuis le noeud source dans le graphe forward.
     */
    private boolean isReachable(String source, String target, Map<String, Set<String>> forwardGraph) {
        if (source.equals(target)) return true;

        Set<String> visited = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(source);
        visited.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> neighbors = forwardGraph.get(current);
            if (neighbors == null) continue;

            for (String neighbor : neighbors) {
                if (neighbor.equals(target)) return true;
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return false;
    }

    private void validateWorkloadPlans(ScenarioDefinition scenario, List<ValidationError> errors) {
        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null || step.phase() != Phase.INJECTION) continue;
            if (!GATLING_TASK_NAME.equals(step.taskName())) continue;
            errors.addAll(workloadPlanValidator.validate(step.id().value(), step.parameters()));
        }
    }

    private void validateIntegrityBlocks(ScenarioDefinition scenario, List<ValidationError> errors) {
        for (StepDefinition step : scenario.steps()) {
            if (step == null || step.id() == null) continue;
            errors.addAll(integrityBlockValidator.validate(step.id().value(), step.phase(), step.parameters()));
            errors.addAll(workloadPlanValidator.validateStep(step));
            errors.addAll(requestMixValidator.validate(step.id().value(), step.phase(), step.parameters()));
        }
    }
}
