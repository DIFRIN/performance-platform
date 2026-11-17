package com.performance.platform.injection.gatling.runner;

import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;

import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holder statique thread-safe pour passer les {@link OpenInjectionStep}
 * du {@code WorkloadPlanTranslator} a la simulation Gatling.
 * <p>
 * Les simulations Gatling etant instanciees par le framework Gatling
 * lui-meme, il n'y a pas de mecanisme d'injection de dependance.
 * Ce holder permet au {@code DefaultGatlingRunner} de stocker les
 * etapes d'injection avant le lancement, et a la classe de simulation
 * de les recuperer dans son bloc d'initialisation.
 * <p>
 * <strong>Usage cote runner :</strong>
 * <pre>{@code
 *   SimulationInjectionHolder.set(simulationId, steps);
 *   try { ... Gatling.fromMap(...) ... }
 *   finally { SimulationInjectionHolder.remove(simulationId); }
 * }</pre>
 * <p>
 * <strong>Usage cote simulation :</strong>
 * <pre>{@code
 *   String simId = System.getProperty("gatling.simulationId");
 *   List<OpenInjectionStep> steps = SimulationInjectionHolder.get(simId);
 *   setUp(scn.injectOpen(steps));
 * }</pre>
 */
public final class SimulationInjectionHolder {

    private static final Map<String, List<OpenInjectionStep>> injections =
            new ConcurrentHashMap<>();

    /** Closed-model injection: concurrency steps plus the total plan duration. */
    public record Closed(List<ClosedInjectionStep> steps, Duration totalDuration) {
        public Closed {
            steps = List.copyOf(steps);
        }
    }

    private static final Map<String, Closed> closedInjections = new ConcurrentHashMap<>();

    private SimulationInjectionHolder() {
        // utilitaire non-instanciable
    }

    /**
     * Stocke les etapes d'injection pour une simulation donnee.
     *
     * @param simulationId identifiant unique de la simulation
     * @param steps        les etapes d'injection a injecter
     */
    public static void set(String simulationId, List<OpenInjectionStep> steps) {
        injections.put(simulationId, List.copyOf(steps));
    }

    /**
     * Recupere les etapes d'injection pour une simulation donnee.
     *
     * @param simulationId identifiant unique de la simulation
     * @return les etapes d'injection, ou une liste vide si absentes
     */
    public static List<OpenInjectionStep> get(String simulationId) {
        return injections.getOrDefault(simulationId, List.of());
    }

    /**
     * Retire les etapes d'injection apres la fin de la simulation.
     *
     * @param simulationId identifiant unique de la simulation
     */
    public static void remove(String simulationId) {
        injections.remove(simulationId);
        closedInjections.remove(simulationId);
    }

    /** Stores closed-model steps for a simulation. */
    public static void setClosed(String simulationId, List<ClosedInjectionStep> steps, Duration totalDuration) {
        closedInjections.put(simulationId, new Closed(steps, totalDuration));
    }

    /** @return closed-model injection, or {@code null} when the simulation is open-model */
    public static Closed getClosed(String simulationId) {
        return closedInjections.get(simulationId);
    }
}
