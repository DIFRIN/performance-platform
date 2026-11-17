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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClosedModelTranslatorTest {

    private final DefaultWorkloadPlanTranslator translator = new DefaultWorkloadPlanTranslator();

    private static WorkloadPlan closed(Stage... stages) {
        return new WorkloadPlan(ArrivalModel.CLOSED, LoadScope.PER_AGENT, List.of(stages));
    }

    @Test
    void translatesRampHoldAndPause() {
        var steps = translator.toClosedSteps(closed(
                new Ramp(1, 10, Duration.ofSeconds(5), false),
                new Hold(10.4, Duration.ofSeconds(10), true),
                new Pause(Duration.ofSeconds(2), true)));
        assertEquals(3, steps.size());
        assertNotEquals(steps.get(0).asScala().getClass(), steps.get(1).asScala().getClass());
        assertEquals(steps.get(1).asScala().getClass(), steps.get(2).asScala().getClass());
    }

    @Test
    void rampWithEqualRoundedBoundsBecomesConstant() {
        var steps = translator.toClosedSteps(closed(
                new Ramp(5, 5.2, Duration.ofSeconds(5), true),
                new Hold(5, Duration.ofSeconds(5), true)));
        assertEquals(steps.get(1).asScala().getClass(), steps.get(0).asScala().getClass());
    }

    @Test
    void burstRejected() {
        assertThrows(IllegalArgumentException.class, () -> translator.toClosedSteps(closed(new Burst(5, true))));
    }

    @Test
    void openModelRejected() {
        var plan = new WorkloadPlan(ArrivalModel.OPEN, LoadScope.PER_AGENT,
                List.of(new Hold(5, Duration.ofSeconds(5), true)));
        assertThrows(IllegalArgumentException.class, () -> translator.toClosedSteps(plan));
    }
}
