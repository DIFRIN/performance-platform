package com.performance.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ExecutionStateEntity}.
 * Provides CRUD operations with the entity's {@code String} primary key.
 */
public interface ExecutionStateJpaRepository extends JpaRepository<ExecutionStateEntity, String> {
}
