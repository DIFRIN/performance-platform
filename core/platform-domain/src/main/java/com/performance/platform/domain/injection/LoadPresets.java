package com.performance.platform.domain.injection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Expansion des presets (types historiques de {@link LoadModelType}) en {@link WorkloadPlan}. */
public final class LoadPresets {

    private LoadPresets() {
    }

    /** type = RAMP | RAMP_UP_DOWN | CONSTANT | SPIKE | STAIR | SOAK | BURST | CUSTOM. */
    public static WorkloadPlan expand(String type, Map<String, Object> parameters) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: type");
        }
        LoadModelType t;
        try {
            t = LoadModelType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown preset type: " + type);
        }
        Map<String, Object> p = parameters == null ? Map.of() : parameters;
        List<Stage> stages = switch (t) {
            case RAMP -> List.of(
                    new Ramp(dbl(p, "from"), dbl(p, "to"), dur(p, "durationSeconds"), true));
            case CONSTANT -> List.of(
                    new Hold(dbl(p, "usersPerSec"), dur(p, "durationSeconds"), true));
            case SPIKE -> spike(p);
            case STAIR -> stair(p);
            case SOAK -> soak(p);
            case BURST -> burst(p);
            case RAMP_UP_DOWN -> rampUpDown(p);
            case CUSTOM -> custom(p);
        };
        return new WorkloadPlan(ArrivalModel.OPEN, LoadScope.PER_AGENT, stages);
    }

    private static List<Stage> spike(Map<String, Object> p) {
        double base = dbl(p, "base");
        double spike = dbl(p, "spike");
        Duration spikeDuration = dur(p, "spikeDurationSeconds");
        Duration total = dur(p, "totalDurationSeconds");
        Duration remaining = total.minus(spikeDuration);
        if (remaining.isNegative() || remaining.isZero()) {
            remaining = Duration.ofSeconds(1);
        }
        return List.of(new Ramp(base, spike, spikeDuration, true), new Hold(base, remaining, true));
    }

    private static List<Stage> stair(Map<String, Object> p) {
        double start = dbl(p, "startUsersPerSec");
        double increment = dbl(p, "incrementUsersPerSec");
        int steps = (int) dbl(p, "steps");
        Duration stepDuration = dur(p, "stepDurationSeconds");
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be > 0");
        }
        List<Stage> stages = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            stages.add(new Hold(start + i * increment, stepDuration, true));
        }
        return stages;
    }

    private static List<Stage> soak(Map<String, Object> p) {
        double rate = dbl(p, "usersPerSec");
        Duration total = dur(p, "durationSeconds");
        Duration ramp = dur(p, "rampDurationSeconds");
        List<Stage> stages = new ArrayList<>();
        stages.add(new Ramp(0, rate, ramp, true));
        Duration remaining = total.minus(ramp);
        if (!remaining.isNegative() && !remaining.isZero()) {
            stages.add(new Hold(rate, remaining, true));
        }
        return stages;
    }

    private static List<Stage> burst(Map<String, Object> p) {
        int users = (int) dbl(p, "users");
        int bursts = (int) dbl(p, "bursts");
        Duration interval = dur(p, "intervalSeconds");
        if (bursts <= 0) {
            throw new IllegalArgumentException("bursts must be > 0");
        }
        List<Stage> stages = new ArrayList<>();
        for (int i = 0; i < bursts; i++) {
            if (i > 0) {
                stages.add(new Pause(interval, true));
            }
            stages.add(new Burst(users, true));
        }
        return stages;
    }

    private static List<Stage> rampUpDown(Map<String, Object> p) {
        double from = dbl(p, "from");
        double to = dbl(p, "to");
        Duration up = dur(p, "rampUpDurationSeconds");
        Duration hold = dur(p, "holdDurationSeconds");
        Duration down = dur(p, "rampDownDurationSeconds");
        List<Stage> stages = new ArrayList<>();
        stages.add(new Ramp(from, to, up, true));
        if (!hold.isNegative() && !hold.isZero()) {
            stages.add(new Hold(to, hold, true));
        }
        stages.add(new Ramp(to, from, down, true));
        return stages;
    }

    private static List<Stage> custom(Map<String, Object> p) {
        if (!(p.get("points") instanceof List<?> raw) || raw.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: points");
        }
        List<Map<String, Object>> points = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> copy = new java.util.HashMap<>();
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                points.add(copy);
            }
        }
        if (points.size() < 2) {
            throw new IllegalArgumentException("points requires at least 2 points, got " + points.size());
        }
        List<Stage> stages = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            Map<String, Object> prev = points.get(i - 1);
            Map<String, Object> curr = points.get(i);
            double segment = dbl(curr, "timeSeconds") - dbl(prev, "timeSeconds");
            if (segment <= 0) {
                throw new IllegalArgumentException("timeSeconds must be strictly increasing");
            }
            stages.add(new Ramp(dbl(prev, "usersPerSec"), dbl(curr, "usersPerSec"),
                    Duration.ofSeconds((long) segment), true));
        }
        return stages;
    }

    private static double dbl(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Parameter '" + key + "' must be a Number");
    }

    private static Duration dur(Map<String, Object> p, String key) {
        return Duration.ofSeconds((long) dbl(p, key));
    }
}
