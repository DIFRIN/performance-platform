package com.performance.platform.domain.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoadPresetsTest {

    private static Duration s(long n) {
        return Duration.ofSeconds(n);
    }

    @Test
    void ramp() {
        var plan = LoadPresets.expand("RAMP", Map.of("from", 1, "to", 10, "durationSeconds", 60));
        assertEquals(ArrivalModel.OPEN, plan.model());
        assertEquals(LoadScope.PER_AGENT, plan.scope());
        assertEquals(List.of(new Ramp(1, 10, s(60), true)), plan.stages());
    }

    @Test
    void constant() {
        var plan = LoadPresets.expand("CONSTANT", Map.of("usersPerSec", 5, "durationSeconds", 30));
        assertEquals(List.of(new Hold(5, s(30), true)), plan.stages());
    }

    @Test
    void spike() {
        var plan = LoadPresets.expand("SPIKE",
                Map.of("base", 2, "spike", 20, "spikeDurationSeconds", 10, "totalDurationSeconds", 60));
        assertEquals(List.of(new Ramp(2, 20, s(10), true), new Hold(2, s(50), true)), plan.stages());
    }

    @Test
    void spikeRemainingFallsBackToOneSecond() {
        var plan = LoadPresets.expand("SPIKE",
                Map.of("base", 2, "spike", 20, "spikeDurationSeconds", 10, "totalDurationSeconds", 10));
        assertEquals(new Hold(2, s(1), true), plan.stages().get(1));
    }

    @Test
    void stair() {
        var plan = LoadPresets.expand("STAIR", Map.of("startUsersPerSec", 5, "incrementUsersPerSec", 5,
                "steps", 3, "stepDurationSeconds", 20));
        assertEquals(List.of(new Hold(5, s(20), true), new Hold(10, s(20), true), new Hold(15, s(20), true)),
                plan.stages());
    }

    @Test
    void soak() {
        var plan = LoadPresets.expand("SOAK",
                Map.of("usersPerSec", 8, "durationSeconds", 100, "rampDurationSeconds", 20));
        assertEquals(List.of(new Ramp(0, 8, s(20), true), new Hold(8, s(80), true)), plan.stages());
        assertEquals(s(100), plan.injectionWindow());
    }

    @Test
    void burst() {
        var plan = LoadPresets.expand("BURST", Map.of("users", 50, "bursts", 3, "intervalSeconds", 10));
        assertEquals(List.of(new Burst(50, true), new Pause(s(10), true), new Burst(50, true),
                new Pause(s(10), true), new Burst(50, true)), plan.stages());
    }

    @Test
    void rampUpDown() {
        var plan = LoadPresets.expand("RAMP_UP_DOWN", Map.of("from", 1, "to", 10, "rampUpDurationSeconds", 30,
                "holdDurationSeconds", 60, "rampDownDurationSeconds", 30));
        assertEquals(List.of(new Ramp(1, 10, s(30), true), new Hold(10, s(60), true),
                new Ramp(10, 1, s(30), true)), plan.stages());
    }

    @Test
    void custom() {
        var plan = LoadPresets.expand("CUSTOM", Map.of("points", List.of(
                Map.of("timeSeconds", 0, "usersPerSec", 1),
                Map.of("timeSeconds", 10, "usersPerSec", 5),
                Map.of("timeSeconds", 40, "usersPerSec", 2))));
        assertEquals(List.of(new Ramp(1, 5, s(10), true), new Ramp(5, 2, s(30), true)), plan.stages());
    }

    @Test
    void missingParameterNamed() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> LoadPresets.expand("RAMP", Map.of("from", 1, "to", 2)));
        assertTrue(ex.getMessage().contains("durationSeconds"));
    }

    @Test
    void unknownType() {
        assertThrows(IllegalArgumentException.class, () -> LoadPresets.expand("NOPE", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> LoadPresets.expand(null, Map.of()));
    }

    @Test
    void customNeedsTwoPoints() {
        assertThrows(IllegalArgumentException.class, () -> LoadPresets.expand("CUSTOM",
                Map.of("points", List.of(Map.of("timeSeconds", 0, "usersPerSec", 1)))));
    }
}
