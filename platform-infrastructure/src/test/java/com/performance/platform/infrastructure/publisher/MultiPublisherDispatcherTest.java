package com.performance.platform.infrastructure.publisher;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.ports.out.ReportPublisherPort;
import com.performance.platform.domain.event.ReportPublished;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.reporting.PublicationException;
import com.performance.platform.reporting.PublicationTarget;
import com.performance.platform.reporting.ReportEngine;
import com.performance.platform.reporting.ReportPublisher;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.PublisherConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MultiPublisherDispatcher")
class MultiPublisherDispatcherTest {

    // ---------- Fake event publisher (captures events) ----------

    private static final class FakeEventPublisher implements ApplicationEventPublisher {
        final List<Object> events = new CopyOnWriteArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        List<Object> captured() {
            return Collections.unmodifiableList(events);
        }
    }

    // ---------- Fake report engine ----------

    private static final class FakeReportEngine implements ReportEngine {
        private CampaignReport reportToReturn;

        void setReport(CampaignReport report) {
            this.reportToReturn = report;
        }

        @Override
        public CampaignReport generate(ExecutionState state) {
            if (reportToReturn == null) {
                throw new ReportGenerationException("no report configured",
                        new IllegalStateException("stub not set up"));
            }
            return reportToReturn;
        }
    }

    // ---------- Fake execution repository ----------

    private static final class FakeExecutionRepository implements ExecutionRepository {
        private ExecutionState stateToReturn;

        void setState(ExecutionState state) {
            this.stateToReturn = state;
        }

        @Override
        public Optional<ExecutionState> findById(ExecutionId id) {
            return Optional.ofNullable(stateToReturn);
        }

        @Override
        public void save(ExecutionState state) { /* no-op */ }

        @Override
        public void updatePhase(ExecutionId id, Phase phase,
                                com.performance.platform.domain.execution.PhaseStatus status) { /* no-op */ }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId,
                                    AgentId agentId, TaskResult result) { /* no-op */ }

        @Override
        public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
            return Map.of();
        }
    }

    // ---------- Configurable fake publisher ----------

    private static final class FakeReportPublisher implements ReportPublisher {
        private final PublicationTarget target;
        private boolean shouldFail;
        private String failMessage = "publish failed";

        FakeReportPublisher(PublicationTarget target) {
            this.target = target;
        }

        FakeReportPublisher failingWith(String message) {
            this.shouldFail = true;
            this.failMessage = message;
            return this;
        }

        @Override
        public PublicationTarget getTarget() {
            return target;
        }

        @Override
        public void publish(CampaignReport report, PublisherConfig config)
                throws PublicationException {
            if (shouldFail) {
                throw new PublicationException(failMessage);
            }
        }
    }

    // ---------- Stub CampaignReport ----------

    /**
     * Creates a minimal CampaignReport for testing.
     * Uses the real record — defensive copies are verified by the
     * record's compact constructor.
     */
    private static CampaignReport stubReport(ExecutionId executionId) {
        return new CampaignReport(
                ReportId.generate(),
                ScenarioId.of("test-scenario"),
                "test-scenario",
                "1.0",
                List.of(),
                Map.of(),
                new com.performance.platform.reporting.model.EnvironmentInfo(
                        List.of("agent-1"), "Java 25", Map.of()),
                new com.performance.platform.reporting.model.ExecutionSummary(
                        3, 1, 2, 0,
                        java.time.Duration.ofSeconds(5),
                        java.time.Duration.ofSeconds(10),
                        java.time.Duration.ofSeconds(1)),
                List.of(),
                List.of(),
                List.of(),
                com.performance.platform.domain.report.Verdict.SUCCESS,
                "All checks passed",
                Instant.now(),
                java.time.Duration.ofSeconds(15)
        );
    }

    // ---------- Shared state ----------

    private ExecutionId executionId;
    private ReportId reportId;
    private ExecutionState state;
    private CampaignReport report;
    private FakeEventPublisher eventPublisher;
    private FakeReportEngine engine;
    private FakeExecutionRepository executionRepository;
    private PublishersProperties props;
    private FakeReportPublisher confluencePublisher;
    private FakeReportPublisher s3Publisher;
    private FakeReportPublisher gitPublisher;

    @BeforeEach
    void setUp() {
        executionId = ExecutionId.generate();
        reportId = ReportId.generate();
        report = stubReport(executionId);
        state = new ExecutionState(
                executionId,
                ScenarioId.of("test-scenario"),
                ExecutionStatus.COMPLETED,
                Map.of(),
                ExecutionContext.initial(executionId, ScenarioId.of("test-scenario")),
                Instant.now().minusSeconds(60),
                Instant.now());

        eventPublisher = new FakeEventPublisher();
        engine = new FakeReportEngine();
        engine.setReport(report);
        executionRepository = new FakeExecutionRepository();
        executionRepository.setState(state);

        props = new PublishersProperties(List.of(
                new PublisherConfig(PublicationTarget.CONFLUENCE,
                        Map.of("url", "https://wiki.example.com")),
                new PublisherConfig(PublicationTarget.S3,
                        Map.of("bucket", "reports")),
                new PublisherConfig(PublicationTarget.GIT,
                        Map.of("repo", "perf-reports"))
        ));

        confluencePublisher = new FakeReportPublisher(PublicationTarget.CONFLUENCE);
        s3Publisher = new FakeReportPublisher(PublicationTarget.S3);
        gitPublisher = new FakeReportPublisher(PublicationTarget.GIT);
    }

    // -------------------------------------------------------------------
    // Cas nominal
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should dispatch to all configured publishers")
    void shouldDispatchToAllConfiguredPublishers() {
        var dispatcher = new MultiPublisherDispatcher(
                List.of(confluencePublisher, s3Publisher, gitPublisher),
                engine, executionRepository, eventPublisher, props);

        dispatcher.publish(reportId, executionId);

        // All events captured — expects one per publisher
        var captured = eventPublisher.captured();
        assertThat(captured).hasSize(3);
        assertThat(captured).allMatch(e -> e instanceof ReportPublished);
        var targets = captured.stream()
                .map(e -> ((ReportPublished) e).target())
                .toList();
        assertThat(targets).containsExactlyInAnyOrder("CONFLUENCE", "S3", "GIT");
    }

    @Test
    @DisplayName("should use ReportId from port call in published events")
    void shouldUseProvidedReportIdInEvents() {
        var dispatcher = new MultiPublisherDispatcher(
                List.of(confluencePublisher), engine, executionRepository,
                eventPublisher, props);

        dispatcher.publish(reportId, executionId);

        var event = (ReportPublished) eventPublisher.captured().get(0);
        assertThat(event.reportId()).isEqualTo(reportId);
        assertThat(event.executionId()).isEqualTo(executionId);
    }

    // -------------------------------------------------------------------
    // Isolation d'échec
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should continue dispatching when one publisher fails")
    void shouldContinueWhenOnePublisherFails() {
        var failingConfluence = new FakeReportPublisher(PublicationTarget.CONFLUENCE)
                .failingWith("Confluence unavailable");
        var dispatcher = new MultiPublisherDispatcher(
                List.of(failingConfluence, s3Publisher, gitPublisher),
                engine, executionRepository, eventPublisher, props);

        // Should not throw — failures are isolated
        dispatcher.publish(reportId, executionId);

        var targets = eventPublisher.captured().stream()
                .map(e -> ((ReportPublished) e).target())
                .toList();
        assertThat(targets).containsExactlyInAnyOrder("S3", "GIT");
        assertThat(targets).doesNotContain("CONFLUENCE");
    }

    @Test
    @DisplayName("should handle all publishers failing")
    void shouldHandleAllPublishersFailing() {
        var f1 = new FakeReportPublisher(PublicationTarget.CONFLUENCE)
                .failingWith("Confluence down");
        var f2 = new FakeReportPublisher(PublicationTarget.S3)
                .failingWith("S3 down");
        var f3 = new FakeReportPublisher(PublicationTarget.GIT)
                .failingWith("Git down");
        var dispatcher = new MultiPublisherDispatcher(
                List.of(f1, f2, f3), engine, executionRepository,
                eventPublisher, props);

        // Should not throw
        dispatcher.publish(reportId, executionId);

        // No events — all failed
        assertThat(eventPublisher.captured()).isEmpty();
    }

    @Test
    @DisplayName("should handle mixed success and failure")
    void shouldHandleMixedSuccessAndFailure() {
        var f1 = new FakeReportPublisher(PublicationTarget.CONFLUENCE)
                .failingWith("Confluence unavailable");
        var f3 = new FakeReportPublisher(PublicationTarget.GIT)
                .failingWith("Git unavailable");
        var dispatcher = new MultiPublisherDispatcher(
                List.of(f1, s3Publisher, f3),
                engine, executionRepository, eventPublisher, props);

        dispatcher.publish(reportId, executionId);

        var targets = eventPublisher.captured().stream()
                .map(e -> ((ReportPublished) e).target())
                .toList();
        assertThat(targets).containsExactly("S3");
    }

    // -------------------------------------------------------------------
    // Execution non trouvée
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should throw ReportGenerationException when execution not found")
    void shouldThrowWhenExecutionNotFound() {
        var emptyRepo = new FakeExecutionRepository(); // stateToReturn = null
        var dispatcher = new MultiPublisherDispatcher(
                List.of(confluencePublisher), engine, emptyRepo,
                eventPublisher, props);

        assertThatThrownBy(() -> dispatcher.publish(reportId, executionId))
                .isInstanceOf(ReportGenerationException.class)
                .hasMessageContaining(executionId.value());

        assertThat(eventPublisher.captured()).isEmpty();
    }

    // -------------------------------------------------------------------
    // Aucun publisher configuré
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should handle empty publisher list gracefully")
    void shouldHandleEmptyPublisherList() {
        var dispatcher = new MultiPublisherDispatcher(
                List.of(), engine, executionRepository, eventPublisher, props);

        dispatcher.publish(reportId, executionId);

        assertThat(eventPublisher.captured()).isEmpty();
    }

    // -------------------------------------------------------------------
    // Publisher sans config correspondante
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should skip publisher without matching config")
    void shouldSkipPublisherWithoutConfig() {
        var s3OnlyProps = new PublishersProperties(List.of(
                new PublisherConfig(PublicationTarget.S3,
                        Map.of("bucket", "reports"))));
        var dispatcher = new MultiPublisherDispatcher(
                List.of(confluencePublisher, s3Publisher),
                engine, executionRepository, eventPublisher, s3OnlyProps);

        dispatcher.publish(reportId, executionId);

        // Only S3 has a config → only S3 should publish
        var targets = eventPublisher.captured().stream()
                .map(e -> ((ReportPublished) e).target())
                .toList();
        assertThat(targets).containsExactly("S3");
    }

    // -------------------------------------------------------------------
    // Immutabilité
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should defensively copy the publisher list")
    void shouldCopyPublisherList() {
        List<ReportPublisher> mutableList = new ArrayList<>(List.of(confluencePublisher));
        var dispatcher = new MultiPublisherDispatcher(
                mutableList, engine, executionRepository, eventPublisher, props);

        mutableList.add(s3Publisher); // mutate after construction

        dispatcher.publish(reportId, executionId);

        // Only the original publisher should have generated an event
        var targets = eventPublisher.captured().stream()
                .map(e -> ((ReportPublished) e).target())
                .toList();
        assertThat(targets).containsExactly("CONFLUENCE");
    }

    // -------------------------------------------------------------------
    // PublishersProperties
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("PublishersProperties")
    class PublishersPropertiesTest {

        @Test
        @DisplayName("should find config for matching target")
        void shouldFindConfigForMatchingTarget() {
            assertThat(props.forTarget(PublicationTarget.CONFLUENCE)).isPresent();
            assertThat(props.forTarget(PublicationTarget.S3)).isPresent();
            assertThat(props.forTarget(PublicationTarget.GIT)).isPresent();
            assertThat(props.forTarget(PublicationTarget.SHAREPOINT)).isEmpty();
        }

        @Test
        @DisplayName("should handle null publishers list")
        void shouldHandleNullPublishersList() {
            var empty = new PublishersProperties(null);
            assertThat(empty.publishers()).isEmpty();
            assertThat(empty.forTarget(PublicationTarget.CONFLUENCE)).isEmpty();
        }
    }
}
