package com.performance.platform.application.usecase;

import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation du use case {@link GetExecutionStatusUseCase}.
 * Lit le statut et l'etat d'une execution uniquement depuis l'{@link ExecutionRepository}
 * (read-model, CQRS-lean — ADR-026). Decorrelee de l'engine (commande).
 * 0 annotation Spring.
 */
public final class GetExecutionStatusService implements GetExecutionStatusUseCase {

    private final ExecutionRepository repository;

    public GetExecutionStatusService(ExecutionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository required");
    }

    @Override
    public ExecutionStatus getStatus(ExecutionId id) {
        return repository.findById(id)
                .map(ExecutionState::status)
                .orElse(ExecutionStatus.STARTED);
    }

    @Override
    public Optional<ExecutionState> getState(ExecutionId id) {
        return repository.findById(id);
    }
}
