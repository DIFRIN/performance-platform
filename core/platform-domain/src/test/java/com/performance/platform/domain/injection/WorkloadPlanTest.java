package com.performance.platform.domain.injection;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkloadPlanTest {

    private static final Duration D = Duration.ofSeconds(10);

    @Test
    void injectionWindowSumsDurations() {
        var p = new WorkloadPlan(ArrivalModel.OPEN, LoadScope.PER_AGENT,
                List.of(new Ramp(0, 10, D, false), new Burst(5, true), new Pause(D, true)));
        assertEquals(Duration.ofSeconds(20), p.injectionWindow());
    }

    @Test
    void stagesAreDefensivelyCopied() {
        var l = new ArrayList<Stage>(List.of(new Hold(1, D, true)));
        var p = new WorkloadPlan(ArrivalModel.OPEN, LoadScope.GLOBAL, l);
        l.add(new Pause(D, true));
        assertEquals(1, p.stages().size());
        assertThrows(UnsupportedOperationException.class, () -> p.stages().add(new Pause(D, true)));
    }

    @Test
    void rejectsEmptyOrNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkloadPlan(ArrivalModel.OPEN, LoadScope.PER_AGENT, List.of()));
        assertThrows(NullPointerException.class,
                () -> new WorkloadPlan(null, LoadScope.PER_AGENT, List.of(new Burst(1, true))));
    }

    @Test
    void rejectsInvalidStages() {
        assertThrows(IllegalArgumentException.class, () -> new Ramp(0, 1, Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new Ramp(-1, 1, D, true));
        assertThrows(IllegalArgumentException.class, () -> new Hold(1, D.negated(), true));
        assertThrows(IllegalArgumentException.class, () -> new Hold(-0.1, D, true));
        assertThrows(IllegalArgumentException.class, () -> new Pause(Duration.ZERO, true));
        assertThrows(IllegalArgumentException.class, () -> new Burst(0, true));
        assertEquals(Duration.ZERO, new Burst(1, true).duration());
    }
}
