package com.performance.platform.scenario.validation;

import com.performance.platform.domain.scenario.Phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validation du bloc optionnel {@code integrity} d'un step d'injection (ADR-069).
 * Formats acceptes : {@code 5%} ou nombre, dans ]0,100]. Cle inconnue = erreur.
 * Bloc autorise uniquement en phase INJECTION. 0 dependance Spring.
 */
public final class IntegrityBlockValidator {

    public static final String BLOCK_KEY = "integrity";
    private static final Set<String> KNOWN_KEYS = Set.of("maxArrivalDeviation", "maxGeneratorCpu");

    /** @return erreurs, vide si le step ne declare pas de bloc ou si le bloc est valide. */
    public List<ValidationError> validate(String stepId, Phase phase, Map<String, Object> parameters) {
        List<ValidationError> errors = new ArrayList<>();
        if (parameters == null || !parameters.containsKey(BLOCK_KEY)) {
            return errors;
        }
        if (phase != Phase.INJECTION) {
            errors.add(error("phase", stepId, "'integrity' block is only allowed in an INJECTION step"));
            return errors;
        }
        if (!(parameters.get(BLOCK_KEY) instanceof Map<?, ?> block)) {
            errors.add(error("block", stepId, "'integrity' must be a mapping"));
            return errors;
        }
        for (Map.Entry<?, ?> entry : block.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!KNOWN_KEYS.contains(key)) {
                errors.add(error("key", stepId,
                    "unknown integrity key '" + key + "' (expected maxArrivalDeviation or maxGeneratorCpu)"));
                continue;
            }
            String problem = checkPercent(entry.getValue());
            if (problem != null) {
                errors.add(error(key, stepId, "integrity." + key + " " + problem));
            }
        }
        return errors;
    }

    /** @return null si valide, sinon la description du probleme. */
    private static String checkPercent(Object raw) {
        double value;
        if (raw instanceof Number n) {
            value = n.doubleValue();
        } else if (raw instanceof String s) {
            String text = s.trim();
            if (text.endsWith("%")) {
                text = text.substring(0, text.length() - 1).trim();
            }
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return "must be a number or a percentage like '5%' (got '" + s + "')";
            }
        } else {
            return "must be a number or a percentage like '5%'";
        }
        if (Double.isNaN(value) || value <= 0 || value > 100) {
            return "must be in ]0,100] (got " + raw + ")";
        }
        return null;
    }

    private static ValidationError error(String rule, String stepId, String message) {
        return new ValidationError("step.integrity." + rule,
            "Step '" + stepId + "': " + message, "steps");
    }
}
