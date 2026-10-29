package com.performance.platform.transport;

/**
 * Facade composite de migration (ADR-028).
 * <p>
 * Les consommateurs doivent dependre du port etroit dont ils ont besoin
 * ({@link TaskChannel}, {@link SignalChannel}, {@link EventChannel},
 * {@link LifecycleChannel}, {@link ConnectionLifecycle}), pas de cette
 * facade. Elle est conservee pour les beans Spring et la configuration
 * de transport, qui doivent exposer toutes les capacites.
 * <p>
 * Les implementations :
 * <ul>
 *   <li>{@code InMemoryExecutionTransport}</li>
 *   <li>{@code KafkaExecutionTransport}</li>
 *   <li>{@code RabbitMQExecutionTransport}</li>
 *   <li>{@code HttpExecutionTransport}</li>
 *   <li>{@code SocketExecutionTransport}</li>
 * </ul>
 * <p>
 * {@code getType()} a ete supprime : aucun {@code if/switch} sur le type
 * de transport (CF-03).
 */
public interface ExecutionTransport extends TaskChannel, SignalChannel,
        EventChannel, LifecycleChannel, ConnectionLifecycle {
}
