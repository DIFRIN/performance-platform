package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;

import io.gatling.javaapi.core.OpenInjectionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

/**
 * Implementation par defaut de {@link LoadModelTranslator}.
 * <p>
 * Traduit les 8 types de {@link LoadModelType} en etapes d'injection
 * Gatling Java DSL ({@link OpenInjectionStep}). Les parametres propres a
 * chaque type sont extraits du {@code Map<String, Object>} du
 * {@link LoadModel}.
 * <p>
 * <strong>Mapping :</strong>
 * <ul>
 *   <li>{@code RAMP} — {@code rampUsersPerSec(from).to(to).during(d)}</li>
 *   <li>{@code CONSTANT} — {@code constantUsersPerSec(n).during(d)}</li>
 *   <li>{@code SPIKE} — ramp base → spike sur spikeDuration, puis retour au base</li>
 *   <li>{@code STAIR} — {@code incrementUsersPerSec}.times().eachLevelLasting()</li>
 *   <li>{@code SOAK} — ramp initial puis constant sur la duree longue</li>
 *   <li>{@code BURST} — sequence de {@code nothingFor} + {@code atOnceUsers} repetee</li>
 *   <li>{@code RAMP_UP_DOWN} — rampUp + hold + rampDown</li>
 *   <li>{@code CUSTOM} — interpolation lineaire entre points</li>
 * </ul>
 */
@Component
public class DefaultLoadModelTranslator implements LoadModelTranslator {

    private static final Logger log = LoggerFactory.getLogger(DefaultLoadModelTranslator.class);

    // === Param keys ===

    private static final String KEY_FROM = "from";
    private static final String KEY_TO = "to";
    private static final String KEY_USERS_PER_SEC = "usersPerSec";
    private static final String KEY_DURATION_SECONDS = "durationSeconds";
    private static final String KEY_BASE = "base";
    private static final String KEY_SPIKE = "spike";
    private static final String KEY_SPIKE_DURATION_SECONDS = "spikeDurationSeconds";
    private static final String KEY_TOTAL_DURATION_SECONDS = "totalDurationSeconds";
    private static final String KEY_START_USERS_PER_SEC = "startUsersPerSec";
    private static final String KEY_INCREMENT_USERS_PER_SEC = "incrementUsersPerSec";
    private static final String KEY_STEPS = "steps";
    private static final String KEY_STEP_DURATION_SECONDS = "stepDurationSeconds";
    private static final String KEY_RAMP_DURATION_SECONDS = "rampDurationSeconds";
    private static final String KEY_USERS = "users";
    private static final String KEY_BURSTS = "bursts";
    private static final String KEY_INTERVAL_SECONDS = "intervalSeconds";
    private static final String KEY_HOLD_DURATION_SECONDS = "holdDurationSeconds";
    private static final String KEY_RAMP_UP_DURATION_SECONDS = "rampUpDurationSeconds";
    private static final String KEY_RAMP_DOWN_DURATION_SECONDS = "rampDownDurationSeconds";
    private static final String KEY_POINTS = "points";
    private static final String KEY_TIME_SECONDS = "timeSeconds";

    @Override
    public List<OpenInjectionStep> translate(LoadModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        LoadModelType type = model.type();
        Map<String, Object> params = model.parameters();
        log.debug("action=translate_load_model type={} paramKeys={}", type, params.keySet());

        return switch (type) {
            case RAMP -> translateRamp(params);
            case CONSTANT -> translateConstant(params);
            case SPIKE -> translateSpike(params);
            case STAIR -> translateStair(params);
            case SOAK -> translateSoak(params);
            case BURST -> translateBurst(params);
            case RAMP_UP_DOWN -> translateRampUpDown(params);
            case CUSTOM -> translateCustom(params);
        };
    }

    // === RAMP ===

    /**
     * {@code rampUsersPerSec(from).to(to).during(durationSeconds)}.
     * <p>Params: {@code from} (Number), {@code to} (Number), {@code durationSeconds} (Number).
     */
    private List<OpenInjectionStep> translateRamp(Map<String, Object> params) {
        double from = getDouble(params, KEY_FROM);
        double to = getDouble(params, KEY_TO);
        Duration duration = getDuration(params, KEY_DURATION_SECONDS);
        log.debug("action=translate_ramp from={} to={} duration={}", from, to, duration);
        return List.of(rampUsersPerSec(from).to(to).during(duration));
    }

    // === CONSTANT ===

    /**
     * {@code constantUsersPerSec(usersPerSec).during(durationSeconds)}.
     * <p>Params: {@code usersPerSec} (Number), {@code durationSeconds} (Number).
     */
    private List<OpenInjectionStep> translateConstant(Map<String, Object> params) {
        double rate = getDouble(params, KEY_USERS_PER_SEC);
        Duration duration = getDuration(params, KEY_DURATION_SECONDS);
        log.debug("action=translate_constant rate={} duration={}", rate, duration);
        return List.of(constantUsersPerSec(rate).during(duration));
    }

    // === SPIKE ===

    /**
     * Ramp {@code base -> spike} sur {@code spikeDurationSeconds},
     * puis constant au {@code base} pour le reste de {@code totalDurationSeconds}.
     * <p>Params: {@code base} (Number), {@code spike} (Number),
     * {@code spikeDurationSeconds} (Number), {@code totalDurationSeconds} (Number).
     */
    private List<OpenInjectionStep> translateSpike(Map<String, Object> params) {
        double base = getDouble(params, KEY_BASE);
        double spike = getDouble(params, KEY_SPIKE);
        Duration spikeDuration = getDuration(params, KEY_SPIKE_DURATION_SECONDS);
        Duration totalDuration = getDuration(params, KEY_TOTAL_DURATION_SECONDS);

        Duration remaining = totalDuration.minus(spikeDuration);
        if (remaining.isNegative() || remaining.isZero()) {
            remaining = Duration.ofSeconds(1);
        }

        log.debug("action=translate_spike base={} spike={} spikeDuration={} totalDuration={} remaining={}",
                base, spike, spikeDuration, totalDuration, remaining);

        List<OpenInjectionStep> steps = new ArrayList<>();
        // Montee de base a spike
        steps.add(rampUsersPerSec(base).to(spike).during(spikeDuration));
        // Retour au base
        steps.add(constantUsersPerSec(base).during(remaining));
        return List.copyOf(steps);
    }

    // === STAIR ===

    /**
     * {@code incrementUsersPerSec(step).times(steps)
     *       .eachLevelLasting(stepDurationSeconds).startingFrom(startUsersPerSec)}.
     * <p>Params: {@code startUsersPerSec} (Number), {@code incrementUsersPerSec} (Number),
     * {@code steps} (Number), {@code stepDurationSeconds} (Number).
     */
    private List<OpenInjectionStep> translateStair(Map<String, Object> params) {
        double start = getDouble(params, KEY_START_USERS_PER_SEC);
        double increment = getDouble(params, KEY_INCREMENT_USERS_PER_SEC);
        int steps = getInt(params, KEY_STEPS);
        Duration stepDuration = getDuration(params, KEY_STEP_DURATION_SECONDS);

        log.debug("action=translate_stair start={} increment={} steps={} stepDuration={}",
                start, increment, steps, stepDuration);

        return List.of(
                incrementUsersPerSec(increment)
                        .times(steps)
                        .eachLevelLasting(stepDuration)
                        .startingFrom(start)
        );
    }

    // === SOAK ===

    /**
     * Ramp initial suivi d'un plateau constant.
     * <p>Params: {@code usersPerSec} (Number), {@code durationSeconds} (Number),
     * {@code rampDurationSeconds} (Number).
     */
    private List<OpenInjectionStep> translateSoak(Map<String, Object> params) {
        double rate = getDouble(params, KEY_USERS_PER_SEC);
        Duration totalDuration = getDuration(params, KEY_DURATION_SECONDS);
        Duration rampDuration = getDuration(params, KEY_RAMP_DURATION_SECONDS);

        log.debug("action=translate_soak rate={} totalDuration={} rampDuration={}",
                rate, totalDuration, rampDuration);

        List<OpenInjectionStep> steps = new ArrayList<>();
        // Ramp initiale jusqu'au taux cible
        steps.add(rampUsersPerSec(0).to(rate).during(rampDuration));
        // Plateau constant
        Duration remaining = totalDuration.minus(rampDuration);
        if (!remaining.isNegative() && !remaining.isZero()) {
            steps.add(constantUsersPerSec(rate).during(remaining));
        }
        return List.copyOf(steps);
    }

    // === BURST ===

    /**
     * Sequence de {@code nothingFor(intervalSeconds)} +
     * {@code atOnceUsers(users)} repetee {@code bursts} fois.
     * <p>Params: {@code users} (Number), {@code bursts} (Number),
     * {@code intervalSeconds} (Number).
     */
    private List<OpenInjectionStep> translateBurst(Map<String, Object> params) {
        int users = getInt(params, KEY_USERS);
        int bursts = getInt(params, KEY_BURSTS);
        Duration interval = getDuration(params, KEY_INTERVAL_SECONDS);

        log.debug("action=translate_burst users={} bursts={} interval={}", users, bursts, interval);

        List<OpenInjectionStep> steps = new ArrayList<>();
        for (int i = 0; i < bursts; i++) {
            if (i > 0) {
                steps.add(nothingFor(interval));
            }
            steps.add(atOnceUsers(users));
        }
        return List.copyOf(steps);
    }

    // === RAMP_UP_DOWN ===

    /**
     * Montee progressive, plateau, descente progressive.
     * <p>Params: {@code from} (Number), {@code to} (Number),
     * {@code rampUpDurationSeconds} (Number), {@code holdDurationSeconds} (Number),
     * {@code rampDownDurationSeconds} (Number).
     */
    private List<OpenInjectionStep> translateRampUpDown(Map<String, Object> params) {
        double from = getDouble(params, KEY_FROM);
        double to = getDouble(params, KEY_TO);
        Duration rampUp = getDuration(params, KEY_RAMP_UP_DURATION_SECONDS);
        Duration hold = getDuration(params, KEY_HOLD_DURATION_SECONDS);
        Duration rampDown = getDuration(params, KEY_RAMP_DOWN_DURATION_SECONDS);

        log.debug("action=translate_ramp_up_down from={} to={} rampUp={} hold={} rampDown={}",
                from, to, rampUp, hold, rampDown);

        List<OpenInjectionStep> steps = new ArrayList<>();
        // Montee
        steps.add(rampUsersPerSec(from).to(to).during(rampUp));
        // Plateau
        if (!hold.isNegative() && !hold.isZero()) {
            steps.add(constantUsersPerSec(to).during(hold));
        }
        // Descente
        steps.add(rampUsersPerSec(to).to(from).during(rampDown));
        return List.copyOf(steps);
    }

    // === CUSTOM ===

    /**
     * CC-02: pipeline cohesif — validation points, iteration segments,
     * construction ramps. L'extraction fragmenterait la logique
     * d'interpolation lineaire.
     * <p>Interpolation lineaire entre les points specifies.
     * Chaque segment est un ramp entre deux points consecutifs.
     * <p>Params: {@code points} (List of Map) — chaque point a
     * {@code timeSeconds} (Number) et {@code usersPerSec} (Number).
     */
    @SuppressWarnings("unchecked")
    private List<OpenInjectionStep> translateCustom(Map<String, Object> params) {
        Object pointsObj = params.get(KEY_POINTS);
        if (!(pointsObj instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException(
                    "CUSTOM load model requires non-empty 'points' list");
        }

        List<Map<String, Object>> points = rawList.stream()
                .filter(Map.class::isInstance)
                .map(o -> (Map<String, Object>) o)
                .toList();

        if (points.size() < 2) {
            throw new IllegalArgumentException(
                    "CUSTOM load model requires at least 2 points, got " + points.size());
        }

        log.debug("action=translate_custom pointCount={}", points.size());

        List<OpenInjectionStep> steps = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            Map<String, Object> prev = points.get(i - 1);
            Map<String, Object> curr = points.get(i);

            double prevRate = getDouble(prev, KEY_USERS_PER_SEC);
            double currRate = getDouble(curr, KEY_USERS_PER_SEC);
            double prevTime = getDouble(prev, KEY_TIME_SECONDS);
            double currTime = getDouble(curr, KEY_TIME_SECONDS);

            double segmentDuration = currTime - prevTime;
            if (segmentDuration <= 0) {
                throw new IllegalArgumentException(
                        "CUSTOM points must have strictly increasing timeSeconds, " +
                        "got " + prevTime + " -> " + currTime);
            }

            var segment = Duration.ofSeconds((long) segmentDuration);
            steps.add(rampUsersPerSec(prevRate).to(currRate).during(segment));
        }
        return List.copyOf(steps);
    }

    // === Parameter helpers ===

    private static double getDouble(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: " + key);
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a Number, got " +
                value.getClass().getSimpleName());
    }

    private static int getInt(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: " + key);
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a Number, got " +
                value.getClass().getSimpleName());
    }

    private static Duration getDuration(Map<String, Object> params, String key) {
        return Duration.ofSeconds((long) getDouble(params, key));
    }
}
