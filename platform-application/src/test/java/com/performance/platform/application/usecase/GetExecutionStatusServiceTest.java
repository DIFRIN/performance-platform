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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de {@link GetExecutionStatusService}.
 */
@DisplayName("GetExecutionStatusService")
class GetExecutionStatusServiceTest {

    private static final ScenarioId SCENARIO_ID = ScenarioId.of(UUID.randomUUID().toString());

    private Optional<ExecutionState> repositoryResult;
    private ExecutionRepository repository;
    private GetExecutionStatusService service;

    @BeforeEach
    void setUp() {
        repositoryResult = Optional.empty();
        repository = new NoOpExecutionRepository() {
            @Override
            public Optional<ExecutionState> findById(ExecutionId id) {
                return repositoryResult;
            }
        };
        service = new GetExecutionStatusService(repository);
    }

    // --- Etat present

    @Test
    @DisplayName("getStatus retourne le statut persiste quand l'etat existe")
    void getStatusReturnsPersistedStatusWhenStatePresent() {
        var state = buildState(ExecutionStatus.COMPLETED);
        repositoryResult = Optional.of(state);

        ExecutionStatus status = service.getStatus(state.id());

        assertEquals(ExecutionStatus.COMPLETED, status);
    }

    @Test
    @DisplayName("getState retourne l'etat complet quand l'etat existe")
    void getStateReturnsFullStateWhenPresent() {
        var state = buildState(ExecutionStatus.RUNNING);
        repositoryResult = Optional.of(state);

        Optional<ExecutionState> result = service.getState(state.id());

        assertTrue(result.isPresent());
        assertEquals(state, result.get());
    }

    // --- Etat absent

    @Test
    @DisplayName("getStatus retourne STARTED par defaut quand l'etat est absent")
    void getStatusReturnsStartedDefaultWhenStateAbsent() {
        ExecutionStatus status = service.getStatus(ExecutionId.generate());

        assertEquals(ExecutionStatus.STARTED, status);
    }

    @Test
    @DisplayName("getState retourne Optional.empty() quand l'etat est absent")
    void getStateReturnsEmptyWhenStateAbsent() {
        Optional<ExecutionState> result = service.getState(ExecutionId.generate());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // --- Constructeur

    @Test
    @DisplayName("constructeur leve NullPointerException si repository null")
    void constructorRejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new GetExecutionStatusService(null));
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
    private abstract static class NoOpExecutionRepository implements ExecutionRepository {
        @Override public void save(ExecutionState state) {}
        @Override public Optional<ExecutionState> findById(ExecutionId id) { return Optional.empty(); }
        @Override public void updatePhase(ExecutionId id, com.performance.platform.domain.scenario.Phase phase, com.performance.platform.domain.execution.PhaseStatus status) {}
        @Override public void saveTaskResult(ExecutionId id, com.performance.platform.domain.id.TaskId taskId, com.performance.platform.domain.id.AgentId agentId, com.performance.platform.domain.task.TaskResult result) {}
        @Override public Map<com.performance.platform.domain.id.AgentId, com.performance.platform.domain.task.TaskResult> getTaskResults(ExecutionId id, com.performance.platform.domain.id.TaskId taskId) { return Map.of(); }
        @Override public List<ExecutionState> findAll(int limit) { return List.of(); }
        @Override public void deleteById(ExecutionId id) {}
        @Override public Map<com.performance.platform.domain.id.TaskId, Map<com.performance.platform.domain.id.AgentId, com.performance.platform.domain.task.TaskResult>> findAllTaskResults(ExecutionId id) { return Map.of(); }
    }
}
