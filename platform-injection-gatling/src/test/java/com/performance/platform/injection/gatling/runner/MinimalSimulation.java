package com.performance.platform.injection.gatling.runner;

import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.List;


import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Simulation Gatling minimale pour les tests de {@link DefaultGatlingRunner}.
 * <p>
 * Recupere les etapes d'injection depuis {@link SimulationInjectionHolder}
 * en utilisant le {@code simulationId} passe via la propriete systeme
 * {@code gatling.simulationId}. Fallback sur un {@code atOnceUsers(1)}
 * si aucun step n'est enregistre.
 * <p>
 * Effectue une requete HTTP GET sur l'URL de base configuree dans les
 * proprietes systeme ({@code test.baseUrl}).
 */
public class MinimalSimulation extends Simulation {

    {
        String simId = System.getProperty("gatling.simulationId", "unknown");
        List<OpenInjectionStep> steps = SimulationInjectionHolder.get(simId);

        HttpProtocolBuilder httpProtocol = http
                .baseUrl(System.getProperty("test.baseUrl", "http://localhost:8080"))
                .acceptHeader("application/json");

        ScenarioBuilder scn = scenario("Minimal")
                .exec(http("ping").get("/ping"));

        OpenInjectionStep[] injectionSteps;
        if (steps.isEmpty()) {
            injectionSteps = new OpenInjectionStep[]{
                    rampUsersPerSec(1).to(2).during(Duration.ofSeconds(1))
            };
        } else {
            injectionSteps = steps.toArray(new OpenInjectionStep[0]);
        }

        setUp(scn.injectOpen(injectionSteps)).protocols(httpProtocol);
    }
}
