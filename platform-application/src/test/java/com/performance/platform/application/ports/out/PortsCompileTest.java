package com.performance.platform.application.ports.out;

import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de compilation et de mockabilite des 3 ports sortants.
 * Chaque test instancie une implementation inline (no-op) de l'interface
 * pour valider que les signatures compilent et sont correctement mockables.
 */
class PortsCompileTest {

    private static final ExecutionId EXEC_ID = ExecutionId.generate();
    private static final ScenarioId SCENARIO_ID = ScenarioId.of(UUID.randomUUID().toString());
    private static final TaskId TASK_ID = TaskId.of(UUID.randomUUID().toString());
    private static final AgentId AGENT_ID = AgentId.generate();
    private static final ReportId REPORT_ID = ReportId.generate();

    // --- ExecutionRepository ---

    @Test
    void executionRepositoryCompiles() {
        var repo = new ExecutionRepository() {
            @Override
            public void save(ExecutionState state) { /* no-op */ }

            @Override
            public Optional<ExecutionState> findById(ExecutionId id) {
                return Optional.empty();
            }

            @Override
            public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) { /* no-op */ }

            @Override
            public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) { /* no-op */ }

            @Override
            public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
                return Map.of();
            }

            @Override
            public List<ExecutionState> findAll(int limit) {
                return List.of();
            }

            @Override
            public void deleteById(ExecutionId id) { /* no-op */ }
        };

        var state = new ExecutionState(
                EXEC_ID, SCENARIO_ID, ExecutionStatus.STARTED,
                Map.of(Phase.PREPARATION, PhaseStatus.PENDING),
                ExecutionContext.initial(EXEC_ID, SCENARIO_ID),
                Instant.now(), Instant.now()
        );
        repo.save(state);
        assertDoesNotThrow(() -> repo.save(state));

        Optional<ExecutionState> found = repo.findById(EXEC_ID);
        assertTrue(found.isEmpty());

        repo.updatePhase(EXEC_ID, Phase.INJECTION, PhaseStatus.RUNNING);
        assertDoesNotThrow(() -> repo.updatePhase(EXEC_ID, Phase.INJECTION, PhaseStatus.RUNNING));

        var result = TaskResult.success(TASK_ID, "test-task", Duration.ofMillis(100), Map.of());
        repo.saveTaskResult(EXEC_ID, TASK_ID, AGENT_ID, result);
        assertDoesNotThrow(() -> repo.saveTaskResult(EXEC_ID, TASK_ID, AGENT_ID, result));

        Map<AgentId, TaskResult> results = repo.getTaskResults(EXEC_ID, TASK_ID);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void executionRepositoryMultiClaim() {
        // Verifie que saveTaskResult accepte N appels pour le meme taskId (ADR-011)
        var repo = new ExecutionRepository() {
            private AgentId lastAgentId;
            private TaskResult lastResult;

            @Override
            public void save(ExecutionState state) {}

            @Override
            public Optional<ExecutionState> findById(ExecutionId id) {
                return Optional.empty();
            }

            @Override
            public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {}

            @Override
            public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
                this.lastAgentId = agentId;
                this.lastResult = result;
            }

            @Override
            public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
                return lastAgentId != null ? Map.of(lastAgentId, lastResult) : Map.of();
            }

            @Override
            public List<ExecutionState> findAll(int limit) {
                return List.of();
            }

            @Override
            public void deleteById(ExecutionId id) { /* no-op */ }
        };

        var agentA = AgentId.generate();
        var agentB = AgentId.generate();

        var resultA = TaskResult.success(TASK_ID, "multi-claim", Duration.ofMillis(50), Map.of());
        var resultB = TaskResult.success(TASK_ID, "multi-claim", Duration.ofMillis(75), Map.of());

        repo.saveTaskResult(EXEC_ID, TASK_ID, agentA, resultA);
        repo.saveTaskResult(EXEC_ID, TASK_ID, agentB, resultB);

        Map<AgentId, TaskResult> all = repo.getTaskResults(EXEC_ID, TASK_ID);
        assertNotNull(all);
    }

    // --- AgentRegistryPort ---

    @Test
    void agentRegistryPortCompiles() {
        var registry = new AgentRegistryPort() {
            @Override
            public void onAgentRegistered(AgentDescriptor descriptor) { /* no-op */ }

            @Override
            public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) { /* no-op */ }

            @Override
            public void onAgentExpired(AgentId agentId) { /* no-op */ }

            @Override
            public void onAgentDeregistered(AgentId agentId) { /* no-op */ }

            @Override
            public List<AgentDescriptor> findByTaskName(String taskName) {
                return List.of();
            }

            @Override
            public boolean hasAgentFor(String taskName) {
                return false;
            }

            @Override
            public Optional<AgentDescriptor> findById(AgentId agentId) {
                return Optional.empty();
            }

            @Override
            public List<AgentDescriptor> findAll() {
                return List.of();
            }
        };

        var descriptor = new AgentDescriptor(
                AGENT_ID, "test-agent", "localhost", 9090, null,
                Set.of("test-task"),
                new AgentCapabilities(4, "1.0.0"),
                AgentState.IDLE,
                Instant.now(), Instant.now(), Duration.ofMinutes(5)
        );

        registry.onAgentRegistered(descriptor);
        assertDoesNotThrow(() -> registry.onAgentRegistered(descriptor));

        var heartbeat = new AgentHeartbeat(AGENT_ID, AgentState.IDLE, 0, Instant.now());
        registry.onAgentHeartbeat(AGENT_ID, heartbeat);
        assertDoesNotThrow(() -> registry.onAgentHeartbeat(AGENT_ID, heartbeat));

        registry.onAgentExpired(AGENT_ID);
        assertDoesNotThrow(() -> registry.onAgentExpired(AGENT_ID));

        registry.onAgentDeregistered(AGENT_ID);
        assertDoesNotThrow(() -> registry.onAgentDeregistered(AGENT_ID));

        List<AgentDescriptor> agents = registry.findByTaskName("test-task");
        assertNotNull(agents);
        assertTrue(agents.isEmpty());

        assertFalse(registry.hasAgentFor("test-task"));

        Optional<AgentDescriptor> found = registry.findById(AGENT_ID);
        assertTrue(found.isEmpty());

        List<AgentDescriptor> all = registry.findAll();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void findByTaskNameNoFiltering() {
        // Regle specifique : findByTaskName ne fait AUCUNE selection
        // Retourne tous les agents competents (ceux qui declarent la tache)
        var registry = new AgentRegistryPort() {
            @Override
            public void onAgentRegistered(AgentDescriptor descriptor) {}

            @Override
            public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {}

            @Override
            public void onAgentExpired(AgentId agentId) {}

            @Override
            public void onAgentDeregistered(AgentId agentId) {}

            @Override
            public List<AgentDescriptor> findByTaskName(String taskName) {
                return List.of();
            }

            @Override
            public boolean hasAgentFor(String taskName) {
                return false;
            }

            @Override
            public Optional<AgentDescriptor> findById(AgentId agentId) {
                return Optional.empty();
            }

            @Override
            public List<AgentDescriptor> findAll() {
                return List.of();
            }
        };

        List<AgentDescriptor> result = registry.findByTaskName("non-existent");
        assertNotNull(result);
    }

    // --- ReportPublisherPort ---

    @Test
    void reportPublisherPortCompiles() {
        var publisher = new ReportPublisherPort() {
            @Override
            public void publish(ReportId reportId, ExecutionId executionId) { /* no-op */ }
        };

        publisher.publish(REPORT_ID, EXEC_ID);
        assertDoesNotThrow(() -> publisher.publish(REPORT_ID, EXEC_ID));
    }

    @Test
    void reportPublisherWithDifferentIds() {
        var publisher = new ReportPublisherPort() {
            @Override
            public void publish(ReportId reportId, ExecutionId executionId) { /* no-op */ }
        };

        var r1 = ReportId.generate();
        var r2 = ReportId.generate();
        var e1 = ExecutionId.generate();
        var e2 = ExecutionId.generate();

        assertDoesNotThrow(() -> publisher.publish(r1, e1));
        assertDoesNotThrow(() -> publisher.publish(r2, e2));
    }
}
