package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationLogPerRequestTest {
    @TempDir
    Path directory;

    @Test
    void shouldAggregateByRequestNameAndSumToTotal() throws IOException {
        Files.writeString(directory.resolve("simulation.log"),
                "RUN\texample.Simulation\tsimulation\t10000\tfixture\t3.11.2\n"
                + "REQUEST\t\tget\t11000\t11010\tOK\t\n"
                + "REQUEST\t\tget\t12000\t12020\tKO\tx\n"
                + "REQUEST\t\tpost\t13000\t13050\tOK\t\n");

        var r = new SimulationLogResultParser().parse(directory, TaskId.of("t"));

        assertThat(r.perRequest()).containsOnlyKeys("get", "post");
        assertThat(r.perRequest().get("get").requests()).isEqualTo(2);
        assertThat(r.perRequest().get("get").failures()).isEqualTo(1);
        assertThat(r.perRequest().get("post").histogram().count()).isEqualTo(1);
        assertThat(r.perRequest().values().stream().mapToLong(s -> s.requests()).sum())
                .isEqualTo(r.totalRequests());
    }
}
