package com.performance.platform.transport;

/**
 * Type de transport pour la couche de communication Orchestrator-Agent.
 * <p>
 * gRPC NON implemente — pas de valeur GRPC.
 * Le type IN_MEMORY est utilise pour les tests et le mode LOCAL.
 */
public enum TransportType {
    KAFKA,
    RABBITMQ,
    HTTP,
    SOCKET,
    IN_MEMORY,
    CUSTOM
}
