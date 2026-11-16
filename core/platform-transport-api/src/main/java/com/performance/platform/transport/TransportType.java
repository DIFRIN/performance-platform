package com.performance.platform.transport;

/**
 * Type de transport pour la couche de communication Orchestrator-Agent.
 * <p>
 * ADR-056 : seuls IN_MEMORY, HTTP et KAFKA sont supportes par le build coeur.
 * Le type IN_MEMORY est utilise pour les tests et le mode LOCAL.
 */
public enum TransportType {
    KAFKA,
    HTTP,
    IN_MEMORY,
    CUSTOM
}
