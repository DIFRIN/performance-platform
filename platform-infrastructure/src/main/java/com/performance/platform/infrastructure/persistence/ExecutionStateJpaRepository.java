package com.performance.platform.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ExecutionStateEntity}.
 * Provides CRUD operations with the entity's {@code String} primary key.
 */
public interface ExecutionStateJpaRepository extends JpaRepository<ExecutionStateEntity, String> {

    /**
     * Returns the N most recent executions, sorted by startedAt DESC.
     * The limit is applied in the query via {@link Pageable}.
     *
     * @param pageable pagination/limit descriptor (use {@code PageRequest.of(0, limit)})
     * @return list of execution state entities sorted desc by startedAt
     */
    @Query("SELECT e FROM ExecutionStateEntity e ORDER BY e.startedAt DESC")
    List<ExecutionStateEntity> findTopByStartedAtDesc(Pageable pageable);
}
