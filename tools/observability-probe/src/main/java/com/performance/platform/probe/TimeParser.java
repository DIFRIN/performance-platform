package com.performance.platform.probe;

import com.performance.platform.probe.model.ProbeWindow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse des fenetres CLI : ISO-8601 ou relatif ({@code now-30m}, {@code now}).
 */
public final class TimeParser {

    private static final Pattern RELATIVE =
            Pattern.compile("now(?:([+-])(\\d+)([smhd]))?");

    private TimeParser() {
    }

    public static Instant parseInstant(String value, Instant now) {
        if (value == null || value.isBlank()) {
            throw ProbeException.config("Missing time value");
        }
        String trimmed = value.strip();
        Matcher relative = RELATIVE.matcher(trimmed.toLowerCase());
        if (relative.matches()) {
            if (relative.group(1) == null) {
                return now;
            }
            long amount = Long.parseLong(relative.group(2));
            Duration delta = switch (relative.group(3)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofDays(amount);
            };
            return "-".equals(relative.group(1)) ? now.minus(delta) : now.plus(delta);
        }
        Instant parsed = tryParse(trimmed, Instant::parse);
        if (parsed == null) parsed = tryParse(trimmed, text -> OffsetDateTime.parse(text).toInstant());
        if (parsed == null) parsed = tryParse(trimmed,
                text -> LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant());
        if (parsed == null) parsed = tryParse(trimmed,
                text -> LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
        if (parsed != null) {
            return parsed;
        }
        throw ProbeException.config("Invalid time '" + value
                + "'. Use ISO-8601 or a relative form such as now-30m");
    }

    private static Instant tryParse(String value,
                                    java.util.function.Function<String, Instant> parser) {
        try {
            return parser.apply(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static ProbeWindow window(String from, String to, String resolution) {
        Instant now = Instant.now();
        Instant start = parseInstant(from, now);
        Instant end = parseInstant(to, now);
        if (end.isBefore(start)) {
            throw ProbeException.config("--to must be after --from");
        }
        return new ProbeWindow(start, end, parseDurationMillis(resolution, Duration.ofSeconds(15)));
    }

    public static Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Duration.ofMillis(parseDurationMillis(value, defaultValue));
    }

    public static long parseDurationMillis(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue.toMillis();
        }
        String trimmed = value.strip().toLowerCase();
        if (trimmed.startsWith("p")) {
            return Duration.parse(trimmed.toUpperCase()).toMillis();
        }
        Matcher matcher = Pattern.compile("(\\d+)(ms|s|m|h|d)").matcher(trimmed);
        if (!matcher.matches()) {
            throw ProbeException.config("Invalid duration '" + value + "' (use 30s, 1m, 15m, 1h)");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> amount;
            case "s" -> amount * 1_000;
            case "m" -> amount * 60_000;
            case "h" -> amount * 3_600_000;
            default -> amount * 86_400_000;
        };
    }
}
