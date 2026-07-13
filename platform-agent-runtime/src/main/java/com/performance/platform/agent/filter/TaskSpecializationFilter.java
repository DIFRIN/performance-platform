package com.performance.platform.agent.filter;

import com.performance.platform.transport.message.TaskExecutionRequest;

/**
 * Filtre côté agent qui détermine si l'agent prend en charge une
 * {@link TaskExecutionRequest}.
 * <p>
 * Remplace l'ancien mécanisme de sélection par {@code agentId} côté
 * orchestrateur. La décision est purement locale : l'agent vérifie si
 * la tâche fait partie de ses compétences (voir ADR-008).
 * <p>
 * Implémentation par défaut : {@link DefaultTaskSpecializationFilter}
 * qui vérifie {@code supportedTaskNames.contains(request.step().taskName())}.
 */
@FunctionalInterface
public interface TaskSpecializationFilter {

    /**
     * Détermine si cet agent prend en charge la demande d'exécution.
     *
     * @param request la demande d'exécution (broadcast)
     * @return {@link TaskFilterResult.Responsible} si compétent,
     *         {@link TaskFilterResult.NotResponsible} sinon
     */
    TaskFilterResult filter(TaskExecutionRequest request);

    /**
     * Vérifie si cet agent peut exécuter le nom de tâche donné.
     * Utilisé pour le filtrage des signaux de cycle de vie.
     *
     * @param taskName le nom de la tâche
     * @return true si l'agent supporte cette tâche
     */
    default boolean canExecute(String taskName) {
        return false;
    }
}
