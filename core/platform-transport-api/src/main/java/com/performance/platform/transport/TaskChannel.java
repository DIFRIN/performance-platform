package com.performance.platform.transport;

import com.performance.platform.transport.message.TaskExecutionRequest;

/**
 * Canal de dispatch des taches (orchestrateur → agents).
 * <p>
 * Port etroit (ISP, ADR-028) : un consommateur qui ne dispatche que des
 * taches ne depend pas des evenements, signaux ou du lifecycle.
 */
public interface TaskChannel {

    /**
     * Diffuse une demande d'execution de task a tous les agents (broadcast).
     *
     * @param request la demande d'execution
     * @throws TransportException en cas d'erreur de communication
     */
    void dispatchTask(TaskExecutionRequest request);

    /**
     * Enregistre le handler appele a la reception d'une demande de task.
     *
     * @param handler le handler (cote agent)
     */
    void receiveTask(TaskRequestHandler handler);
}
