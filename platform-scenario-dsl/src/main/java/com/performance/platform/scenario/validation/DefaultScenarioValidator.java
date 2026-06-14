package com.performance.platform.scenario.validation;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implementation par defaut de {@link ScenarioValidator}.
 * Valide la structure, la coherence du DAG, les requiredContexts et les references aux loadModels.
 * 0 dependance Spring.
 *
 * <p>CC-02 exception: class exceeds 300 lines (~345) because validation rules are
 * naturally co-located for readability of the orchestration flow.
 * Methods {@link #validate(ScenarioDefinition)} and {@link #validateRequiredContexts}
 * exceed 40 lines for the same reason: they sequence multiple sub-validations sharing
 * the error/warning collectors.
 */
public class DefaultScenarioValidator implements ScenarioValidator {

    private static final Pattern SEMVER_PATTERN =
        Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private static final Pattern ID_PATTERN =
        Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]{0,99}$");

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
        boolean hasInjection = false;
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
                hasInjection = true;
                // Warning: timeout absent sur step INJECTION
                if (step.timeout() == null) {
                    warnings.add(new ValidationWarning(stepPath + ".timeout",
                        "No timeout specified for INJECTION step. Default timeout will be used."));
                }
            }
            if (step.phase() == Phase.ASSERTION) {
                hasAssertion = true;
                // Warning: requiredContexts vide sur step ASSERTION
                if (step.requiredContexts() == null || step.requiredContexts().isEmpty()) {
                    warnings.add(new ValidationWarning(stepPath + ".requiredContexts",
                        "ASSERTION step has no requiredContexts. It may execute with no context for verification."));
                }
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
        DagCycleDetector.DagAnalysisResult dagResult = DagCycleDetector.analyze(dependsOnMap);

        if (dagResult.hasCycle()) {
            errors.add(new ValidationError("dependsOn",
                "Cycle detected in the step dependency graph. The dependsOn edges do not form a valid DAG.",
                "steps"));
        }

        // --- Validation requiredContexts (utilise la transitivite du DAG) ---
        validateRequiredContexts(scenario, dependsOnMap, dagResult.hasCycle(), stepsById, errors);

        // --- Validation loadModel references (INJECTION gatling) ---
        validateLoadModelReferences(scenario, errors);

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

    private void validatePhase(StepDefinition step, List<ValidationError> errors, String stepPath) {
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
                if (!stepsById.containsKey(depId)) {
                    errors.add(new ValidationError("step.dependsOn",
                        "Step '" + stepId + "' depends on '" + depId + "' which does not exist",
                        "steps"));
                }
            }
        }
    }

    /**
     * Construit la map dependsOn : stepId -> [stepIds dont cette etape depend].
     */
    private Map<String, List<String>> buildDependsOnMap(ScenarioDefinition scenario) {
        Map<String, List<String>> dependsOnMap = new LinkedHashMap<>();
        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null) continue;
            String stepId = step.id().value();
            List<TaskId> deps = step.dependsOn();
            if (deps == null || deps.isEmpty()) {
                dependsOnMap.put(stepId, List.of());
            } else {
                List<String> depIds = new ArrayList<>();
                for (TaskId dep : deps) {
                    depIds.add(dep.value());
                }
                dependsOnMap.put(stepId, depIds);
            }
        }
        return dependsOnMap;
    }

    /**
     * Valide les requiredContexts.
     * Chaque cle de requiredContexts doit etre un step existant et
     * doit etre un ancetre (direct ou transitif) du step courant dans le DAG.
     *
     * <p>CC-02 note: method exceeds 40 lines (~49) because it combines adjacency
     * building, reachability BFS, and the validation loop into a single cohesive
     * traversal of the DAG. Separating these would require passing internal graph
     * structures across methods without improving testability.
     */
    private void validateRequiredContexts(ScenarioDefinition scenario,
                                          Map<String, List<String>> dependsOnMap,
                                          boolean hasCycle,
                                          Map<String, StepDefinition> stepsById,
                                          List<ValidationError> errors) {
        if (hasCycle) {
            // Si le DAG a un cycle, on ne peut pas verifier les requiredContexts
            return;
        }

        // Construire la fermeture transitive du graphe forward
        // forwardGraph: dep -> [steps qui dependent de dep]
        Map<String, Set<String>> forwardGraph = buildForwardGraph(dependsOnMap);

        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null) continue;
            String stepId = step.id().value();
            List<String> requiredContexts = step.requiredContexts();
            if (requiredContexts == null || requiredContexts.isEmpty()) continue;

            for (String ctxStepId : requiredContexts) {
                if (ctxStepId == null || ctxStepId.isBlank()) {
                    errors.add(new ValidationError("step.requiredContexts",
                        "Step '" + stepId + "' has a blank requiredContext entry",
                        "steps"));
                    continue;
                }

                // Le step de contexte doit exister
                if (!stepsById.containsKey(ctxStepId)) {
                    errors.add(new ValidationError("step.requiredContexts",
                        "Step '" + stepId + "' requires context from '" + ctxStepId
                            + "' which does not exist",
                        "steps"));
                    continue;
                }

                // Le step de contexte doit etre un ancetre dans le DAG
                // (il doit y avoir un chemin de ctxStepId vers stepId)
                if (!ctxStepId.equals(stepId) && !isReachable(ctxStepId, stepId, forwardGraph)) {
                    errors.add(new ValidationError("step.requiredContexts",
                        "Step '" + stepId + "' requires context from '" + ctxStepId
                            + "' which is not prior in the execution DAG. "
                            + "The context step must be a dependency (direct or transitive) of the step.",
                        "steps"));
                }
            }
        }
    }

    /**
     * Construit le graphe forward : dep -> [steps qui dependent de dep].
     */
    private Map<String, Set<String>> buildForwardGraph(Map<String, List<String>> dependsOnMap) {
        Map<String, Set<String>> forwardGraph = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : dependsOnMap.entrySet()) {
            String step = entry.getKey();
            List<String> deps = entry.getValue();
            if (deps == null) continue;

            for (String dep : deps) {
                forwardGraph.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(step);
            }
            // S'assurer que le step lui-meme est dans le graphe
            forwardGraph.computeIfAbsent(step, k -> new LinkedHashSet<>());
        }
        // Ajouter les noeuds des dependances qui ne sont pas des steps (ne devrait pas arriver)
        for (List<String> deps : dependsOnMap.values()) {
            if (deps != null) {
                for (String dep : deps) {
                    forwardGraph.computeIfAbsent(dep, k -> new LinkedHashSet<>());
                }
            }
        }
        return forwardGraph;
    }

    /**
     * Verifie si le noeud cible est atteignable depuis le noeud source dans le graphe forward.
     * BFS depuis la source.
     */
    private boolean isReachable(String source, String target, Map<String, Set<String>> forwardGraph) {
        if (source.equals(target)) return true;

        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
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

    private void validateLoadModelReferences(ScenarioDefinition scenario, List<ValidationError> errors) {
        for (StepDefinition step : scenario.steps()) {
            if (step.id() == null) continue;
            if (step.phase() != Phase.INJECTION) continue;
            if (!GATLING_TASK_NAME.equals(step.taskName())) continue;

            Object loadModelRef = step.parameters().get("loadModel");
            if (loadModelRef != null) {
                String ref = loadModelRef.toString();
                if (scenario.loadModels() == null || !scenario.loadModels().containsKey(ref)) {
                    errors.add(new ValidationError("step.loadModel",
                        "Step '" + step.id().value() + "' references loadModel '" + ref
                            + "' which is not defined in loadModels",
                        "steps"));
                }
            }
        }
    }
}
