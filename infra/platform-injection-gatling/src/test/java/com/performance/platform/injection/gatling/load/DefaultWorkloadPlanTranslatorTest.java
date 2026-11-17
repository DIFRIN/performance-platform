package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.Pause;
import com.performance.platform.domain.injection.Ramp;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.domain.injection.WorkloadPlan;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultWorkloadPlanTranslatorTest {

    private final DefaultWorkloadPlanTranslator translator = new DefaultWorkloadPlanTranslator();

    private static WorkloadPlan open(Stage... stages) {
        return new WorkloadPlan(ArrivalModel.OPEN, LoadScope.PER_AGENT, List.of(stages));
    }

    @Test
    void translatesEachStageKindOneToOne() {
        var steps = translator.toOpenSteps(open(
                new Ramp(1, 10, Duration.ofSeconds(5), false),
                new Hold(10, Duration.ofSeconds(10), true),
                new Burst(20, true),
                new Pause(Duration.ofSeconds(2), true)));
        assertEquals(4, steps.size());
    }

    @Test
    void rampWithEqualBoundsBecomesConstantStep() {
        var steps = translator.toOpenSteps(open(new Ramp(5, 5, Duration.ofSeconds(5), true)));
        assertEquals(1, steps.size());
        assertEquals(translator.toOpenSteps(open(new Hold(5, Duration.ofSeconds(5), true))).get(0).getClass(),
                steps.get(0).getClass());
    }

    @Test
    void closedModelRejectedByOpenTranslation() {
        var plan = new WorkloadPlan(ArrivalModel.CLOSED, LoadScope.PER_AGENT,
                List.of(new Hold(5, Duration.ofSeconds(5), true)));
        assertThrows(IllegalArgumentException.class, () -> translator.toOpenSteps(plan));
    }

    @Test
    void closedStepsNotYetSupported() {
        var plan = new WorkloadPlan(ArrivalModel.CLOSED, LoadScope.PER_AGENT,
                List.of(new Hold(5, Duration.ofSeconds(5), true)));
        assertThrows(UnsupportedOperationException.class, () -> translator.toClosedSteps(plan));
    }
}
