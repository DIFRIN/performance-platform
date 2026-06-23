package com.performance.platform.application.usecase;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de {@link ListExecutionsService}.
 */
@DisplayName("ListExecutionsService")
class ListExecutionsServiceTest {

    private static final ScenarioId SCENARIO_ID = ScenarioId.of(UUID.randomUUID().toString());

    private AtomicInteger capturedLimit;
    private List<ExecutionState> repositoryResult;
    private ExecutionRepository repository;
    private ListExecutionsService service;

    @BeforeEach
    void setUp() {
        capturedLimit = new AtomicInteger(-1);
        repositoryResult = List.of();
        repository = new NoOpExecutionRepository() {
            @Override
            public List<ExecutionState> findAll(int limit) {
                capturedLimit.set(limit);
                return repositoryResult;
            }
        };
        service = new ListExecutionsService(repository);
    }

    // --- Nominal

    @Test
    @DisplayName("list(10) transmet limit=10 au repository")
    void listWithPositiveLimitDelegatesToRepository() {
        service.list(10);
        assertEquals(10, capturedLimit.get());
    }

    @Test
    @DisplayName("list(0) applique le defaut de 50")
    void listWithZeroLimitAppliesDefault() {
        service.list(0);
        assertEquals(ListExecutionsService.DEFAULT_LIMIT, capturedLimit.get());
    }

    @Test
    @DisplayName("list(-5) applique le defaut de 50")
    void listWithNegativeLimitAppliesDefault() {
        service.list(-5);
        assertEquals(ListExecutionsService.DEFAULT_LIMIT, capturedLimit.get());
    }

    @Test
    @DisplayName("list retourne la liste du repository")
    void listReturnsRepositoryResult() {
        var state = buildState(ExecutionStatus.COMPLETED);
        repositoryResult = List.of(state);

        var result = service.list(5);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(state, result.get(0));
    }

    @Test
    @DisplayName("list retourne une liste vide si le repository est vide")
    void listReturnsEmptyListWhenRepositoryEmpty() {
        repositoryResult = List.of();

        var result = service.list(10);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // --- Constructeur

    @Test
    @DisplayName("constructeur leve NullPointerException si repository null")
    void constructorRejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new ListExecutionsService(null));
    }

    // --- Helpers

    private ExecutionState buildState(ExecutionStatus status) {
        var id = ExecutionId.generate();
        return new ExecutionState(
                id, SCENARIO_ID, status, Map.of(),
                ExecutionContext.initial(id, SCENARIO_ID),
                Instant.now(), Instant.now()
        );
    }

    /** Repository no-op pour les tests. */
    private static abstract class NoOpExecutionRepository implements ExecutionRepository {
        @Override public void save(ExecutionState state) {}
        @Override public Optional<ExecutionState> findById(ExecutionId id) { return Optional.empty(); }
        @Override public void updatePhase(ExecutionId id, com.performance.platform.domain.scenario.Phase phase, com.performance.platform.domain.execution.PhaseStatus status) {}
        @Override public void saveTaskResult(ExecutionId id, com.performance.platform.domain.id.TaskId taskId, com.performance.platform.domain.id.AgentId agentId, com.performance.platform.domain.task.TaskResult result) {}
        @Override public Map<com.performance.platform.domain.id.AgentId, com.performance.platform.domain.task.TaskResult> getTaskResults(ExecutionId id, com.performance.platform.domain.id.TaskId taskId) { return Map.of(); }
        @Override public List<ExecutionState> findAll(int limit) { return List.of(); }
        @Override public void deleteById(ExecutionId id) {}
    }
}
