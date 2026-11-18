package com.performance.platform.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.injection.gatling.GatlingTaskExecutor;
import com.performance.platform.injection.gatling.result.DefaultGatlingResultParser;
import com.performance.platform.injection.gatling.worker.InjectionWorkerProperties;
import com.performance.platform.injection.gatling.worker.ProcessInjectionWorkerLauncher;

import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Worker d'injection en mode LOCAL (ADR-066) : run nominal, annulation, concurrence.
 * <p>
 * Le worker est une vraie JVM fille ({@link ProcessInjectionWorkerLauncher}), le SUT un
 * WireMock embarque. La simulation de test est une classe custom (parametre {@code simulation}),
 * chargee depuis le classpath herite du parent.
 */
@Tag("integration")
@DisplayName("InjectionWorkerIT")
class InjectionWorkerIT {

    /** Simulation minimale : GET /ping sur {@code test.baseUrl}, profil issu du load model. */
    @SuppressWarnings("this-escape") // Gatling requires setUp() during Simulation construction.
    public static class PingSimulation extends Simulation {
        {
            String simId = System.getProperty("gatling.simulationId", "unknown");
            List<OpenInjectionStep> steps =
                    com.performance.platform.injection.gatling.runner.SimulationInjectionHolder.get(simId);
            HttpProtocolBuilder protocol = http.baseUrl(System.getProperty("test.baseUrl"));
            ScenarioBuilder scn = scenario("ping").exec(http("ping").get("/ping"));
            setUp(scn.injectOpen(steps.toArray(new OpenInjectionStep[0]))).protocols(protocol);
        }
    }

    private static final ScenarioId SCENARIO = ScenarioId.of("worker-it");

    private static WireMockServer sut;
    private static ProcessInjectionWorkerLauncher launcher;
    private GatlingTaskExecutor executor;

    @BeforeAll
    static void startSut() {
        sut = new WireMockServer(0);
        sut.start();
        sut.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")));
        launcher = new ProcessInjectionWorkerLauncher(InjectionWorkerProperties.defaults(), "./plugins");
    }

    @AfterAll
    static void stopSut() {
        launcher.destroy();
        sut.stop();
    }

    @AfterEach
    void cleanup() {
        if (executor != null) executor.cleanup(null);
    }

    private GatlingTaskExecutor newExecutor() {
        executor = new GatlingTaskExecutor(launcher, new DefaultGatlingResultParser());
        return executor;
    }

    private StepDefinition step(String id, int usersPerSec, int durationSeconds) {
        return new StepDefinition(TaskId.of(id), "gatling", Phase.INJECTION, Map.of(
                "simulation", PingSimulation.class.getName(),
                "timeout", 120,
                "systemProperties", Map.of("test.baseUrl", "http://localhost:" + sut.port()),
                "workload", com.performance.platform.domain.injection.WorkloadPlanCodec.toMap(
                        com.performance.platform.domain.injection.LoadPresets.expand("CONSTANT",
                                Map.of("usersPerSec", usersPerSec, "durationSeconds", durationSeconds)))),
                null, null, null, null);
    }

    private static ExecutionContext ctx(String executionId) {
        return ExecutionContext.initial(ExecutionId.of(executionId), SCENARIO);
    }

    @Test
    @DisplayName("run nominal : le worker produit un InjectionResult non vide")
    void nominalRun() {
        TaskResult result = newExecutor().execute(ctx("exec-nominal"), step("inj-nominal", 2, 3));

        assertThat(result.status()).as("error=%s", result.errorMessage()).isEqualTo(TaskStatus.SUCCESS);
        var injection = (InjectionResult) result.outputs().get(InjectionResult.OUTPUT_KEY);
        assertThat(injection.totalRequests()).isPositive();
        assertThat(sut.getAllServeEvents()).isNotEmpty();
    }

    @Test
    @DisplayName("annulation : cleanup(executionId) arrête le worker et la charge")
    void cancellationStopsWorker() {
        var exec = newExecutor();
        var run = CompletableFuture.supplyAsync(
                () -> exec.execute(ctx("exec-cancel"), step("inj-cancel", 2, 120)),
                Executors.newVirtualThreadPerTaskExecutor());

        await().atMost(Duration.ofSeconds(60)).until(() -> !exec.canAcceptNewTask());
        exec.cleanup(ExecutionId.of("exec-cancel"));

        TaskResult result = run.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join();
        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.errorMessage()).contains("stopped by platform");
        await().atMost(Duration.ofSeconds(5)).until(exec::canAcceptNewTask);

        // plus aucune charge après l'arrêt
        int before = sut.getAllServeEvents().size();
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(sut.getAllServeEvents()).hasSize(before));
    }

    @Test
    @DisplayName("deux injections concurrentes : la seconde n'est pas exécutée en parallèle")
    void secondInjectionIsRejectedWhileFirstRuns() {
        var exec = newExecutor();
        var first = CompletableFuture.supplyAsync(
                () -> exec.execute(ctx("exec-conc-1"), step("inj-conc-1", 2, 120)),
                Executors.newVirtualThreadPerTaskExecutor());

        await().atMost(Duration.ofSeconds(60)).until(() -> !exec.canAcceptNewTask());

        TaskResult second = exec.execute(ctx("exec-conc-2"), step("inj-conc-2", 1, 2));
        assertThat(second.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(second.errorMessage()).contains("concurrency limit");
        assertThat(first).isNotDone();

        exec.cleanup(ExecutionId.of("exec-conc-1"));
        assertThat(first.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join().status())
                .isEqualTo(TaskStatus.FAILED);
    }
}
