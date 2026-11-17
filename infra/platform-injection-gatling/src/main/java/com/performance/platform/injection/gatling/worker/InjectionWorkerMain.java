package com.performance.platform.injection.gatling.worker;

import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.injection.gatling.load.DefaultWorkloadPlanTranslator;
import com.performance.platform.injection.gatling.runner.DefaultGatlingRunner;
import com.performance.platform.injection.gatling.runner.GatlingRunConfig;
import com.performance.platform.injection.gatling.runner.GatlingRunner;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Point d'entree de la JVM fille d'injection (ADR-066).
 * Codes de sortie : 0 termine, 1 echec simulation, 2 run directory invalide.
 */
public final class InjectionWorkerMain {

    public static final int EXIT_OK = 0;
    public static final int EXIT_SIMULATION_FAILED = 1;
    public static final int EXIT_INVALID_RUN_DIRECTORY = 2;

    private InjectionWorkerMain() {
    }

    public static void main(String[] args) {
        int code = run(args, new DefaultGatlingRunner(new DefaultWorkloadPlanTranslator()));
        System.exit(code);
    }

    /** Logique testable, sans {@code System.exit}. */
    static int run(String[] args, GatlingRunner runner) {
        if (args == null || args.length < 1 || args[0] == null || args[0].isBlank()
                || !Files.isDirectory(Path.of(args[0]))) {
            System.err.println("action=worker_invalid_run_directory args=" + (args == null ? "null" : String.join(",", args)));
            return EXIT_INVALID_RUN_DIRECTORY;
        }
        RunDirectory dir = new RunDirectory(Path.of(args[0]));
        WorkerRunSpec spec;
        WorkloadPlan plan;
        try {
            spec = dir.readSpec();
            plan = dir.readPlan();
        } catch (Exception e) {
            System.err.println("action=worker_invalid_run_directory runDirectory=" + args[0] + " error=" + e.getMessage());
            return EXIT_INVALID_RUN_DIRECTORY;
        }
        try {
            Path results = runner.run(new GatlingRunConfig(spec.simulationClass(), plan,
                    spec.systemProperties(), dir.resultsDirectory(), spec.simulationId(), spec.timeout()));
            dir.writeResult(new WorkerOutcome(EXIT_OK, true, false, results, dir.resultFile(), ""));
            return EXIT_OK;
        } catch (Throwable t) {
            System.err.println("action=worker_simulation_failed simulationId=" + spec.simulationId()
                    + " error=" + t.getMessage());
            try {
                dir.writeResult(new WorkerOutcome(EXIT_SIMULATION_FAILED, false, false, null,
                        dir.resultFile(), String.valueOf(t.getMessage())));
            } catch (Exception ignored) {
                // le code de sortie reste porteur de l'echec
            }
            return EXIT_SIMULATION_FAILED;
        }
    }
}
