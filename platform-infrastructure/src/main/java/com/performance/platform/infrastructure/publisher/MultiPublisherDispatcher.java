package com.performance.platform.infrastructure.publisher;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.ports.out.ReportPublisherPort;
import com.performance.platform.domain.event.ReportPublished;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.reporting.PublicationException;
import com.performance.platform.reporting.ReportEngine;
import com.performance.platform.reporting.ReportPublisher;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.PublisherConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Dispatches a generated {@link CampaignReport} to every configured
 * {@link ReportPublisher}. Implements the {@link ReportPublisherPort} driven port.
 *
 * <p><b>Failure isolation:</b> if one publisher fails with a
 * {@link PublicationException}, the error is logged and the remaining
 * publishers continue. A {@link ReportPublished} event is emitted for
 * each successful publication.</p>
 *
 * <p>All blocking I/O (generation + HTTP/RPC publication calls) executes
 * under Virtual Threads configured by the caller.</p>
 */
@Component
public class MultiPublisherDispatcher implements ReportPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MultiPublisherDispatcher.class);

    private final List<ReportPublisher> publishers;
    private final ReportEngine engine;
    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PublishersProperties props;

    /**
     * Constructor injection.
     *
     * @param publishers          all {@link ReportPublisher} beans discovered by Spring
     * @param engine              the report generation engine
     * @param executionRepository repository to load {@code ExecutionState} by id
     * @param eventPublisher      Spring application event publisher
     * @param props               publisher configuration properties
     */
    public MultiPublisherDispatcher(List<ReportPublisher> publishers,
                                     ReportEngine engine,
                                     ExecutionRepository executionRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     PublishersProperties props) {
        this.publishers = List.copyOf(publishers);
        this.engine = engine;
        this.executionRepository = executionRepository;
        this.eventPublisher = eventPublisher;
        this.props = props;
    }

    /**
     * Generates the campaign report and dispatches it to every configured
     * {@link ReportPublisher}. Failures are isolated per publisher.
     *
     * @param reportId    the report identifier
     * @param executionId the execution identifier used to load state
     */
    @Override
    public void publish(ReportId reportId, ExecutionId executionId) {
        log.info("action=publish_start reportId={} executionId={} publisherCount={}",
                reportId.value(), executionId.value(), publishers.size());

        var state = executionRepository.findById(executionId)
                .orElseThrow(() -> new ReportGenerationException(
                        "Execution not found: " + executionId.value(),
                        new IllegalStateException("No ExecutionState for " + executionId.value())));

        CampaignReport report = engine.generate(state);

        int successCount = 0;
        int failureCount = 0;

        for (ReportPublisher publisher : publishers) {
            var target = publisher.getTarget();
            var configOpt = props.forTarget(target);

            if (configOpt.isEmpty()) {
                log.warn("action=no_publisher_config executionId={} target={}",
                        executionId.value(), target);
                continue;
            }

            PublisherConfig config = configOpt.get();

            try {
                publisher.publish(report, config);
                eventPublisher.publishEvent(new ReportPublished(
                        executionId, reportId, target.name(), Instant.now()));
                successCount++;
                log.info("action=report_published executionId={} reportId={} target={}",
                        executionId.value(), reportId.value(), target);
            } catch (PublicationException e) {
                failureCount++;
                log.warn("action=publish_failed executionId={} reportId={} target={} error={}",
                        executionId.value(), reportId.value(), target, e.getMessage());
            }
        }

        log.info("action=publish_complete executionId={} reportId={} success={} failure={}",
                executionId.value(), reportId.value(), successCount, failureCount);
    }
}
