package com.performance.platform.domain.event;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Objects;

/**
 * Reponse de l'orchestrateur a un claim d'agent (ADR-055).
 * <p>
 * Un agent n'execute une tache qu'apres avoir recu un grant accorde.
 * Pour {@code fanOut: one}, le premier claim est accorde et les suivants
 * sont rejetes ; pour {@code fanOut: all}, tous les claims de la fenetre
 * de claim sont accordes.
 * <p>
 * Signal diffuse par le canal existant (ADR-056 : ports inchanges) ;
 * chaque agent ne retient que le grant qui le concerne.
 *
 * @param id          identifiant unique du signal
 * @param executionId execution concernee
 * @param taskId      etape concernee
 * @param messageId   message de dispatch concernee (correlation)
 * @param agentId     agent destinataire du verdict
 * @param granted     true si l'agent doit executer la tache
 * @param issuedAt    instant d'emission
 */
public record TaskGrantSignal(
        SignalId id,
        ExecutionId executionId,
        TaskId taskId,
        MessageId messageId,
        AgentId agentId,
        boolean granted,
        Instant issuedAt
) implements AgentSignal {

    public TaskGrantSignal {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(messageId, "messageId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(issuedAt, "issuedAt required");
    }
}
