package com.performance.platform.application.usecase;

import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionState;

import java.util.List;
import java.util.Objects;

/**
 * Implementation du use case {@link ListExecutionsUseCase}.
 * Delegue la recuperation des executions a l'{@link ExecutionRepository}.
 * Si {@code limit <= 0}, un defaut de 50 est applique.
 */
public class ListExecutionsService implements ListExecutionsUseCase {

    static final int DEFAULT_LIMIT = 50;

    private final ExecutionRepository repository;

    public ListExecutionsService(ExecutionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository required");
    }

    @Override
    public List<ExecutionState> list(int limit) {
        int effective = limit <= 0 ? DEFAULT_LIMIT : limit;
        return repository.findAll(effective);
    }
}
