package com.performance.platform.injection.gatling.runner;

import io.gatling.javaapi.core.ClosedInjectionStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationInjectionHolderClosedTest {

    @Test
    void shouldStoreReadAndRemoveClosedInjection() {
        String id = "closed-holder-" + System.nanoTime();
        assertThat(SimulationInjectionHolder.getClosed(id)).isNull();
        SimulationInjectionHolder.setClosed(id,
                List.of(constantConcurrentUsers(3).during(Duration.ofSeconds(2))), Duration.ofSeconds(2));
        var closed = SimulationInjectionHolder.getClosed(id);
        assertThat(closed.steps()).hasSize(1);
        assertThat(closed.totalDuration()).isEqualTo(Duration.ofSeconds(2));
        SimulationInjectionHolder.remove(id);
        assertThat(SimulationInjectionHolder.getClosed(id)).isNull();
    }

    @Test
    void closedShouldBeImmutable() {
        List<ClosedInjectionStep> steps = new ArrayList<>(
                List.of(constantConcurrentUsers(1).during(Duration.ofSeconds(1))));
        var closed = new SimulationInjectionHolder.Closed(steps, Duration.ofSeconds(1));
        steps.clear();
        assertThat(closed.steps()).hasSize(1);
        assertThatThrownBy(() -> closed.steps().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
