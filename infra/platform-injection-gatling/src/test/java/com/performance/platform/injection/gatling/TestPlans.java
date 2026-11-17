package com.performance.platform.injection.gatling;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.domain.injection.WorkloadPlanCodec;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Plans de test partages. */
public final class TestPlans {

    private TestPlans() {
    }

    public static WorkloadPlan hold(double rate, int seconds) {
        return new WorkloadPlan(ArrivalModel.OPEN, LoadScope.PER_AGENT,
                List.of(new Hold(rate, Duration.ofSeconds(seconds), true)));
    }

    /** Forme canonique d'un palier de 10 users/s pendant 60 s. */
    public static Map<String, Object> holdMap() {
        return WorkloadPlanCodec.toMap(hold(10, 60));
    }
}
