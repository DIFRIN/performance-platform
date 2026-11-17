package com.performance.platform.injection.gatling.worker;

import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunDirectoryTest {

    @TempDir Path tmp;

    @Test
    void inputsRoundTripWithoutSecrets() throws Exception {
        var dir = new RunDirectory(tmp);
        var spec = new WorkerRunSpec(tmp, "sim-1", "com.X", Map.of("a", "b"),
                Map.of("SECRET", "s3cr3t"), Duration.ofSeconds(30));
        var model = new LoadModel(LoadModelType.CONSTANT, Map.of("usersPerSec", 5, "duration", "PT10S"));
        dir.writeInputs(spec, model);

        var read = dir.readSpec();
        assertThat(read.simulationId()).isEqualTo("sim-1");
        assertThat(read.simulationClass()).isEqualTo("com.X");
        assertThat(read.systemProperties()).containsEntry("a", "b");
        assertThat(read.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(read.environment()).isEmpty();
        assertThat(dir.readLoadModel()).isEqualTo(model);
        assertThat(Files.readString(tmp.resolve(RunDirectory.SPEC_FILE))).doesNotContain("s3cr3t");
    }

    @Test
    void resultRoundTrip() throws Exception {
        var dir = new RunDirectory(tmp);
        var out = new WorkerOutcome(0, true, false, tmp.resolve("results/x"), dir.resultFile(), "");
        dir.writeResult(out);
        assertThat(dir.readResult()).isEqualTo(out);
    }
}
