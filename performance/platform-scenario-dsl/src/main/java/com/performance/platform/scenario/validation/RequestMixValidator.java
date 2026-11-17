package com.performance.platform.scenario.validation;

import com.performance.platform.domain.scenario.Phase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation du bloc optionnel {@code parameters.requests} (mix pondere, ADR-070).
 * Liste non vide, {@code name} unique {@code [a-z0-9-]+}, {@code weight} > 0, exclusif avec la forme
 * mono-requete ({@code path}/{@code method} au niveau du step). Chaque entree respecte les regles de la
 * requete unique. 0 dependance Spring.
 */
public final class RequestMixValidator {

    public static final String BLOCK_KEY = "requests";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final List<String> MAP_KEYS = List.of("pathParams", "queryParams", "headers");

    /** @return erreurs, vide si le step ne declare pas de bloc ou si le bloc est valide. */
    public List<ValidationError> validate(String stepId, Phase phase, Map<String, Object> parameters) {
        List<ValidationError> errors = new ArrayList<>();
        if (parameters == null || !parameters.containsKey(BLOCK_KEY)) {
            return errors;
        }
        if (phase != Phase.INJECTION) {
            errors.add(error("phase", stepId, "'requests' block is only allowed in an INJECTION step"));
            return errors;
        }
        if (parameters.containsKey("path") || parameters.containsKey("method")) {
            errors.add(error("exclusive", stepId,
                "'requests' cannot be combined with step-level 'path'/'method' (single-request form)"));
        }
        if (!(parameters.get(BLOCK_KEY) instanceof List<?> list) || list.isEmpty()) {
            errors.add(error("empty", stepId, "'requests' must be a non-empty list"));
            return errors;
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            validateEntry(stepId, i, list.get(i), names, errors);
        }
        return errors;
    }

    private void validateEntry(String stepId, int index, Object raw, Set<String> names,
                               List<ValidationError> errors) {
        String at = "requests[" + index + "]";
        if (!(raw instanceof Map<?, ?> entry)) {
            errors.add(error("entry", stepId, at + " must be a mapping"));
            return;
        }
        Object name = entry.get("name");
        if (name == null || !NAME_PATTERN.matcher(name.toString()).matches()) {
            errors.add(error("name", stepId, at + ".name must match [a-z0-9-]+ (got '" + name + "')"));
        } else if (!names.add(name.toString())) {
            errors.add(error("name", stepId, "duplicate request name '" + name + "'"));
        }
        checkWeight(stepId, at, entry.get("weight"), errors);

        Object path = entry.get("path");
        if (path == null || path.toString().isBlank()) {
            errors.add(error("path", stepId, at + ".path is required"));
        }
        Object method = entry.get("method");
        if (method != null && !METHODS.contains(method.toString().toUpperCase())) {
            errors.add(error("method", stepId, at + ".method unsupported: " + method));
        }
        for (String key : MAP_KEYS) {
            Object value = entry.get(key);
            if (value != null && !(value instanceof Map<?, ?>)) {
                errors.add(error(key, stepId, at + "." + key + " must be a map of name -> value"));
            }
        }
        Object status = entry.get("expectedStatus");
        if (status != null && !(status instanceof Number)) {
            try {
                Integer.parseInt(status.toString().trim());
            } catch (NumberFormatException e) {
                errors.add(error("expectedStatus", stepId, at + ".expectedStatus must be an integer: " + status));
            }
        }
    }

    private void checkWeight(String stepId, String at, Object raw, List<ValidationError> errors) {
        double weight = Double.NaN;
        if (raw instanceof Number n) {
            weight = n.doubleValue();
        } else if (raw != null) {
            try {
                weight = Double.parseDouble(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                // reste NaN
            }
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight) || weight <= 0) {
            errors.add(error("weight", stepId, at + ".weight must be a number > 0 (got " + raw + ")"));
        }
    }

    private static ValidationError error(String rule, String stepId, String message) {
        return new ValidationError("step.requests." + rule, "Step '" + stepId + "': " + message, "steps");
    }
}
