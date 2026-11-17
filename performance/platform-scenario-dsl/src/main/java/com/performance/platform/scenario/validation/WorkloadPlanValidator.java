package com.performance.platform.scenario.validation;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.LoadPresets;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.domain.injection.WorkloadPlanCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regles de validation du plan de charge d'un step d'injection (ADR-064, section 4).
 * Un preset {@code loadModel} est developpe en {@link WorkloadPlan} avant validation.
 * 0 dependance Spring.
 */
public final class WorkloadPlanValidator {

    private static final Set<String> STAGE_KINDS = Set.of("ramp", "hold", "burst", "pause");
    private static final String UNIT_KEY = "unit";
    private static final String LOAD_MODEL_KEY = "loadModel";

    /** @return erreurs (une par regle violee), vide si le step ne declare pas de plan ou si le plan est valide. */
    public List<ValidationError> validate(String stepId, Map<String, Object> parameters) {
        List<ValidationError> errors = new ArrayList<>();
        if (parameters == null) {
            return errors;
        }
        boolean hasWorkload = parameters.containsKey(WorkloadPlanCodec.PARAM_KEY);
        boolean hasLoadModel = parameters.containsKey(LOAD_MODEL_KEY);
        if (!hasWorkload && !hasLoadModel) {
            return errors;
        }
        if (hasWorkload && hasLoadModel) {
            errors.add(error("exclusive", stepId, "'workload' and 'loadModel' are mutually exclusive"));
            return errors;
        }
        if (hasWorkload) {
            validateWorkload(stepId, parameters.get(WorkloadPlanCodec.PARAM_KEY), errors);
        } else {
            validateLoadModel(stepId, parameters.get(LOAD_MODEL_KEY), errors);
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void validateWorkload(String stepId, Object raw, List<ValidationError> errors) {
        if (!(raw instanceof Map<?, ?> map)) {
            errors.add(error("workload", stepId, "workload must be a mapping"));
            return;
        }
        if (containsUnit(map)) {
            errors.add(error("unit", stepId,
                "workload must not declare a 'unit' (derived from the model: OPEN = users/s, CLOSED = concurrent users)"));
        }
        if (!(map.get("stages") instanceof List<?> stages) || stages.isEmpty()) {
            errors.add(error("empty", stepId, "workload plan must have at least one stage"));
            return;
        }
        boolean unknownKind = false;
        for (Object stage : stages) {
            if (stage instanceof Map<?, ?> sm && sm.size() == 1) {
                String kind = String.valueOf(sm.keySet().iterator().next());
                if (!STAGE_KINDS.contains(kind)) {
                    errors.add(error("stage-kind", stepId,
                        "unknown stage kind '" + kind + "' (expected ramp, hold, burst or pause)"));
                    unknownKind = true;
                }
            }
        }
        if (unknownKind) {
            return;
        }
        WorkloadPlan plan;
        try {
            plan = WorkloadPlanCodec.fromMap((Map<String, Object>) map);
        } catch (IllegalArgumentException e) {
            errors.add(error("plan", stepId, e.getMessage()));
            return;
        }
        validatePlan(stepId, plan, errors);
    }

    @SuppressWarnings("unchecked")
    private void validateLoadModel(String stepId, Object raw, List<ValidationError> errors) {
        if (!(raw instanceof Map<?, ?> model)) {
            errors.add(error("loadModel", stepId, "loadModel must be an inline mapping"));
            return;
        }
        Object params = model.get("parameters");
        if (params != null && !(params instanceof Map<?, ?>)) {
            errors.add(error("loadModel", stepId, "loadModel parameters must be a mapping"));
            return;
        }
        if (containsUnit(model)) {
            errors.add(error("unit", stepId,
                "loadModel must not declare a 'unit' (derived from the model)"));
            return;
        }
        Object type = model.get("type");
        WorkloadPlan plan;
        try {
            plan = LoadPresets.expand(type == null ? null : type.toString(), (Map<String, Object>) params);
        } catch (IllegalArgumentException e) {
            errors.add(error("loadModel", stepId, e.getMessage()));
            return;
        }
        validatePlan(stepId, plan, errors);
    }

    private void validatePlan(String stepId, WorkloadPlan plan, List<ValidationError> errors) {
        if (plan.model() == ArrivalModel.CLOSED && plan.stages().stream().anyMatch(s -> s instanceof Burst)) {
            errors.add(error("burst-closed", stepId, "Burst stage is not allowed in a CLOSED plan"));
        }
        if (plan.stages().stream().noneMatch(s -> s.measured())) {
            errors.add(error("measured", stepId, "workload plan must have at least one measured stage"));
        }
    }

    /** Cherche une cle {@code unit} au niveau du plan, des parametres ou des corps d'etape. */
    private static boolean containsUnit(Map<?, ?> map) {
        if (map.containsKey(UNIT_KEY)) {
            return true;
        }
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested && containsUnit(nested)) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nested && containsUnit(nested)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ValidationError error(String rule, String stepId, String message) {
        return new ValidationError("step.workload." + rule,
            "Step '" + stepId + "': " + message, "steps");
    }
}
