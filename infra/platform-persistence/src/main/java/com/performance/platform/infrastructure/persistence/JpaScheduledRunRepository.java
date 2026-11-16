package com.performance.platform.infrastructure.persistence;

import com.performance.platform.application.ports.out.ScheduledRunRepository;
import com.performance.platform.domain.id.ScheduledRunId;
import com.performance.platform.application.schedule.ScheduledRun;
import com.performance.platform.application.schedule.ScheduledRunStatus;
import com.performance.platform.infrastructure.persistence.mapper.ScheduledRunMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter of the {@link ScheduledRunRepository} port.
 * Conditional on a {@link DataSource} — no scheduled runs in AGENT mode.
 */
@Repository
@ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
public class JpaScheduledRunRepository implements ScheduledRunRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaScheduledRunRepository.class);

    private final ScheduledRunJpaRepository repository;
    private final ScheduledRunMapper mapper;

    public JpaScheduledRunRepository(ScheduledRunJpaRepository repository, ScheduledRunMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(ScheduledRun run) {
        log.info("action=save_scheduled_run scheduleId={} status={} nextRunAt={}",
                run.id().value(), run.status(), run.nextRunAt());
        repository.save(mapper.toEntity(run));
    }

    @Override
    public Optional<ScheduledRun> findById(ScheduledRunId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<ScheduledRun> findDue(Instant now, int limit) {
        return repository
                .findByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                        ScheduledRunStatus.PENDING, now, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ScheduledRun> findRecent(int limit) {
        int bounded = limit <= 0 ? 50 : limit;
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, bounded))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
