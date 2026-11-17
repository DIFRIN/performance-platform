package com.performance.platform.injection.gatling.worker;

import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import com.performance.platform.injection.gatling.runner.GatlingRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Lanceur de test : execute un {@link GatlingRunner} stub au lieu d'une JVM fille. */
public final class RunnerBackedLauncher implements InjectionWorkerLauncher {

    private final GatlingRunner runner;

    public RunnerBackedLauncher(GatlingRunner runner) {
        this.runner = runner;
    }

    public static InjectionWorkerLauncher of(GatlingRunner runner) {
        return new RunnerBackedLauncher(runner);
    }

    @Override
    public WorkerHandle start(WorkerRunSpec spec) {
        var dir = new RunDirectory(spec.runDirectory());
        var future = new CompletableFuture<WorkerOutcome>();
        Thread.ofVirtual().start(() -> {
            try {
                var loadModel = dir.readLoadModel();
                Path results = runner.run(new GatlingRunConfig(spec.simulationClass(), loadModel,
                        spec.systemProperties(), dir.resultsDirectory(), spec.simulationId(), spec.timeout()));
                future.complete(new WorkerOutcome(0, true, false, results, dir.resultFile(), ""));
            } catch (Throwable t) {
                future.complete(new WorkerOutcome(1, false, false, null, dir.resultFile(), String.valueOf(t.getMessage())));
            }
        });
        return new WorkerHandle() {
            @Override public WorkerOutcome await() throws InterruptedException {
                try {
                    return future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException(e);
                }
            }
            @Override public void stop(Duration grace) { }
            @Override public boolean alive() { return !future.isDone(); }
        };
    }
}
