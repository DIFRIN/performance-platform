package com.performance.platform.application.usecase;

import com.performance.platform.application.exception.ExecutionException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de {@link DeleteExecutionService}.
 */
@DisplayName("DeleteExecutionService")
class DeleteExecutionServiceTest {

    private static final ScenarioId SCENARIO_ID = ScenarioId.of(UUID.randomUUID().toString());

    private ExecutionId storedId;
    private ExecutionState storedState;
    private List<ExecutionId> deletedIds;
    private ExecutionRepository repository;
    private DeleteExecutionService service;

    @BeforeEach
    void setUp() {
        deletedIds = new ArrayList<>();
        repository = new NoOpExecutionRepository() {
            @Override
            public Optional<ExecutionState> findById(ExecutionId id) {
                if (storedId != null && storedId.equals(id)) {
                    return Optional.of(storedState);
                }
                return Optional.empty();
            }

            @Override
            public void deleteById(ExecutionId id) {
                deletedIds.add(id);
            }
        };
        service = new DeleteExecutionService(repository);
    }

    // --- Suppression autorisee (statuts terminaux)

    @ParameterizedTest(name = "delete autorise pour statut={0}")
    @EnumSource(value = ExecutionStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    @DisplayName("delete delegue deleteById pour les statuts terminaux")
    void deleteAllowedForTerminalStatuses(ExecutionStatus status) {
        var id = ExecutionId.generate();
        storedId = id;
        storedState = buildState(id, status);

        service.delete(id);

        assertEquals(1, deletedIds.size());
        assertEquals(id, deletedIds.get(0));
    }

    // --- Suppression interdite (statuts actifs)

    @ParameterizedTest(name = "delete interdit pour statut={0}")
    @EnumSource(value = ExecutionStatus.class, names = {"STARTED", "RUNNING"})
    @DisplayName("delete leve ExecutionNotDeletableException pour les statuts actifs")
    void deleteRejectedForActiveStatuses(ExecutionStatus status) {
        var id = ExecutionId.generate();
        storedId = id;
        storedState = buildState(id, status);

        var ex = assertThrows(ExecutionNotDeletableException.class, () -> service.delete(id));

        assertEquals(id, ex.getExecutionId());
        assertEquals(status, ex.getCurrentStatus());
        assertTrue(deletedIds.isEmpty(), "deleteById ne doit pas etre appele");
    }

    // --- Execution introuvable

    @Test
    @DisplayName("delete leve ExecutionException si l'execution n'existe pas")
    void deleteThrowsWhenNotFound() {
        var unknownId = ExecutionId.generate();

        assertThrows(ExecutionException.class, () -> service.delete(unknownId));
        assertTrue(deletedIds.isEmpty());
    }

    // --- Validation

    @Test
    @DisplayName("delete leve NullPointerException si id null")
    void deleteRejectsNullId() {
        assertThrows(NullPointerException.class, () -> service.delete(null));
    }

    @Test
    @DisplayName("constructeur leve NullPointerException si repository null")
    void constructorRejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new DeleteExecutionService(null));
    }

    // --- Helpers

    private ExecutionState buildState(ExecutionId id, ExecutionStatus status) {
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
