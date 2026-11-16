package com.performance.platform.engine.shared;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.task.TaskResult;

import java.util.Objects;

/**
 * Resultat d'une etape pour un agent donne (ADR-055).
 * <p>
 * Avec {@code fanOut: all}, une etape produit un resultat par agent accorde ;
 * le contexte d'execution les stocke sous {@code taskId -> agentId}.
 *
 * @param agentId agent qui a execute l'etape
 * @param result  resultat produit
 */
public record StepDispatchResult(AgentId agentId, TaskResult result) {

    public StepDispatchResult {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(result, "result required");
    }
}
