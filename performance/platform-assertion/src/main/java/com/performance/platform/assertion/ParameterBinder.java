package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.Severity;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lecture typee des parametres d'une assertion.
 * <p>
 * Centralise les messages d'erreur et evite la duplication presente dans
 * chaque adapter ({@code getRequiredStringParam}, {@code getRequiredDoubleParam},
 * {@code getStringParam}...). Les erreurs sont des
 * {@link IllegalArgumentException} converties en statut {@code ERROR} par
 * l'executor, ou en erreur de validation au parsing.
 */
public final class ParameterBinder {

    private final Map<String, Object> parameters;

    public ParameterBinder(Map<String, Object> parameters) {
        this.parameters = parameters == null ? Map.of() : parameters;
    }

    public static ParameterBinder of(Map<String, Object> parameters) {
        return new ParameterBinder(parameters);
    }

    public boolean has(String key) {
        return parameters.get(key) != null;
    }

    public Object raw(String key) {
        return parameters.get(key);
    }

    public String optionalString(String key, String defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().strip();
        return text.isEmpty() ? defaultValue : text;
    }

    public String requireString(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: '" + key + "'");
        }
        String text = value.toString().strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be a non-empty string");
        }
        return text;
    }

    public double requireDouble(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: '" + key + "'");
        }
        return toDouble(key, value);
    }

    public Double optionalDouble(String key, Double defaultValue) {
        Object value = parameters.get(key);
        return value == null ? defaultValue : toDouble(key, value);
    }

    public double optionalDouble(String key, double defaultValue) {
        Object value = parameters.get(key);
        return value == null ? defaultValue : toDouble(key, value);
    }

    public double optionalDouble(String key) {
        Object value = parameters.get(key);
        return value == null ? Double.NaN : toDouble(key, value);
    }

    public int optionalInt(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be an integer, got: " + value);
        }
    }

    public boolean optionalBoolean(String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString().strip());
    }

    public Severity optionalSeverity(String key, Severity defaultValue) {
        String text = optionalString(key, null);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Severity.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be CRITICAL or WARNING, got: " + text);
        }
    }

    public Duration optionalDuration(String key, Duration defaultValue) {
        String text = optionalString(key, null);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Duration.parse("PT" + text.toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be a duration like 30s or 2m, got: " + text);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> optionalMap(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Parameter '" + key + "' must be a map, got: " + value);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> optionalListOfMaps(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        throw new IllegalArgumentException("Parameter '" + key + "' must be a list, got: " + value);
    }

    private static double toDouble(String key, Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be a number, got: " + value);
        }
    }
}
