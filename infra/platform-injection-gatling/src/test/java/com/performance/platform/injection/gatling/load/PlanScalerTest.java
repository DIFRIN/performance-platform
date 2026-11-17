package com.performance.platform.injection.gatling.load;

import static org.junit.jupiter.api.Assertions.*;

import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.injection.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanScalerTest {

    private static WorkloadPlan plan(ArrivalModel m, LoadScope s, Stage... st) {
        return new WorkloadPlan(m, s, List.of(st));
    }

    @Test
    void perAgentUnchanged() {
        var p = plan(ArrivalModel.OPEN, LoadScope.PER_AGENT, new Hold(100, Duration.ofSeconds(5), true));
        assertSame(p, PlanScaler.scale(p, GrantShare.equal(1, 4)));
    }

    @Test
    void openRatesScaledByFraction() {
        var p = plan(ArrivalModel.OPEN, LoadScope.GLOBAL, new Hold(20_000, Duration.ofSeconds(5), true),
                new Ramp(0, 40_000, Duration.ofSeconds(5), true));
        var s = PlanScaler.scale(p, GrantShare.equal(2, 4));
        assertEquals(5_000, ((Hold) s.stages().get(0)).level(), 1e-9);
        assertEquals(10_000, ((Ramp) s.stages().get(1)).to(), 1e-9);
    }

    @Test
    void burstOfTenOverFour() {
        var p = plan(ArrivalModel.OPEN, LoadScope.GLOBAL, new Burst(10, true));
        long[] got = new long[4];
        for (int i = 0; i < 4; i++) got[i] = ((Burst) PlanScaler.scale(p, GrantShare.equal(i, 4)).stages().get(0)).users();
        assertArrayEquals(new long[] {3, 3, 2, 2}, got);
    }

    @Test
    void integerSharesSumToTotalForAllCounts() {
        for (int count = 1; count <= 16; count++) {
            for (long total = 0; total <= 100; total++) {
                long sum = 0;
                for (int i = 0; i < count; i++) sum += PlanScaler.integerShare(total, GrantShare.equal(i, count));
                assertEquals(total, sum, "count=" + count + " total=" + total);
            }
        }
    }

    @Test
    void closedPopulationUsesIntegerShare() {
        var p = plan(ArrivalModel.CLOSED, LoadScope.GLOBAL, new Hold(10, Duration.ofSeconds(5), true));
        double sum = 0;
        for (int i = 0; i < 4; i++) sum += ((Hold) PlanScaler.scale(p, GrantShare.equal(i, 4)).stages().get(0)).level();
        assertEquals(10, sum, 1e-9);
    }

    @Test
    void zeroShareIsIdle() {
        var p = plan(ArrivalModel.OPEN, LoadScope.GLOBAL, new Burst(1, true));
        var s = PlanScaler.scale(p, GrantShare.equal(3, 4));
        assertTrue(PlanScaler.isIdle(s));
        assertFalse(PlanScaler.isIdle(PlanScaler.scale(p, GrantShare.equal(0, 4))));
    }
}
