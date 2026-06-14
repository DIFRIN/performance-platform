package com.performance.platform.agent.filter;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.transport.message.TaskExecutionRequest;

import java.util.Objects;
import java.util.Set;

/**
 * Implémentation par défaut du {@link TaskSpecializationFilter}.
 * <p>
 * Vérifie si le {@code taskName} de la step fait partie des
 * {@code supportedTaskNames} de l'agent. Si oui, retourne
 * {@link TaskFilterResult.Responsible}, sinon
 * {@link TaskFilterResult.NotResponsible}.
 * <p>
 * La décision est purement locale — aucun appel réseau, aucune
 * coordination avec d'autres agents (voir ADR-008).
 */
public class DefaultTaskSpecializationFilter implements TaskSpecializationFilter {

    private final Set<String> supportedTaskNames;
    private final AgentId agentId;

    /**
     * Construit un filtre pour un agent donné.
     *
     * @param supportedTaskNames les noms de tasks supportées par cet agent
     * @param agentId            l'identifiant de l'agent local
     */
    public DefaultTaskSpecializationFilter(Set<String> supportedTaskNames, AgentId agentId) {
        this.supportedTaskNames = Set.copyOf(Objects.requireNonNull(
                supportedTaskNames, "supportedTaskNames must not be null"));
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
    }

    @Override
    public TaskFilterResult filter(TaskExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var taskName = request.step().taskName();
        var messageId = request.id();

        if (supportedTaskNames.contains(taskName)) {
            return new TaskFilterResult.Responsible(messageId, agentId);
        }
        return new TaskFilterResult.NotResponsible(messageId, agentId);
    }
}
