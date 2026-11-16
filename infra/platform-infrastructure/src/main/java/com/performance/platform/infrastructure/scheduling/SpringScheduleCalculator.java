package com.performance.platform.infrastructure.scheduling;

import com.performance.platform.application.exception.InvalidScheduleException;
import com.performance.platform.application.ports.out.ScheduleCalculator;
import com.performance.platform.application.schedule.ScheduleKind;
import com.performance.platform.application.schedule.ScheduleSpec;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolution des expressions de planification saisies par l'utilisateur.
 * <p>
 * Syntaxes supportees :
 * <ul>
 *   <li>{@code now} / vide — execution immediate</li>
 *   <li>{@code in 10m}, {@code in 1h30m}, {@code in 2d} — delai relatif</li>
 *   <li>{@code 2026-11-13T09:00}, {@code 2026-11-13 09:00},
 *       {@code 2026-11-13T09:00:00Z} — date/heure absolue</li>
 *   <li>{@code cron:0 0 2 * * *} ou {@code 0 0 2 * * *} — recurrence cron
 *       (5 champs standards acceptes, normalises en 6 champs Spring)</li>
 * </ul>
 */
@Component
public class SpringScheduleCalculator implements ScheduleCalculator {

    private static final Pattern DELAY_TOKEN =
            Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(ms|s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)");
    private static final Pattern CRON_FIELD = Pattern.compile("[0-9A-Za-z*?/,#L\\-]+");

    private final Clock clock;

    public SpringScheduleCalculator() {
        this(Clock.systemDefaultZone());
    }

    SpringScheduleCalculator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ScheduleSpec parse(String expression) {
        String raw = expression == null ? "" : expression.strip();
        if (raw.isEmpty() || raw.equalsIgnoreCase("now") || raw.equalsIgnoreCase("immediate")) {
            return ScheduleSpec.immediate();
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();

        if (lower.startsWith("in ")) {
            Duration delay = parseDelay(raw.substring(3).strip());
            if (delay.isNegative() || delay.isZero()) {
                throw new InvalidScheduleException(
                        "Schedule delay must be positive: '" + expression + "'");
            }
            return ScheduleSpec.once(raw, now.plus(delay));
        }

        if (lower.startsWith("cron:")) {
            return ScheduleSpec.cron(normalizeCron(raw.substring("cron:".length()).strip()));
        }

        if (looksLikeCron(raw)) {
            return ScheduleSpec.cron(normalizeCron(raw));
        }

        if (lower.startsWith("at ")) {
            return ScheduleSpec.once(raw, parseInstant(raw.substring(3).strip()));
        }

        return ScheduleSpec.once(raw, parseInstant(raw));
    }

    @Override
    public Instant nextRun(ScheduleSpec spec, Instant from) {
        return switch (spec.kind()) {
            case IMMEDIATE -> from;
            case ONCE -> spec.runAt();
            case CRON -> nextCron(spec.expression(), from);
        };
    }

    // ------------------------------------------------------------------
    // Delays
    // ------------------------------------------------------------------

    private Duration parseDelay(String value) {
        if (value.isEmpty()) {
            throw new InvalidScheduleException("Missing delay after 'in'");
        }
        if (value.regionMatches(true, 0, "P", 0, 1)) {
            try {
                return Duration.parse(value.toUpperCase(Locale.ROOT));
            } catch (DateTimeParseException e) {
                throw new InvalidScheduleException("Invalid ISO-8601 duration: '" + value + "'", e);
            }
        }

        Matcher matcher = DELAY_TOKEN.matcher(value);
        Duration total = Duration.ZERO;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            double amount = Double.parseDouble(matcher.group(1));
            total = total.plus(durationOf(amount, matcher.group(2)));
        }
        if (!matched) {
            throw new InvalidScheduleException(
                    "Invalid delay '" + value + "'. Use e.g. 'in 10m', 'in 1h30m' or 'in 2d'");
        }
        return total;
    }

    private static Duration durationOf(double amount, String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "ms" -> Duration.ofMillis(Math.round(amount));
            case "s", "sec", "secs", "second", "seconds" -> Duration.ofMillis(Math.round(amount * 1_000));
            case "m", "min", "mins", "minute", "minutes" -> Duration.ofMillis(Math.round(amount * 60_000));
            case "h", "hr", "hrs", "hour", "hours" -> Duration.ofMillis(Math.round(amount * 3_600_000));
            case "d", "day", "days" -> Duration.ofMillis(Math.round(amount * 86_400_000));
            default -> throw new InvalidScheduleException("Unsupported delay unit: " + unit);
        };
    }

    // ------------------------------------------------------------------
    // Absolute instant
    // ------------------------------------------------------------------

    private Instant parseInstant(String value) {
        String normalized = value.replace(' ', 'T');
        Instant target = null;

        try {
            target = Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // try other formats
        }
        if (target == null) {
            try {
                target = OffsetDateTime.parse(normalized).toInstant();
            } catch (DateTimeParseException ignored) {
                // try other formats
            }
        }
        if (target == null) {
            try {
                target = LocalDateTime.parse(normalized).atZone(clock.getZone()).toInstant();
            } catch (DateTimeParseException ignored) {
                // try other formats
            }
        }
        if (target == null) {
            try {
                target = LocalDate.parse(normalized).atStartOfDay(clock.getZone()).toInstant();
            } catch (DateTimeParseException ignored) {
                // fall through to error
            }
        }
        if (target == null) {
            throw new InvalidScheduleException(
                    "Invalid schedule '" + value + "'. Use 'in 10m', an ISO-8601 date or a cron expression");
        }
        if (!target.isAfter(clock.instant())) {
            throw new InvalidScheduleException("Scheduled time must be in the future: '" + value + "'");
        }
        return target;
    }

    // ------------------------------------------------------------------
    // Cron
    // ------------------------------------------------------------------

    private static boolean looksLikeCron(String value) {
        if (value.startsWith("@")) {
            return true;
        }
        String[] fields = value.split("\\s+");
        if (fields.length < 5 || fields.length > 6) {
            return false;
        }
        for (String field : fields) {
            if (!CRON_FIELD.matcher(field).matches()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeCron(String value) {
        if (value.isEmpty()) {
            throw new InvalidScheduleException("Missing cron expression");
        }
        String normalized = value;
        if (!normalized.startsWith("@")) {
            String[] fields = normalized.split("\\s+");
            if (fields.length == 5) {
                normalized = "0 " + normalized;
            } else if (fields.length != 6) {
                throw new InvalidScheduleException(
                        "Cron expression must have 5 or 6 fields: '" + value + "'");
            }
        }
        try {
            CronExpression.parse(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidScheduleException("Invalid cron expression: '" + value + "'", e);
        }
        return normalized;
    }

    private Instant nextCron(String expression, Instant from) {
        CronExpression cron = CronExpression.parse(expression);
        ZonedDateTime next = cron.next(from.atZone(clock.getZone()));
        if (next == null) {
            throw new InvalidScheduleException("Cron expression has no future occurrence: '" + expression + "'");
        }
        return next.toInstant();
    }
}
