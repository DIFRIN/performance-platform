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
import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

/**
 * Ramp → rampUsersPerSec (palier constant si from == to), Hold → constantUsersPerSec,
 * Burst → atOnceUsers, Pause → nothingFor.
 * Closed model: Ramp → rampConcurrentUsers, Hold → constantConcurrentUsers, Pause → 0 users.
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
        Objects.requireNonNull(plan, "plan must not be null");
        if (plan.model() != ArrivalModel.CLOSED) {
            throw new IllegalArgumentException("toClosedSteps requires model CLOSED, got " + plan.model());
        }
        List<ClosedInjectionStep> steps = new ArrayList<>();
        for (Stage stage : plan.stages()) {
            switch (stage) {
                case Ramp r -> {
                    int from = toUsers(r.from());
                    int to = toUsers(r.to());
                    steps.add(from == to
                            ? constantConcurrentUsers(to).during(r.duration())
                            : rampConcurrentUsers(from).to(to).during(r.duration()));
                }
                case Hold h -> steps.add(constantConcurrentUsers(toUsers(h.level())).during(h.duration()));
                case Pause p -> steps.add(constantConcurrentUsers(0).during(p.duration()));
                case Burst b -> throw new IllegalArgumentException("Burst is not supported by the CLOSED model");
            }
        }
        return List.copyOf(steps);
    }

    private static int toUsers(double level) {
        return Math.toIntExact(Math.round(level));
    }
}
