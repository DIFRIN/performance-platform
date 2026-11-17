package com.performance.platform.injection.gatling.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessInjectionWorkerLauncher")
class ProcessInjectionWorkerLauncherTest {

    /** Worker factice : echo des args/-D/env puis sortie selon le run directory. */
    public static final class FakeWorker {
        public static void main(String[] args) throws Exception {
            System.out.println("prop=" + System.getProperty("my.prop"));
            System.out.println("secret=" + System.getenv("MY_SECRET"));
            if (Files.exists(Path.of(args[0], "fail"))) {
                for (int i = 1; i <= 80; i++) System.err.println("line-" + i);
                System.exit(3);
            }
            if (Files.exists(Path.of(args[0], "hang"))) Thread.sleep(60_000);
        }
    }

    @TempDir Path dir;

    private ProcessInjectionWorkerLauncher launcher() {
        return new ProcessInjectionWorkerLauncher(InjectionWorkerProperties.defaults(), null,
                ProcessHandle.current().info().command().orElse("java"), System.getProperty("java.class.path"));
    }

    private WorkerRunSpec spec() {
        return new WorkerRunSpec(dir, "sim", "Sim", Map.of("my.prop", "v1"), Map.of("MY_SECRET", "s3"),
                Duration.ZERO);
    }

    @Test
    @DisplayName("exit 0 -> completed, system properties and env reach the child only")
    void exitZero() throws Exception {
        var outcome = launcher().start(spec(), FakeWorker.class.getName()).await();
        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.logTail()).contains("prop=v1", "secret=s3");
        assertThat(System.getProperty("my.prop")).isNull();
    }

    @Test
    @DisplayName("exit != 0 -> not completed, log tail limited to the last 50 lines")
    void exitNonZero() throws Exception {
        Files.createFile(dir.resolve("fail"));
        var outcome = launcher().start(spec(), FakeWorker.class.getName()).await();
        assertThat(outcome.exitCode()).isEqualTo(3);
        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.logTail().lines().count()).isEqualTo(50);
        assertThat(outcome.logTail()).contains("line-80").doesNotContain("line-30\n");
    }

    @Test
    @DisplayName("stop() terminates the process and flags stoppedByPlatform")
    void stop() throws Exception {
        Files.createFile(dir.resolve("hang"));
        var handle = launcher().start(spec(), FakeWorker.class.getName());
        assertThat(handle.alive()).isTrue();
        handle.stop(Duration.ofSeconds(5));
        var outcome = handle.await();
        assertThat(handle.alive()).isFalse();
        assertThat(outcome.stoppedByPlatform()).isTrue();
        assertThat(outcome.completed()).isFalse();
    }

    @Test
    @DisplayName("command includes jvm-args, -D properties, classpath and run directory")
    void command() {
        var props = new InjectionWorkerProperties(1, java.util.List.of("-Xmx64m"), null);
        var l = new ProcessInjectionWorkerLauncher(props, null, "java", "a.jar");
        assertThat(l.command(spec(), "Main")).containsExactly("java", "-Xmx64m", "-Dmy.prop=v1",
                "-cp", "a.jar", "Main", dir.toString());
    }
}
