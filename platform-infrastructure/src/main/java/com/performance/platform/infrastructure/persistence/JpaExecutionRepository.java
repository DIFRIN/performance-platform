package com.performance.platform.infrastructure.persistence;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.infrastructure.persistence.mapper.ExecutionStateMapper;
import com.performance.platform.infrastructure.persistence.mapper.TaskResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter of the {@link ExecutionRepository} port.
 * Delegates persistence to Spring Data JPA repositories and uses
 * the domain-entity mappers (ISSUE-051) for conversion.
 *
 * <p>Transaction management is delegated to the underlying Spring Data
 * repositories ({@code SimpleJpaRepository.save()} is already
 * {@code @Transactional}). The {@link #deleteById(ExecutionId)} method uses
 * a {@link TransactionTemplate} for programmatic transaction management so that
 * the cascading delete (task results then execution state) is atomic regardless
 * of whether this bean is accessed through a Spring AOP proxy or directly.
 * Database access is expected to run under Virtual Threads (configured by the
 * caller via {@code Executors.newVirtualThreadPerTaskExecutor()}).</p>
 *
 * <p>Save operations are idempotent: Spring Data's {@code save()} calls
 * {@code EntityManager.merge()} for entities with manually-assigned IDs,
 * which inserts when the row does not exist and updates when it does.</p>
 */
@Repository
public class JpaExecutionRepository implements ExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaExecutionRepository.class);

    private final ExecutionStateJpaRepository stateRepo;
    private final TaskResultJpaRepository taskResultRepo;
    private final ExecutionStateMapper stateMapper;
    private final TaskResultMapper taskResultMapper;
    private final TransactionTemplate txTemplate;

    /**
     * Constructor injection.
     *
     * @param stateRepo        Spring Data repository for execution states
     * @param taskResultRepo   Spring Data repository for task results
     * @param stateMapper      domain-to-entity mapper for execution states
     * @param taskResultMapper domain-to-entity mapper for task results
     * @param txManager        platform transaction manager for programmatic transactions
     */
    public JpaExecutionRepository(ExecutionStateJpaRepository stateRepo,
                                   TaskResultJpaRepository taskResultRepo,
                                   ExecutionStateMapper stateMapper,
                                   TaskResultMapper taskResultMapper,
                                   PlatformTransactionManager txManager) {
        this.stateRepo = stateRepo;
        this.taskResultRepo = taskResultRepo;
        this.stateMapper = stateMapper;
        this.taskResultMapper = taskResultMapper;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public void save(ExecutionState state) {
        log.info("action=save_execution_state executionId={} scenarioId={}",
                state.id().value(), state.scenarioId().value());
        ExecutionStateEntity entity = stateMapper.toEntity(state);
        stateRepo.save(entity);
        log.info("action=execution_state_saved executionId={}", state.id().value());
    }

    @Override
    public Optional<ExecutionState> findById(ExecutionId id) {
        log.info("action=find_execution_state executionId={}", id.value());
        Optional<ExecutionState> result = stateRepo.findById(id.value())
                .map(stateMapper::toDomain);
        if (result.isPresent()) {
            log.info("action=execution_state_found executionId={}", id.value());
        } else {
            log.info("action=execution_state_not_found executionId={}", id.value());
        }
        return result;
    }

    @Override
    public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {
        log.info("action=update_phase executionId={} phase={} status={}",
                id.value(), phase, status);
        ExecutionStateEntity entity = stateRepo.findById(id.value())
                .orElseThrow(() -> new IllegalStateException(
                        "Execution not found: " + id.value()));

        var phases = new LinkedHashMap<String, String>(entity.phases());
        phases.put(phase.name(), status.name());

        var updated = new ExecutionStateEntity(
                entity.id(), entity.scenarioId(), entity.status(),
                phases, entity.context(),
                entity.startedAt(), Instant.now());

        stateRepo.save(updated);
        log.info("action=phase_updated executionId={} phase={} status={}",
                id.value(), phase, status);
    }

    @Override
    public void saveTaskResult(ExecutionId id, TaskId taskId,
                                AgentId agentId, TaskResult result) {
        log.info("action=save_task_result executionId={} taskId={} agentId={}",
                id.value(), taskId.value(), agentId.value());
        TaskResultEntity entity = taskResultMapper.toEntity(id, taskId, agentId, result);
        taskResultRepo.save(entity);
        log.info("action=task_result_saved executionId={} taskId={} agentId={}",
                id.value(), taskId.value(), agentId.value());
    }

    @Override
    public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
        log.info("action=get_task_results executionId={} taskId={}",
                id.value(), taskId.value());
        List<TaskResultEntity> entities = taskResultRepo.findByExecutionIdAndTaskId(
                id.value(), taskId.value());
        Map<AgentId, TaskResult> results = entities.stream()
                .collect(Collectors.toMap(
                        e -> AgentId.of(e.agentId()),
                        taskResultMapper::toDomain,
                        (a, b) -> a,
                        LinkedHashMap::new));
        log.info("action=task_results_retrieved executionId={} taskId={} count={}",
                id.value(), taskId.value(), results.size());
        return results;
    }

    @Override
    public List<ExecutionState> findAll(int limit) {
        log.info("action=find_all_executions limit={}", limit);
        List<ExecutionStateEntity> entities =
                stateRepo.findTopByStartedAtDesc(PageRequest.of(0, limit));
        List<ExecutionState> result = entities.stream()
                .map(stateMapper::toDomain)
                .collect(Collectors.toList());
        log.info("action=find_all_executions_done count={} limit={}", result.size(), limit);
        return result;
    }

    @Override
    public void deleteById(ExecutionId id) {
        log.info("action=delete_execution executionId={}", id.value());
        txTemplate.executeWithoutResult(status -> {
            if (!stateRepo.existsById(id.value())) {
                log.info("action=delete_execution_noop executionId={} reason=not_found", id.value());
                return;
            }
            taskResultRepo.deleteByExecutionId(id.value());
            stateRepo.deleteById(id.value());
        });
        log.info("action=delete_execution_done executionId={}", id.value());
    }
}
