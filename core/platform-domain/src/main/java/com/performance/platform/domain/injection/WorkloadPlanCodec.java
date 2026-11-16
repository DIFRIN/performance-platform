package com.performance.platform.domain.injection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Codec Map ↔ WorkloadPlan et hash SHA-256 de la forme canonique. Java pur. */
public final class WorkloadPlanCodec {

    public static final String PARAM_KEY = "workload";

    private static final Pattern SHORT_DURATION = Pattern.compile("^(\\d+)([smh])$");

    private WorkloadPlanCodec() {}

    public static WorkloadPlan fromMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("workload must not be null");
        }
        try {
            ArrivalModel model = parseEnum(map.get("model"), "model", ArrivalModel.class, null);
            LoadScope scope = parseEnum(map.get("scope"), "scope", LoadScope.class, LoadScope.PER_AGENT);
            Object raw = map.get("stages");
            if (!(raw instanceof List<?> list)) {
                throw new IllegalArgumentException("stages must be a list");
            }
            List<Stage> stages = new ArrayList<>();
            for (Object o : list) {
                stages.add(parseStage(o));
            }
            return new WorkloadPlan(model, scope, stages);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("invalid workload: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> toMap(WorkloadPlan plan) {
        Map<String, Object> out = new TreeMap<>();
        out.put("model", plan.model().name().toLowerCase(Locale.ROOT));
        out.put("scope", plan.scope().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        List<Object> stages = new ArrayList<>();
        for (Stage s : plan.stages()) {
            stages.add(stageToMap(s));
        }
        out.put("stages", stages);
        return out;
    }

    public static String hash(WorkloadPlan plan) {
        StringBuilder sb = new StringBuilder();
        write(sb, toMap(plan));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ---- canonical form ----

    private static Map<String, Object> stageToMap(Stage s) {
        Map<String, Object> body = new TreeMap<>();
        String kind;
        switch (s) {
            case Ramp r -> {
                kind = "ramp";
                body.put("from", r.from());
                body.put("to", r.to());
                body.put("duration", r.duration().toString());
            }
            case Hold h -> {
                kind = "hold";
                body.put("level", h.level());
                body.put("duration", h.duration().toString());
            }
            case Burst b -> {
                kind = "burst";
                body.put("users", b.users());
            }
            case Pause p -> {
                kind = "pause";
                body.put("duration", p.duration().toString());
            }
        }
        body.put("measured", s.measured());
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put(kind, body);
        return wrapper;
    }

    private static void write(StringBuilder sb, Object v) {
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : new TreeMap<>(m).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append("\":");
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                write(sb, l.get(i));
            }
            sb.append(']');
        } else if (v instanceof String s) {
            sb.append('"').append(s).append('"');
        } else {
            sb.append(v);
        }
    }

    // ---- parsing ----

    private static Stage parseStage(Object o) {
        if (!(o instanceof Map<?, ?> m) || m.size() != 1) {
            throw new IllegalArgumentException("each stage must be a map with exactly one kind");
        }
        Map.Entry<?, ?> e = m.entrySet().iterator().next();
        String kind = String.valueOf(e.getKey());
        if (!(e.getValue() instanceof Map<?, ?> b)) {
            throw new IllegalArgumentException("stage '" + kind + "' must be a map");
        }
        boolean measured = bool(b.get("measured"));
        return switch (kind) {
            case "ramp" -> new Ramp(num(b, "from"), num(b, "to"), duration(b.get("duration")), measured);
            case "hold" -> new Hold(num(b, "level"), duration(b.get("duration")), measured);
            case "burst" -> new Burst(whole(b, "users"), measured);
            case "pause" -> new Pause(duration(b.get("duration")), measured);
            default -> throw new IllegalArgumentException("unknown stage kind: " + kind);
        };
    }

    private static <E extends Enum<E>> E parseEnum(Object v, String name, Class<E> type, E dflt) {
        if (v == null) {
            if (dflt != null) return dflt;
            throw new IllegalArgumentException(name + " is required");
        }
        String norm = v.toString().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, norm);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid " + name + ": " + v);
        }
    }

    private static boolean bool(Object v) {
        if (v == null) return true;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        throw new IllegalArgumentException("invalid measured: " + v);
    }

    private static double num(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ex) {
                // fall through
            }
        }
        throw new IllegalArgumentException(key + " must be a number");
    }

    private static long whole(Map<?, ?> m, String key) {
        double d = num(m, key);
        if (d != Math.rint(d)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return (long) d;
    }

    private static Duration duration(Object v) {
        if (v instanceof Duration d) return d;
        if (v == null) throw new IllegalArgumentException("duration is required");
        String s = v.toString().trim();
        Matcher m = SHORT_DURATION.matcher(s);
        try {
            if (m.matches()) {
                long n = Long.parseLong(m.group(1));
                return switch (m.group(2)) {
                    case "s" -> Duration.ofSeconds(n);
                    case "m" -> Duration.ofMinutes(n);
                    default -> Duration.ofHours(n);
                };
            }
            return Duration.parse(s);
        } catch (DateTimeParseException | ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("invalid duration: " + s, ex);
        }
    }
}
