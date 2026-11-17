package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.Pause;
import com.performance.platform.domain.injection.Ramp;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.domain.injection.WorkloadPlan;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

/**
 * Ramp → rampUsersPerSec (palier constant si from == to), Hold → constantUsersPerSec,
 * Burst → atOnceUsers, Pause → nothingFor.
 */
@Component
public class DefaultWorkloadPlanTranslator implements WorkloadPlanTranslator {

    @Override
    public List<OpenInjectionStep> toOpenSteps(WorkloadPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        if (plan.model() != ArrivalModel.OPEN) {
            throw new IllegalArgumentException("toOpenSteps requires model OPEN, got " + plan.model());
        }
        List<OpenInjectionStep> steps = new ArrayList<>();
        for (Stage stage : plan.stages()) {
            steps.add(switch (stage) {
                case Ramp r when Double.compare(r.from(), r.to()) == 0 ->
                        constantUsersPerSec(r.from()).during(r.duration());
                case Ramp r -> rampUsersPerSec(r.from()).to(r.to()).during(r.duration());
                case Hold h -> constantUsersPerSec(h.level()).during(h.duration());
                case Burst b -> atOnceUsers(Math.toIntExact(b.users()));
                case Pause p -> nothingFor(p.duration());
            });
        }
        return List.copyOf(steps);
    }

    @Override
    public List<ClosedInjectionStep> toClosedSteps(WorkloadPlan plan) {
        throw new UnsupportedOperationException("CLOSED model translation not implemented yet");
    }
}
