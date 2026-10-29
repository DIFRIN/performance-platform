package com.performance.platform.injection.gatling.runner;

import java.nio.file.Path;

/**
 * Lance une simulation Gatling in-process et retourne le repertoire
 * de resultats produits par Gatling.
 * <p>
 * La simulation est chargee depuis le classpath. Les etapes d'injection
 * sont traduites depuis le {@code LoadModel} du {@link GatlingRunConfig}
 * via le {@code LoadModelTranslator} (ISSUE-054) et rendues disponibles
 * a la simulation via un holder statique.
 * <p>
 * <strong>Virtual Threads</strong> : le lancement est execute sur un
 * Virtual Thread pour ne pas bloquer le thread appelant (utilisation
 * standard des Virtual Threads pour tout I/O bloquant).
 */
@FunctionalInterface
public interface GatlingRunner {

    /**
     * Lance la simulation Gatling et retourne le chemin du repertoire
     * de resultats (contenant {@code simulation.log}, {@code stats.json}, etc.).
     *
     * @param config configuration complete de la simulation
     * @return le chemin du repertoire de resultats Gatling
     * @throws GatlingExecutionException en cas d'echec du lancement
     */
    Path run(GatlingRunConfig config) throws GatlingExecutionException;
}
