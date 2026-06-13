package com.performance.platform.application.ports.out;

import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.id.AgentId;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant vers le registre des agents.
 * Permet de suivre le cycle de vie des agents connectes : enregistrement,
 * heartbeat, expiration, desenregistrement, et requetage.
 * 0 annotation framework.
 */
public interface AgentRegistryPort {

    /**
     * Notifie le registre qu'un nouvel agent vient de s'enregistrer.
     *
     * @param descriptor les metadonnees completes de l'agent
     */
    void onAgentRegistered(AgentDescriptor descriptor);

    /**
     * Notifie le registre qu'un agent a emis un heartbeat.
     * Met a jour le timestamp de dernier heartbeat et l'etat actif.
     *
     * @param agentId   l'identifiant de l'agent
     * @param heartbeat le heartbeat reçu
     */
    void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);

    /**
     * Notifie le registre qu'un agent est considere comme expire
     * (depassement du TTL sans heartbeat).
     *
     * @param agentId l'identifiant de l'agent expire
     */
    void onAgentExpired(AgentId agentId);

    /**
     * Notifie le registre qu'un agent s'est explicitement desenregistre.
     *
     * @param agentId l'identifiant de l'agent
     */
    void onAgentDeregistered(AgentId agentId);

    /**
     * Retourne tous les agents competents pour un nom de tache.
     * AUCUNE selection — retourne tous les agents qui declarent la tache.
     *
     * @param taskName le nom de la tache a verifier
     * @return la liste des agents pouvant executer cette tache
     */
    List<AgentDescriptor> findByTaskName(String taskName);

    /**
     * Verifie si au moins un agent est disponible pour un nom de tache.
     *
     * @param taskName le nom de la tache a verifier
     * @return true si au moins un agent est competent
     */
    boolean hasAgentFor(String taskName);

    /**
     * Recupere les metadonnees d'un agent par son identifiant.
     *
     * @param agentId l'identifiant de l'agent
     * @return l'agent s'il est enregistre
     */
    Optional<AgentDescriptor> findById(AgentId agentId);

    /**
     * Retourne la liste de tous les agents enregistres.
     *
     * @return la liste complete
     */
    List<AgentDescriptor> findAll();
}
