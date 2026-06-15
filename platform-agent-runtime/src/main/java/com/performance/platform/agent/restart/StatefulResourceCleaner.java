package com.performance.platform.agent.restart;

import com.performance.platform.domain.id.ExecutionId;

/**
 * Contrat de libération des ressources stateful d'un {@code TaskExecutor}.
 * <p>
 * Chaque implémentation de {@code TaskExecutor} qui maintient un état local
 * (connexions poolées, containers Docker, serveurs mock) doit implémenter
 * cette interface pour permettre un restart propre.
 * <p>
 * {@code executionId} nullable : quand il est {@code null}, toutes les
 * ressources doivent être libérées (cleanup global).
 * <p>
 * Cleaners attendus (PDR-010/013) : Gatling, MockServer, Kafka, Database, Docker, Shell.
 *
 * @see com.performance.platform.agent.restart.ScenarioRestartHandler
 */
public interface StatefulResourceCleaner {

    /**
     * Libère les ressources stateful associées à l'executionId donné.
     *
     * @param executionId nullable — si null, libérer toutes les ressources
     */
    void cleanup(ExecutionId executionId);
}
