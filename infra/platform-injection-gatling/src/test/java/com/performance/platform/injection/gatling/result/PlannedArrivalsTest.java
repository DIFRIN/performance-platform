package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.injection.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedArrivalsTest {

    private static WorkloadPlan open(Stage... stages) {
        return new WorkloadPlan(ArrivalModel.OPEN, LoadScope.GLOBAL, List.of(stages));
    }

    @Test
    void shouldSumWindowsToPlanTotal() {
        var plan = open(new Ramp(0, 10, Duration.ofSeconds(10), false),
                new Hold(7, Duration.ofSeconds(13), true), new Burst(5, true));
        var p = new PlannedArrivals(plan, 0);
        long sum = 0;
        for (long s = -5000; s < 40000; s += 5000) sum += p.inWindow(s, 5000);
        assertThat(p.total()).isEqualTo(50 + 91 + 5);
        assertThat(Math.abs(sum - p.total())).isLessThanOrEqualTo(1);
        assertThat(p.arrivalKnown()).isTrue();
    }

    @Test
    void shouldIntegrateRampAndHoldPerWindow() {
        var p = new PlannedArrivals(open(new Ramp(0, 10, Duration.ofSeconds(10), true),
                new Hold(10, Duration.ofSeconds(5), true)), 0);
        assertThat(p.inWindow(0, 5000)).isEqualTo(13); // 12.5 -> 13 cumulative rounding
        assertThat(p.inWindow(5000, 5000)).isEqualTo(37);
        assertThat(p.inWindow(10000, 5000)).isEqualTo(50);
    }

    @Test
    void shouldReportNothingForClosedModel() {
        var p = new PlannedArrivals(new WorkloadPlan(ArrivalModel.CLOSED, LoadScope.GLOBAL,
                List.of(new Hold(20, Duration.ofSeconds(10), true))), 0);
        assertThat(p.arrivalKnown()).isFalse();
        assertThat(p.inWindow(0, 5000)).isZero();
        assertThat(p.total()).isZero();
    }
}
