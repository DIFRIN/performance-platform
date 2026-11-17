package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.ArrivalModel;
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
        check(plan, caps, InjectionLimits.none());
    }

    /**
     * Idem, avec garde-fou de debit : le pic du plan (part de l'agent) ne doit pas depasser les limites.
     *
     * @throws PlanRejectedException si le pic depasse strictement une limite
     */
    public static void check(WorkloadPlan plan, InjectionCapabilities caps, InjectionLimits limits) {
        Objects.requireNonNull(limits, "limits must not be null");
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
        checkLimits(plan, limits);
    }

    private static void checkLimits(WorkloadPlan plan, InjectionLimits limits) {
        boolean open = plan.model() == ArrivalModel.OPEN;
        double peak = 0;
        for (Stage stage : plan.stages()) {
            switch (stage) {
                case Ramp r -> peak = Math.max(peak, Math.max(r.from(), r.to()));
                case Hold h -> peak = Math.max(peak, h.level());
                case Burst b -> {
                    if (limits.maxConcurrentUsers() != null && b.users() > limits.maxConcurrentUsers()) {
                        throw exceeded("max-concurrent-users", b.users(), limits.maxConcurrentUsers());
                    }
                }
                case Pause p -> { }
            }
        }
        if (open && limits.maxArrivalRate() != null && peak > limits.maxArrivalRate()) {
            throw exceeded("max-arrival-rate", peak, limits.maxArrivalRate());
        }
        if (!open && limits.maxConcurrentUsers() != null && peak > limits.maxConcurrentUsers()) {
            throw exceeded("max-concurrent-users", peak, limits.maxConcurrentUsers());
        }
    }

    private static PlanRejectedException exceeded(String limit, Number requested, Number max) {
        String agent = Objects.requireNonNullElse(System.getenv("AGENT_ID"), "local");
        return new PlanRejectedException("limit:" + limit, "Requested " + requested + " exceeds injection."
                + limit + "=" + max + " on agent " + agent);
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
