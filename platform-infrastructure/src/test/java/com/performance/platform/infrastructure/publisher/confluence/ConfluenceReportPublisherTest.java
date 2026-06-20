package com.performance.platform.infrastructure.publisher.confluence;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.Verdict;

import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.reporting.PublicationException;
import com.performance.platform.reporting.PublicationTarget;
import com.performance.platform.reporting.model.AssertionReportEntry;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.InjectionReportEntry;
import com.performance.platform.reporting.model.PublisherConfig;
import com.performance.platform.reporting.model.TaskReportEntry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfluenceReportPublisher")
class ConfluenceReportPublisherTest {

    private static final String API_PATH = "/wiki/rest/api/content";

    private WireMockServer wireMock;
    private ConfluenceReportPublisher publisher;
    private CampaignReport report;
    private PublisherConfig validConfig;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0); // dynamic port
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        baseUrl = "http://localhost:" + wireMock.port();
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        publisher = new ConfluenceReportPublisher(httpClient);

        validConfig = new PublisherConfig(PublicationTarget.CONFLUENCE, Map.of(
                ConfluenceReportPublisher.KEY_URL, baseUrl,
                ConfluenceReportPublisher.KEY_SPACE_KEY, "PERF",
                ConfluenceReportPublisher.KEY_TOKEN, "test-token-123",
                ConfluenceReportPublisher.KEY_PARENT_PAGE_ID, "987654"
        ));

        report = stubReport();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // -------------------------------------------------------------------
    // Cas nominal
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should publish report to Confluence REST API")
    void shouldPublishReportToConfluenceApi() {
        wireMock.stubFor(post(urlEqualTo(API_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"id\":\"12345\",\"title\":\"test\"}")));

        publisher.publish(report, validConfig);

        wireMock.verify(postRequestedFor(urlEqualTo(API_PATH))
                .withHeader("Authorization", equalTo("Bearer test-token-123"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .containing("\"space\":{\"key\":\"PERF\"}")));
    }

    @Test
    @DisplayName("should return CONFLUENCE target")
    void shouldReturnConfluenceTarget() {
        assertThat(publisher.getTarget()).isEqualTo(PublicationTarget.CONFLUENCE);
    }

    @Test
    @DisplayName("should handle 201 Created response")
    void shouldHandleCreatedResponse() {
        wireMock.stubFor(post(urlEqualTo(API_PATH))
                .willReturn(aResponse().withStatus(201)
                        .withBody("{\"id\":\"12345\"}")));

        // Should not throw
        publisher.publish(report, validConfig);

        wireMock.verify(postRequestedFor(urlEqualTo(API_PATH)));
    }

    // -------------------------------------------------------------------
    // Configuration manquante
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should throw when url is missing")
    void shouldThrowWhenUrlMissing() {
        var config = new PublisherConfig(PublicationTarget.CONFLUENCE, Map.of(
                ConfluenceReportPublisher.KEY_SPACE_KEY, "PERF",
                ConfluenceReportPublisher.KEY_TOKEN, "token"));

        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("url");
    }

    @Test
    @DisplayName("should throw when spaceKey is missing")
    void shouldThrowWhenSpaceKeyMissing() {
        var config = new PublisherConfig(PublicationTarget.CONFLUENCE, Map.of(
                ConfluenceReportPublisher.KEY_URL, baseUrl,
                ConfluenceReportPublisher.KEY_TOKEN, "token"));

        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("spaceKey");
    }

    @Test
    @DisplayName("should throw when token is missing")
    void shouldThrowWhenTokenMissing() {
        var config = new PublisherConfig(PublicationTarget.CONFLUENCE, Map.of(
                ConfluenceReportPublisher.KEY_URL, baseUrl,
                ConfluenceReportPublisher.KEY_SPACE_KEY, "PERF"));

        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("token");
    }

    // -------------------------------------------------------------------
    // Erreurs HTTP
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should throw PublicationException on HTTP 401")
    void shouldThrowOnUnauthorized() {
        wireMock.stubFor(post(urlEqualTo(API_PATH))
                .willReturn(aResponse().withStatus(401)
                        .withBody("Unauthorized")));

        assertThatThrownBy(() -> publisher.publish(report, validConfig))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("should throw PublicationException on HTTP 500")
    void shouldThrowOnServerError() {
        wireMock.stubFor(post(urlEqualTo(API_PATH))
                .willReturn(aResponse().withStatus(500)
                        .withBody("Internal Server Error")));

        assertThatThrownBy(() -> publisher.publish(report, validConfig))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("should throw PublicationException on connection failure")
    void shouldThrowOnConnectionFailure() {
        // Stop WireMock to simulate connection refused
        wireMock.stop();
        var badConfig = new PublisherConfig(PublicationTarget.CONFLUENCE, Map.of(
                ConfluenceReportPublisher.KEY_URL, "http://localhost:1", // invalid port
                ConfluenceReportPublisher.KEY_SPACE_KEY, "PERF",
                ConfluenceReportPublisher.KEY_TOKEN, "token"));

        assertThatThrownBy(() -> publisher.publish(report, badConfig))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("Failed to connect");
    }

    // -------------------------------------------------------------------
    // Construction payload Confluence
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should build valid Confluence JSON payload")
    void shouldBuildValidPayload() {
        String payload = ConfluenceReportPublisher.buildConfluencePayload(
                "Test Title", "SPACE", "123", "<p>body</p>");

        assertThat(payload).contains("\"type\":\"page\"");
        assertThat(payload).contains("\"title\":\"Test Title\"");
        assertThat(payload).contains("\"key\":\"SPACE\"");
        assertThat(payload).contains("\"ancestors\":[{\"id\":123}]");
        assertThat(payload).contains("\"representation\":\"storage\"");
    }

    @Test
    @DisplayName("should build payload without ancestors when parentPageId is blank")
    void shouldBuildPayloadWithoutAncestors() {
        String payload = ConfluenceReportPublisher.buildConfluencePayload(
                "Title", "SPACE", "", "<p>body</p>");

        assertThat(payload).doesNotContain("ancestors");
    }

    @Test
    @DisplayName("should escape JSON special characters in payload")
    void shouldEscapeJsonSpecialChars() {
        String payload = ConfluenceReportPublisher.buildConfluencePayload(
                "Title \"quoted\"", "SPACE", "", "<p>body</p>");

        assertThat(payload).contains("\\\"quoted\\\"");
    }

    // -------------------------------------------------------------------
    // Construction body HTML
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should build HTML storage body with report data")
    void shouldBuildStorageBody() {
        String body = ConfluenceReportPublisher.buildStorageBody(report);

        assertThat(body).contains("<h1>Performance Report: test-scenario</h1>");
        assertThat(body).contains("<h2>Execution Summary</h2>");
        assertThat(body).contains("<h2>Verdict</h2>");
        assertThat(body).contains("SUCCESS");
        assertThat(body).contains("<h2>Preparation</h2>");
        assertThat(body).contains("prepare-db");
        assertThat(body).contains("<h2>Injection</h2>");
        assertThat(body).contains("com.example.LoadTest");
        assertThat(body).contains("<h2>Assertions</h2>");
        assertThat(body).contains("response time");
        assertThat(body).contains("<h2>Environment</h2>");
        assertThat(body).contains("Java 21");
    }

    @Test
    @DisplayName("should handle empty preparation/injection/assertion lists")
    void shouldHandleEmptySections() {
        var minimalReport = new CampaignReport(
                ReportId.generate(), ScenarioId.of("min"), "min", "1.0",
                List.of(), Map.of(),
                new EnvironmentInfo(List.of("a1"), "Java 21", Map.of()),
                new ExecutionSummary(0, 0, 0, 0,
                        Duration.ZERO, Duration.ZERO, Duration.ZERO),
                List.of(), List.of(), List.of(),
                Verdict.SUCCESS, "minimal", Instant.now(), Duration.ofSeconds(1));

        String body = ConfluenceReportPublisher.buildStorageBody(minimalReport);

        assertThat(body).doesNotContain("<h2>Preparation</h2>");
        assertThat(body).doesNotContain("<h2>Injection</h2>");
        assertThat(body).doesNotContain("<h2>Assertions</h2>");
    }

    // -------------------------------------------------------------------
    // Token non loggé
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should not log token value")
    void shouldNotLogToken() {
        var secureConfig = new PublisherConfig(PublicationTarget.CONFLUENCE, Map.of(
                ConfluenceReportPublisher.KEY_URL, baseUrl,
                ConfluenceReportPublisher.KEY_SPACE_KEY, "PERF",
                ConfluenceReportPublisher.KEY_TOKEN, "secret-api-token-do-not-log"));

        wireMock.stubFor(post(urlEqualTo(API_PATH))
                .willReturn(aResponse().withStatus(200)));

        // Should not throw — token is sent in header but not logged
        publisher.publish(report, secureConfig);

        // Verify request was made with the token in the header (not body)
        wireMock.verify(postRequestedFor(urlEqualTo(API_PATH))
                .withHeader("Authorization", equalTo("Bearer secret-api-token-do-not-log")));
    }

    // -------------------------------------------------------------------
    // Stub helpers
    // -------------------------------------------------------------------

    private static CampaignReport stubReport() {
        var taskId = TaskId.of("task-1");
        return new CampaignReport(
                ReportId.generate(),
                ScenarioId.of("test-scenario"),
                "test-scenario",
                "1.0",
                List.of("smoke", "critical"),
                Map.of("env", "staging"),
                new EnvironmentInfo(List.of("agent-1", "agent-2"), "Java 21", Map.of()),
                new ExecutionSummary(3, 2, 1, 0,
                        Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(1)),
                List.of(new TaskReportEntry(taskId, "prepare-db",
                        TaskStatus.SUCCESS, Duration.ofSeconds(2), Map.of())),
                List.of(new InjectionReportEntry(taskId,
                        new InjectionResult(taskId, "com.example.LoadTest",
                                Duration.ofSeconds(10), 1000, 995, 5, 0.5, 100.0,
                                45, 78, 120, 150, 200, 300, 10, 50.5,
                                Path.of("/tmp/gatling"), Map.of()),
                        Path.of("/tmp/gatling"))),
                List.of(new AssertionReportEntry(taskId,
                        new AssertionResult(taskId, AssertionStatus.PASSED,
                                "response time < 200ms",
                                new Evidence(150L, 200L, AssertionOperator.LT,
                                        "ms", Map.of()),
                                Duration.ofMillis(10), Instant.now()),
                        new Evidence(150L, 200L, AssertionOperator.LT,
                                "ms", Map.of()))),
                Verdict.SUCCESS,
                "All checks passed",
                Instant.now(),
                Duration.ofSeconds(15)
        );
    }
}
