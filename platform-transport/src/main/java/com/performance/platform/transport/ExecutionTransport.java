package com.performance.platform.transport;

import com.performance.platform.domain.event.AgentSignal;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

/**
 * Interface publique critique (&#x26A1;) d'abstraction de communication entre
 * l'Orchestrateur et les Agents.
 * <p>
 * Toute modification de cette interface necessite un ADR (Architecture
 * Decision Record).
 * <p>
 * <strong>Modele de communication :</strong> broadcast + filtre cote agent.
 * {@code dispatchTask()} est un broadcast pur — jamais de ciblage par
 * {@code agentId}. La selection se fait cote agent via le
 * {@code TaskSpecializationFilter} (voir ADR-008).
 * <p>
 * <strong>Interfaces implementees :</strong>
 * <ul>
 *   <li>{@code InMemoryExecutionTransport} — mode LOCAL + tests</li>
 *   <li>{@code KafkaExecutionTransport} — broker Kafka</li>
 *   <li>{@code RabbitMQExecutionTransport} — broker RabbitMQ</li>
 *   <li>{@code HttpExecutionTransport} — HTTP polling</li>
 *   <li>{@code SocketExecutionTransport} — sockets TCP</li>
 * </ul>
 */
public interface ExecutionTransport {

    /**
     * Diffuse une demande d'execution de task a tous les agents
     * (broadcast). Les agents non concernes ignorent la demande via leur
     * {@code TaskSpecializationFilter}.
     *
     * @param request la demande d'execution de task
     * @throws TransportException en cas d'erreur de communication
     */
    void dispatchTask(TaskExecutionRequest request);

    /**
     * Diffuse un signal a tous les agents (broadcast).
     *
     * @param signal le signal a diffuser
     * @throws TransportException en cas d'erreur de communication
     */
    void broadcastSignal(AgentSignal signal);

    /**
     * Publie un evenement d'execution. Les souscripteurs enregistres
     * via {@link #subscribe(ExecutionEventHandler)} recoivent l'evenement.
     *
     * @param event l'evenement a publier
     * @throws TransportException en cas d'erreur de communication
     */
    void publishEvent(ExecutionEvent event);

    /**
     * Enregistre un handler qui sera appele pour chaque
     * {@link ExecutionEvent} publie.
     *
     * @param handler le handler a enregistrer
     * @return une {@link Subscription} permettant d'annuler l'enregistrement
     */
    Subscription subscribe(ExecutionEventHandler handler);

    /**
     * Enregistre le handler appele a la reception d'une
     * {@link TaskExecutionRequest} (cote agent).
     *
     * @param handler le handler a enregistrer
     */
    void receiveTask(TaskRequestHandler handler);

    /**
     * Enregistre le handler appele a la reception d'un
     * {@link AgentSignal} (cote agent).
     *
     * @param handler le handler a enregistrer
     */
    void receiveSignal(AgentSignalHandler handler);

    /**
     * Etablit la connexion au medium de transport.
     *
     * @throws TransportException en cas d'echec de connexion
     */
    void connect() throws TransportException;

    /**
     * Ferme proprement la connexion au medium de transport.
     */
    void disconnect();

    /**
     * Retourne {@code true} si le transport est actuellement connecte.
     *
     * @return true si connecte
     */
    boolean isConnected();

    /**
     * Retourne le type de ce transport.
     *
     * @return le type de transport
     */
    TransportType getType();
}
