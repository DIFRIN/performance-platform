package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.Pause;
import com.performance.platform.domain.injection.Ramp;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.plugin.InjectionCapabilities;

import java.util.Objects;

/** Admission d'un plan : rejette, sans approximer, ce que l'adaptateur ne sait pas executer (ADR-065). */
public final class PlanAdmission {

    private PlanAdmission() {
    }

    /** @throws PlanRejectedException (message nommant la fonctionnalite) si le plan n'est pas executable */
    public static void check(WorkloadPlan plan, InjectionCapabilities caps) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(caps, "caps must not be null");
        if (!caps.models().contains(plan.model())) {
            throw new PlanRejectedException("model:" + plan.model(),
                    "Unsupported feature 'model: " + plan.model() + "'");
        }
        if (plan.scope() == LoadScope.GLOBAL && !caps.supportsGlobalScope()) {
            throw new PlanRejectedException("scope:global", "Unsupported feature 'scope: global'");
        }
        for (Stage stage : plan.stages()) {
            String kind = kindOf(stage);
            if (!caps.stageKinds().contains(kind)) {
                throw new PlanRejectedException("stage:" + kind, "Unsupported feature 'stage: " + kind + "'");
            }
        }
    }

    private static String kindOf(Stage stage) {
        return switch (stage) {
            case Ramp r -> "ramp";
            case Hold h -> "hold";
            case Burst b -> "burst";
            case Pause p -> "pause";
        };
    }
}
