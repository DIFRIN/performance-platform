package com.performance.platform.domain.template;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the dynamic placeholders a scenario may put in any part of a request
 * (path, path params, query params, headers, body). Shared by every executor that sends
 * requests (http-client, gatling) so the syntax is identical everywhere.
 *
 * <pre>
 * {index}  {index+100}  {index*2}  {index%50}  {index:6}   incremental counter (+ arithmetic, zero-pad)
 * {uuid}                      random UUID
 * {timestamp}                 epoch millis
 * {date} {date:yyyy-MM-dd}    UTC date-time (ISO instant by default, or a DateTimeFormatter pattern)
 * {random:1-1000}             random integer in [min, max]
 * {randomString:8}            random alphanumeric string
 * {randomHex:8}               random hexadecimal string
 * {choice:a|b|c}              random pick
 * {cycle:a|b|c}               round-robin pick driven by {index}
 * {executionId} {stepId}      identifiers of the running execution / step
 * </pre>
 *
 * Unknown names are left untouched, so JSON bodies such as {@code {"id":1}} are safe.
 * A known placeholder with invalid arguments raises {@link IllegalArgumentException}.
 */
public final class DynamicValues {

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z]+)([^{}]*)}");
    private static final Pattern INDEX_ARGS = Pattern.compile("(?:([+\\-*/%])(\\d+))?(?::(\\d+))?");
    private static final Pattern RANGE_ARGS = Pattern.compile(":(\\d+)-(\\d+)");
    private static final Pattern LENGTH_ARGS = Pattern.compile(":(\\d{1,4})");
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String HEX = "0123456789abcdef";

    /** Values the placeholders are resolved against; {@code index} is evaluated on each use. */
    public record Context(String executionId, String stepId, LongSupplier index) {
        public Context {
            executionId = executionId == null ? "" : executionId;
            stepId = stepId == null ? "" : stepId;
            index = index == null ? () -> 1L : index;
        }

        public static Context of(String executionId, String stepId) {
            return new Context(executionId, stepId, () -> 1L);
        }
    }

    private DynamicValues() {
    }

    public static String resolve(String template, Context ctx) {
        return transform(template, ctx, false);
    }

    /**
     * Resolves every string of a structured value (YAML map / list / scalar). Maps keep their
     * keys and order; non-string scalars are returned unchanged.
     */
    public static Object resolveDeep(Object value, Context ctx) {
        if (value instanceof String s) return resolve(s, ctx);
        if (value instanceof java.util.Map<?, ?> map) {
            var out = new java.util.LinkedHashMap<Object, Object>();
            map.forEach((k, v) -> out.put(k, resolveDeep(v, ctx)));
            return out;
        }
        if (value instanceof java.util.Collection<?> items) {
            return items.stream().map(item -> resolveDeep(item, ctx)).toList();
        }
        return value;
    }

    /** Regex matching every value {@link #resolve} could produce; literal parts are quoted. */
    public static String toRegex(String template, Context ctx) {
        return transform(template, ctx, true);
    }

    public static boolean isDynamic(String template) {
        if (template == null || template.indexOf('{') < 0) return false;
        Matcher m = TOKEN.matcher(template);
        while (m.find()) {
            if (isKnown(m.group(1))) return true;
        }
        return false;
    }

    private static String transform(String template, Context ctx, boolean regex) {
        if (template == null) return null;
        Matcher m = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            if (!isKnown(m.group(1))) continue;
            out.append(literal(template.substring(last, m.start()), regex));
            out.append(regex ? tokenRegex(m.group(1), m.group(2), ctx) : tokenValue(m.group(1), m.group(2), ctx));
            last = m.end();
        }
        return out.append(literal(template.substring(last), regex)).toString();
    }

    private static String literal(String text, boolean regex) {
        return regex && !text.isEmpty() ? Pattern.quote(text) : text;
    }

    private static boolean isKnown(String name) {
        return switch (name) {
            case "index", "uuid", "timestamp", "date", "random", "randomString", "randomHex",
                 "choice", "cycle", "executionId", "stepId" -> true;
            default -> false;
        };
    }

    private static String tokenValue(String name, String args, Context ctx) {
        return switch (name) {
            case "index" -> index(args, ctx);
            case "uuid" -> UUID.randomUUID().toString();
            case "timestamp" -> String.valueOf(System.currentTimeMillis());
            case "date" -> date(args);
            case "random" -> String.valueOf(randomInt(args));
            case "randomString" -> randomChars(ALPHANUM, length(name, args));
            case "randomHex" -> randomChars(HEX, length(name, args));
            case "choice" -> {
                String[] options = options(name, args);
                yield options[ThreadLocalRandom.current().nextInt(options.length)];
            }
            case "cycle" -> {
                String[] options = options(name, args);
                yield options[(int) Math.floorMod(ctx.index().getAsLong() - 1, (long) options.length)];
            }
            case "executionId" -> ctx.executionId();
            default -> ctx.stepId();
        };
    }

    private static String tokenRegex(String name, String args, Context ctx) {
        return switch (name) {
            case "index", "timestamp", "random" -> "\\d+";
            case "uuid" -> "[0-9a-fA-F-]{36}";
            case "date" -> ".+";
            case "randomString" -> "[A-Za-z0-9]{" + length(name, args) + "}";
            case "randomHex" -> "[0-9a-f]{" + length(name, args) + "}";
            case "choice", "cycle" -> {
                var alternatives = new StringBuilder("(?:");
                String separator = "";
                for (String option : options(name, args)) {
                    alternatives.append(separator).append(Pattern.quote(option));
                    separator = "|";
                }
                yield alternatives.append(')').toString();
            }
            case "executionId" -> Pattern.quote(ctx.executionId());
            default -> Pattern.quote(ctx.stepId());
        };
    }

    private static String index(String args, Context ctx) {
        Matcher m = INDEX_ARGS.matcher(args);
        if (!m.matches()) throw invalid("index", args);
        long value = ctx.index().getAsLong();
        if (m.group(1) != null) {
            long operand = Long.parseLong(m.group(2));
            value = switch (m.group(1).charAt(0)) {
                case '+' -> value + operand;
                case '-' -> value - operand;
                case '*' -> value * operand;
                case '/' -> operand == 0 ? zeroOperand() : value / operand;
                default -> operand == 0 ? zeroOperand() : value % operand;
            };
        }
        String text = String.valueOf(value);
        if (m.group(3) == null) return text;
        return "0".repeat(Math.max(0, Integer.parseInt(m.group(3)) - text.length())) + text;
    }

    private static long zeroOperand() {
        throw new IllegalArgumentException("Invalid dynamic placeholder {index}: division by zero");
    }

    private static String date(String args) {
        if (args.isEmpty()) return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        if (args.charAt(0) != ':') throw invalid("date", args);
        try {
            return DateTimeFormatter.ofPattern(args.substring(1)).withZone(ZoneOffset.UTC).format(Instant.now());
        } catch (IllegalArgumentException e) {
            throw invalid("date", args);
        }
    }

    private static long randomInt(String args) {
        if (args.isEmpty()) return ThreadLocalRandom.current().nextLong(0, 1_000_000);
        Matcher m = RANGE_ARGS.matcher(args);
        if (!m.matches()) throw invalid("random", args);
        long min = Long.parseLong(m.group(1));
        long max = Long.parseLong(m.group(2));
        if (max < min || max == Long.MAX_VALUE) throw invalid("random", args);
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static int length(String name, String args) {
        Matcher m = LENGTH_ARGS.matcher(args);
        if (!m.matches()) throw invalid(name, args);
        return Integer.parseInt(m.group(1));
    }

    private static String randomChars(String alphabet, int length) {
        var random = ThreadLocalRandom.current();
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static String[] options(String name, String args) {
        if (args.length() < 2 || args.charAt(0) != ':') throw invalid(name, args);
        return args.substring(1).split("\\|", -1);
    }

    private static IllegalArgumentException invalid(String name, String args) {
        return new IllegalArgumentException("Invalid dynamic placeholder {" + name + args + "}");
    }
}
