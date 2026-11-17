package com.performance.platform.injection.gatling.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.Pause;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.plugin.InjectionCapabilities;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanAdmissionTest {

    private static InjectionCapabilities caps(Set<String> kinds, boolean global) {
        return new InjectionCapabilities(Set.of(ArrivalModel.OPEN), kinds, Set.of("http"),
                global, true, false, false);
    }

    private static WorkloadPlan plan(ArrivalModel m, LoadScope s) {
        return new WorkloadPlan(m, s, List.of(new Hold(5, Duration.ofSeconds(10), true),
                new Pause(Duration.ofSeconds(1), false)));
    }

    @Test
    void acceptsSupportedPlan() {
        PlanAdmission.check(plan(ArrivalModel.OPEN, LoadScope.PER_AGENT), caps(Set.of("hold", "pause"), false));
    }

    @Test
    void rejectsGlobalScope() {
        assertThatThrownBy(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, LoadScope.GLOBAL),
                caps(Set.of("hold", "pause"), false)))
                .isInstanceOf(PlanRejectedException.class).hasMessageContaining("scope: global");
    }

    @Test
    void rejectsUnsupportedModel() {
        assertThatThrownBy(() -> PlanAdmission.check(plan(ArrivalModel.CLOSED, LoadScope.PER_AGENT),
                caps(Set.of("hold", "pause"), false)))
                .isInstanceOf(PlanRejectedException.class).hasMessageContaining("CLOSED");
    }

    @Test
    void rejectsUnsupportedStageKind() {
        assertThatThrownBy(() -> PlanAdmission.check(plan(ArrivalModel.OPEN, LoadScope.PER_AGENT),
                caps(Set.of("hold"), false)))
                .isInstanceOf(PlanRejectedException.class).hasMessageContaining("pause");
    }
}
