package com.performance.platform.transport.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des connexions socket actives cote orchestrateur.
 * <p>
 * Chaque connexion socket ouverte par un agent est enregistree ici.
 * Le registre permet le broadcast de {@code TaskExecutionRequest} et
 * {@code AgentSignal} sur toutes les connexions actives.
 * <p>
 * <strong>Thread-safe</strong> — utilise {@link ConcurrentHashMap#newKeySet()}.
 * <p>
 * <strong>Best-effort</strong> : si une connexion est rompue pendant le
 * broadcast, l'erreur est loggee et les autres connexions continuent
 * de recevoir.
 */
public class SocketConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SocketConnectionRegistry.class);

    private final Set<ManagedConnection> connections = ConcurrentHashMap.newKeySet();

    /**
     * Enregistre une connexion active.
     *
     * @param socket la connexion socket ouverte
     * @return la {@link ManagedConnection} enregistree
     * @throws IOException si la creation du writer echoue
     */
    public ManagedConnection register(Socket socket) throws IOException {
        ManagedConnection connection = new ManagedConnection(socket);
        connections.add(connection);
        log.info("action=connection_registered total={}", connections.size());
        return connection;
    }

    /**
     * Retire une connexion du registre.
     *
     * @param connection la connexion a retirer
     */
    public void unregister(ManagedConnection connection) {
        connections.remove(connection);
        connection.close();
        log.info("action=connection_unregistered total={}", connections.size());
    }

    /**
     * Diffuse un message texte (JSON) a toutes les connexions actives.
     * <p>
     * Les erreurs de write sont loggees individuellement — une connexion
     * defaillante ne bloque pas le broadcast vers les autres.
     *
     * @param message le message a diffuser (une ligne JSON)
     */
    public void broadcast(String message) {
        int count = 0;
        int errors = 0;
        for (ManagedConnection conn : connections) {
            try {
                conn.send(message);
                count++;
            } catch (Exception e) {
                errors++;
                log.warn("action=broadcast_error error={}", e.getMessage());
                // Retirer la connexion defaillante
                unregister(conn);
            }
        }
        log.debug("action=broadcast sent={} errors={} total={}", count, errors, connections.size());
    }

    /**
     * Retourne le nombre de connexions actives.
     *
     * @return le nombre de connexions
     */
    public int getActiveCount() {
        return connections.size();
    }

    /**
     * Ferme toutes les connexions actives et vide le registre.
     */
    public void closeAll() {
        for (ManagedConnection conn : connections) {
            conn.close();
        }
        connections.clear();
        log.info("action=all_connections_closed");
    }

    // === ManagedConnection ===

    /**
     * Wrapper autour d'une connexion socket avec un {@link BufferedWriter}
     * pour l'envoi de messages JSON.
     */
    static final class ManagedConnection {

        private static final Logger log = LoggerFactory.getLogger(ManagedConnection.class);

        private final Socket socket;
        private final BufferedWriter writer;

        ManagedConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
        }

        /**
         * Envoie un message suivi d'un saut de ligne.
         */
        synchronized void send(String message) throws IOException {
            writer.write(message);
            writer.newLine();
            writer.flush();
        }

        /**
         * Ferme la connexion socket proprement.
         */
        void close() {
            try {
                writer.close();
            } catch (IOException e) {
                log.debug("action=close_writer_error error={}", e.getMessage());
            }
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("action=close_socket_error error={}", e.getMessage());
            }
        }
    }
}
