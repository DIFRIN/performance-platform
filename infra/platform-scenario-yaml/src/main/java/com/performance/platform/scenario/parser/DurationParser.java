package com.performance.platform.scenario.parser;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseur de durees au format humain ("30s", "5m", "2h", "500ms") vers java.time.Duration.
 * Utilitaire interne au module — 0 dependance framework.
 */
public final class DurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)\\s*(ms|s|m|h)$");

    private DurationParser() {
        // utilitaire — pas d'instanciation
    }

    /**
     * Parse une chaine de duree et retourne la Duration correspondante.
     * Formats supportes : "500ms" (millisecondes), "30s" (secondes), "5m" (minutes), "2h" (heures).
     *
     * @param input la chaine a parser (ex: "30s", "5m"), peut etre null
     * @return la Duration parsee, ou null si input est null/blank
     * @throws IllegalArgumentException si le format est invalide
     */
    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();
        var matcher = DURATION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid duration format: '" + input + "'. Expected format: <number><unit> where unit is ms, s, m, or h (e.g., 30s, 5m, 2h, 500ms)"
            );
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }

    /**
     * Parse une chaine de duree avec une valeur par defaut si null/blank.
     *
     * @param input        la chaine a parser
     * @param defaultValue la Duration par defaut si input est null/blank
     * @return la Duration parsee, ou defaultValue
     * @throws IllegalArgumentException si le format est invalide
     */
    public static Duration parseOrDefault(String input, Duration defaultValue) {
        Duration parsed = parse(input);
        return parsed != null ? parsed : defaultValue;
    }
}
