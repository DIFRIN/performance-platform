package com.performance.platform.injection.gatling.load;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.Ramp;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.plugin.InjectionCapabilities;
import com.performance.platform.domain.injection.WorkloadPlan;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InjectionLimitsTest {

    private static final InjectionCapabilities CAPS = new InjectionCapabilities(
            Set.of(ArrivalModel.OPEN, ArrivalModel.CLOSED), Set.of("ramp", "hold", "burst", "pause"),
            Set.of("http"), false, true, false, false);
    private static final Duration D = Duration.ofSeconds(10);

    private static WorkloadPlan plan(ArrivalModel m, Stage... stages) {
        return new WorkloadPlan(m, LoadScope.PER_AGENT, List.of(stages));
    }

    @Test
    void openAboveRateRejected() {
        var limits = new InjectionLimits(100.0, null);
        assertThatThrownBy(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, new Ramp(10, 101, D, true)), CAPS, limits))
                .isInstanceOf(PlanRejectedException.class)
                .hasMessageContaining("101").hasMessageContaining("100").hasMessageContaining("agent");
    }

    @Test
    void openEqualToRateAccepted() {
        var limits = new InjectionLimits(100.0, null);
        assertThatCode(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, new Hold(100, D, true)), CAPS, limits))
                .doesNotThrowAnyException();
    }

    @Test
    void closedAboveUsersRejectedAndEqualAccepted() {
        var limits = new InjectionLimits(null, 50L);
        assertThatThrownBy(() -> PlanAdmission.check(plan(ArrivalModel.CLOSED, new Hold(51, D, true)), CAPS, limits))
                .isInstanceOf(PlanRejectedException.class);
        assertThatCode(() -> PlanAdmission.check(plan(ArrivalModel.CLOSED, new Hold(50, D, true)), CAPS, limits))
                .doesNotThrowAnyException();
    }

    @Test
    void burstComparedToConcurrentUsers() {
        var limits = new InjectionLimits(null, 50L);
        assertThatThrownBy(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, new Burst(51, true)), CAPS, limits))
                .isInstanceOf(PlanRejectedException.class).hasMessageContaining("max-concurrent-users");
        assertThatCode(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, new Burst(50, true)), CAPS, limits))
                .doesNotThrowAnyException();
    }

    @Test
    void undefinedLimitsMeanNoLimit() {
        assertThatCode(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, new Hold(1e9, D, true)), CAPS,
                InjectionLimits.none())).doesNotThrowAnyException();
    }
}
