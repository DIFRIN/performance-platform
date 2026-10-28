package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationLogResultParserTest {
    @TempDir
    Path directory;

    private final DefaultGatlingResultParser parser = new DefaultGatlingResultParser();
    private final TaskId taskId = TaskId.of("log-parser");
    private static final String RUN = "RUN\texample.Simulation\tsimulation\t1000\tfixture\t3.11.2\n";

    @Test
    void shouldMeasureMixedRequestsWithoutCountingGroupsOrErrorsTwice() throws IOException {
        Files.writeString(directory.resolve("simulation.log"), RUN + """
                USER\tusers\tSTART\t1000
                REQUEST\tgroup\tfirst\t1100\t1110\tOK\t \t
                REQUEST\tgroup\tsecond\t1200\t1220\tKO\tcheck failed
                ERROR\tcheck failed\t1
                GROUP\tgroup\t1000\t2000\t30\tKO
                REQUEST\t\tthird\t1300\t1330\tOK\t
                REQUEST\t\tfourth\t1400\t1440\tOK\t
                USER\tusers\tEND\t2000
                """);

        var result = parser.parse(directory, taskId);

        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.simulationClass()).isEqualTo("example.Simulation");
        assertThat(result.duration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(result.totalRequests()).isEqualTo(4);
        assertThat(result.successfulRequests()).isEqualTo(3);
        assertThat(result.failedRequests()).isEqualTo(1);
        assertThat(result.errorRate()).isEqualTo(25.0);
        assertThat(result.throughput()).isEqualTo(4.0);
        assertThat(result.minMs()).isEqualTo(10);
        assertThat(result.maxMs()).isEqualTo(40);
        assertThat(result.meanMs()).isEqualTo(25.0);
        assertThat(result.p50Ms()).isEqualTo(20);
        assertThat(result.p75Ms()).isEqualTo(30);
        assertThat(result.p90Ms()).isEqualTo(40);
        assertThat(result.p95Ms()).isEqualTo(40);
        assertThat(result.p99Ms()).isEqualTo(40);
        assertThat(result.rawStats()).containsEntry("numberOfRequests",
                Map.of("total", 4L, "ok", 3L, "ko", 1L));
        assertThat(result.gatlingReportDirectory()).isEqualTo(directory.toAbsolutePath().normalize());
    }

    @Test
    void shouldMeasureRequestSpanWhenUserRecordsAreAbsent() throws IOException {
        Files.writeString(directory.resolve("simulation.log"), RUN
                + "REQUEST\t\tfirst\t1000\t1025\tOK\t\n"
                + "REQUEST\t\tsecond\t1075\t1100\tKO\tfailure\n");

        var result = parser.parse(directory, taskId);

        assertThat(result.duration()).isEqualTo(Duration.ofMillis(100));
        assertThat(result.throughput()).isEqualTo(20.0);
        assertThat(result.meanMs()).isEqualTo(25.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "REQUEST\t\tbad\t1000\t1020\tUNKNOWN\t\n",
            "REQUEST\t\tbad\t1000\n",
            "REQUEST\t\tbad\tnot-a-number\t1020\tOK\t\n",
            "REQUEST\t\tbad\t1020\t1000\tKO\tfailure\n",
            "REQUEST\t\tbad\t-1\t20\tOK\t\n",
            "USER\tusers\tBAD\t1000\n"
    })
    void shouldRejectMalformedMeasuredRecords(String record) throws IOException {
        Files.writeString(directory.resolve("simulation.log"), RUN + record);
        assertThatThrownBy(() -> parser.parse(directory, taskId))
                .isInstanceOf(ResultParsingException.class)
                .hasMessageContaining("simulation.log").hasMessageContaining("line 2");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "USER\tusers\tSTART\t1000\nUSER\tusers\tEND\t2000\n",
            "REQUEST\t\tinstant\t1000\t1000\tOK\t\n"
    })
    void shouldRejectMissingMeasurementsInsteadOfFabricatingMetrics(String records) throws IOException {
        Files.writeString(directory.resolve("simulation.log"), RUN + records);
        assertThatThrownBy(() -> parser.parse(directory, taskId))
                .isInstanceOf(ResultParsingException.class).hasMessageContaining("Incomplete simulation.log");
    }

    @Test
    void shouldRequireRunMetadata() throws IOException {
        Files.writeString(directory.resolve("simulation.log"), "REQUEST\t\trequest\t1000\t1020\tOK\t\n");
        assertThatThrownBy(() -> parser.parse(directory, taskId))
                .isInstanceOf(ResultParsingException.class).hasMessageContaining("Incomplete simulation.log");
    }

    @Test
    void shouldNotHideBrokenExistingJsonBehindLogFallback() throws IOException {
        Files.writeString(directory.resolve("simulation.log"), RUN
                + "REQUEST\t\trequest\t1000\t1020\tOK\t\n");
        Files.writeString(directory.resolve("stats.json"), "{bad json");
        assertThatThrownBy(() -> parser.parse(directory, taskId))
                .isInstanceOf(ResultParsingException.class).hasMessageContaining("Failed to read");
    }
}
