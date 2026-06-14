package com.performance.platform.agent.filter;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.MessageId;

import java.util.Objects;

/**
 * Résultat du filtrage côté agent : l'agent est-il responsable de cette task ?
 * <p>
 * Type scellé avec deux variantes :
 * <ul>
 *   <li>{@link Responsible} — l'agent prend en charge la task</li>
 *   <li>{@link NotResponsible} — l'agent ignore la task</li>
 * </ul>
 * <p>
 * Voir ADR-008 : le filtrage remplace la sélection explicite par agentId.
 */
public sealed interface TaskFilterResult
        permits TaskFilterResult.Responsible, TaskFilterResult.NotResponsible {

    /**
     * L'agent est responsable de cette task.
     */
    record Responsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {
        public Responsible {
            Objects.requireNonNull(messageId, "messageId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
        }
    }

    /**
     * L'agent n'est pas responsable de cette task.
     */
    record NotResponsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {
        public NotResponsible {
            Objects.requireNonNull(messageId, "messageId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
        }
    }
}
