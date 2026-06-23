package com.performance.platform.application.usecase;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;

import java.util.Objects;
import java.util.Set;

/**
 * Implementation du use case {@link DeleteExecutionUseCase}.
 * Leve {@link ExecutionNotDeletableException} si l'execution est active (STARTED ou RUNNING).
 * Autorise la suppression pour les statuts terminaux : COMPLETED, FAILED, CANCELLED (ADR-020).
 */
public class DeleteExecutionService implements DeleteExecutionUseCase {

    private static final Set<ExecutionStatus> DELETABLE_STATUSES = Set.of(
            ExecutionStatus.COMPLETED,
            ExecutionStatus.FAILED,
            ExecutionStatus.CANCELLED
    );

    private final ExecutionRepository repository;

    public DeleteExecutionService(ExecutionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository required");
    }

    @Override
    public void delete(ExecutionId id) {
        Objects.requireNonNull(id, "id required");

        var state = repository.findById(id)
                .orElseThrow(() -> new ExecutionException("Execution not found: " + id, id));

        if (!DELETABLE_STATUSES.contains(state.status())) {
            throw new ExecutionNotDeletableException(id, state.status());
        }

        repository.deleteById(id);
    }
}
