package com.performance.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TaskResultEntity}.
 * Supports multi-claim retrieval (ADR-011) via the composite primary key.
 */
public interface TaskResultJpaRepository extends JpaRepository<TaskResultEntity, TaskResultId> {

    /**
     * Finds all task results for a given execution and task, supporting
     * the multi-claim pattern where multiple agents produce results for
     * the same task within one execution.
     *
     * @param executionId execution identifier
     * @param taskId      task identifier
     * @return list of task result entities (possibly empty, never null)
     */
    @Query("SELECT t FROM TaskResultEntity t WHERE t.id.executionId = :executionId AND t.id.taskId = :taskId")
    List<TaskResultEntity> findByExecutionIdAndTaskId(
            @Param("executionId") String executionId,
            @Param("taskId") String taskId);
}
