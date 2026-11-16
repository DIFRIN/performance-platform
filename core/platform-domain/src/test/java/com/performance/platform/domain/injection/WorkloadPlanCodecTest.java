package com.performance.platform.domain.injection;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkloadPlanCodecTest {

    private static Map<String, Object> sample() {
        return Map.of("model", "open", "stages", List.of(
                Map.of("ramp", Map.of("from", 0, "to", 200, "duration", "30s", "measured", false)),
                Map.of("hold", Map.of("level", 200, "duration", "5m")),
                Map.of("burst", Map.of("users", 500)),
                Map.of("pause", Map.of("duration", "PT10S"))));
    }

    @Test
    void parsesShortAndIsoDurationsWithDefaults() {
        WorkloadPlan p = WorkloadPlanCodec.fromMap(sample());
        assertEquals(LoadScope.PER_AGENT, p.scope());
        assertEquals(new Ramp(0, 200, Duration.ofSeconds(30), false), p.stages().get(0));
        assertEquals(new Hold(200, Duration.ofMinutes(5), true), p.stages().get(1));
        assertEquals(new Pause(Duration.ofSeconds(10), true), p.stages().get(3));
        assertEquals(Duration.ofSeconds(340), p.injectionWindow());
        assertEquals(Duration.ofHours(1), WorkloadPlanCodec.fromMap(Map.of("model", "closed", "stages",
                List.of(Map.of("pause", Map.of("duration", "1h"))))).injectionWindow());
    }

    @Test
    void roundTripIsIdempotent() {
        Map<String, Object> once = WorkloadPlanCodec.toMap(WorkloadPlanCodec.fromMap(sample()));
        Map<String, Object> twice = WorkloadPlanCodec.toMap(WorkloadPlanCodec.fromMap(once));
        assertEquals(once, twice);
        assertEquals("[model, scope, stages]", once.keySet().toString());
        assertEquals("per-agent", once.get("scope"));
        assertEquals(WorkloadPlanCodec.fromMap(sample()), WorkloadPlanCodec.fromMap(once));
    }

    @Test
    void hashStableAndSensitive() {
        WorkloadPlan a = WorkloadPlanCodec.fromMap(sample());
        WorkloadPlan b = WorkloadPlanCodec.fromMap(sample());
        assertEquals(WorkloadPlanCodec.hash(a), WorkloadPlanCodec.hash(b));
        assertTrue(WorkloadPlanCodec.hash(a).matches("[0-9a-f]{64}"));
        WorkloadPlan c = new WorkloadPlan(a.model(), LoadScope.GLOBAL, a.stages());
        assertNotEquals(WorkloadPlanCodec.hash(a), WorkloadPlanCodec.hash(c));
        WorkloadPlan d = new WorkloadPlan(a.model(), a.scope(),
                List.of(new Burst(501, true)));
        assertNotEquals(WorkloadPlanCodec.hash(a), WorkloadPlanCodec.hash(d));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> WorkloadPlanCodec.fromMap(null));
        assertThrows(IllegalArgumentException.class, () -> WorkloadPlanCodec.fromMap(Map.of("stages", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadPlanCodec.fromMap(Map.of("model", "open", "stages", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadPlanCodec.fromMap(Map.of("model", "open", "stages", List.of(Map.of("jump", Map.of())))));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadPlanCodec.fromMap(Map.of("model", "weird", "stages", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadPlanCodec.fromMap(Map.of("model", "open", "stages",
                        List.of(Map.of("pause", Map.of("duration", "abc"))))));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadPlanCodec.fromMap(Map.of("model", "open", "stages",
                        List.of(Map.of("hold", Map.of("level", -1, "duration", "5s"))))));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadPlanCodec.fromMap(Map.of("model", "open", "stages",
                        List.of(Map.of("burst", Map.of("users", 1.5))))));
    }
}
