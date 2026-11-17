package com.performance.platform.injection.gatling.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Worker cancellation")
class WorkerCancellationTest {

    public static final class LongWorker {
        public static void main(String[] args) throws Exception {
            Thread.sleep(120_000);
        }
    }

    @TempDir Path dir;

    private ProcessInjectionWorkerLauncher launcher() {
        return new ProcessInjectionWorkerLauncher(InjectionWorkerProperties.defaults(), null,
                ProcessHandle.current().info().command().orElse("java"), System.getProperty("java.class.path"));
    }

    private WorkerRunSpec spec(Duration timeout) {
        return new WorkerRunSpec(dir, "sim", "Sim", Map.of(), Map.of(), timeout);
    }

    @Test
    @DisplayName("stop() ends a long-running worker within the grace period")
    void stopWithinGrace() throws Exception {
        var handle = launcher().start(spec(Duration.ZERO), LongWorker.class.getName());
        long t0 = System.nanoTime();
        handle.stop(Duration.ofSeconds(3));
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(5));
        assertThat(handle.alive()).isFalse();
        var outcome = handle.await();
        assertThat(outcome.stoppedByPlatform()).isTrue();
        assertThat(outcome.completed()).isFalse();
    }

    @Test
    @DisplayName("stopAllWorkers() (@PreDestroy) stops every live worker")
    void preDestroy() throws Exception {
        var l = launcher();
        var handle = l.start(spec(Duration.ZERO), LongWorker.class.getName());
        assertThat(handle.alive()).isTrue();
        l.stopAllWorkers();
        assertThat(handle.alive()).isFalse();
        assertThat(handle.await().stoppedByPlatform()).isTrue();
    }
}
