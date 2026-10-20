package com.performance.platform.transport;

/**
 * Cycle de vie de la connexion au medium de transport.
 */
public interface ConnectionLifecycle {

    /**
     * Etablit la connexion.
     *
     * @throws TransportException en cas d'echec
     */
    void connect() throws TransportException;

    /**
     * Ferme proprement la connexion.
     */
    void disconnect();

    /**
     * @return true si le transport est connecte
     */
    boolean isConnected();
}
