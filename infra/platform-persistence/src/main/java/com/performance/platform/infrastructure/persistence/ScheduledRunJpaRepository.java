package com.performance.platform.infrastructure.persistence;

import com.performance.platform.application.schedule.ScheduledRunStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/** Spring Data repository for {@link ScheduledRunEntity}. */
public interface ScheduledRunJpaRepository extends JpaRepository<ScheduledRunEntity, String> {

    List<ScheduledRunEntity> findByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            ScheduledRunStatus status, Instant now, Pageable pageable);

    List<ScheduledRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
