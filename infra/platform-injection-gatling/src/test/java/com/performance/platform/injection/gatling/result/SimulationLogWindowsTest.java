package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationLogWindowsTest {
    @TempDir
    Path directory;

    private static final String RUN = "RUN\texample.Simulation\tsimulation\t10000\tfixture\t3.11.2\n";

    private void log(String body) throws IOException {
        Files.writeString(directory.resolve("simulation.log"), RUN + body);
    }

    @Test
    void shouldExcludeWarmupFromPercentilesAndHistogram() throws IOException {
        log("USER\tu\tSTART\t10000\n"
                + "REQUEST\t\ta\t11000\t11900\tOK\t\n"   // warm-up (slow)
                + "REQUEST\t\tb\t16000\t16010\tOK\t\n"
                + "REQUEST\t\tc\t17000\t17020\tKO\tx\n"
                + "USER\tu\tEND\t18000\n");
        var plan = new WorkloadPlan(ArrivalModel.OPEN, LoadScope.GLOBAL, List.of(
                new Hold(1, Duration.ofSeconds(5), false), new Hold(1, Duration.ofSeconds(5), true)));

        var r = new SimulationLogResultParser().parse(directory, TaskId.of("t"), plan, Duration.ofSeconds(5));

        assertThat(r.totalRequests()).isEqualTo(3);
        assertThat(r.maxMs()).isEqualTo(20);
        assertThat(r.p99Ms()).isEqualTo(20);
        assertThat(r.histogram().count()).isEqualTo(2);
        assertThat(r.complete()).isTrue();
        assertThat(r.windows()).hasSize(2);
        var w0 = r.windows().get(0);
        var w1 = r.windows().get(1);
        assertThat(w0.measured()).isFalse();
        assertThat(w0.requests()).isEqualTo(1);
        assertThat(w0.startedUsers()).isEqualTo(1);
        assertThat(w0.plannedArrivals()).isEqualTo(5);
        assertThat(w1.measured()).isTrue();
        assertThat(w1.requests()).isEqualTo(2);
        assertThat(w1.failures()).isEqualTo(1);
        assertThat(w0.start().toEpochMilli() % 5000).isZero();
        assertThat(w0.plannedArrivals() + w1.plannedArrivals()).isEqualTo(10);
    }

    @Test
    void shouldAlignWindowsOnWallClockAndKnowNoArrivalsWithoutPlan() throws IOException {
        log("REQUEST\t\ta\t12000\t12010\tOK\t\nREQUEST\t\tb\t13000\t13010\tOK\t\n"
                + "REQUEST\t\tc\t21000\t21010\tOK\t\n");

        var r = new SimulationLogResultParser().parse(directory, TaskId.of("t"));

        assertThat(r.windows()).extracting(LoadWindow::requests).containsExactly(2L, 0L, 1L);
        assertThat(r.windows()).allMatch(LoadWindow::measured);
        assertThat(r.windows()).allMatch(w -> w.plannedArrivals() == 0);
        assertThat(r.windows().get(0).start().toEpochMilli()).isEqualTo(10000);
    }

    @Test
    void shouldHaveZeroPlannedArrivalsForClosedModel() throws IOException {
        log("REQUEST\t\ta\t12000\t12010\tOK\t\n");
        var plan = new WorkloadPlan(ArrivalModel.CLOSED, LoadScope.GLOBAL,
                List.of(new Hold(10, Duration.ofSeconds(10), true)));

        var r = new SimulationLogResultParser().parse(directory, TaskId.of("t"), plan, Duration.ofSeconds(5));

        assertThat(r.windows()).allMatch(w -> w.plannedArrivals() == 0);
    }
}
