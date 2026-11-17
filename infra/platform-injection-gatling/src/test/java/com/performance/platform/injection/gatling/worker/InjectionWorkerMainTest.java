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

class InjectionWorkerMainTest {

    @TempDir Path tmp;

    @Test
    void missingArgumentReturnsInvalid() {
        assertThat(InjectionWorkerMain.run(new String[0], c -> tmp)).isEqualTo(2);
    }

    @Test
    void nonexistentDirectoryReturnsInvalid() {
        assertThat(InjectionWorkerMain.run(new String[]{tmp.resolve("nope").toString()}, c -> tmp)).isEqualTo(2);
    }

    @Test
    void emptyDirectoryReturnsInvalid() {
        assertThat(InjectionWorkerMain.run(new String[]{tmp.toString()}, c -> tmp)).isEqualTo(2);
    }

    @Test
    void successWritesResult() throws Exception {
        write();
        int code = InjectionWorkerMain.run(new String[]{tmp.toString()}, c -> c.resultsDirectory().resolve("r1"));
        assertThat(code).isZero();
        var outcome = new RunDirectory(tmp).readResult();
        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.resultsDirectory()).isEqualTo(tmp.resolve("results/r1"));
    }

    @Test
    void simulationFailureReturnsOneAndWritesResult() throws Exception {
        write();
        int code = InjectionWorkerMain.run(new String[]{tmp.toString()}, c -> { throw new IllegalStateException("boom"); });
        assertThat(code).isEqualTo(1);
        assertThat(Files.exists(tmp.resolve(RunDirectory.RESULT_FILE))).isTrue();
        assertThat(new RunDirectory(tmp).readResult().logTail()).contains("boom");
    }

    private void write() throws Exception {
        new RunDirectory(tmp).writeInputs(
                new WorkerRunSpec(tmp, "sim", "com.X", Map.of(), Map.of(), Duration.ofSeconds(5)),
                new LoadModel(LoadModelType.CONSTANT, Map.of()));
    }
}
