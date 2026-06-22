package com.performance.platform.infrastructure.publisher.s3;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("S3ReportPublisher")
class S3ReportPublisherTest {

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_REGION = "us-east-1";

    private WireMockServer wireMock;
    private S3ReportPublisher publisher;
    private CampaignReport report;
    private PublisherConfig validConfig;
    private String endpoint;

    private final S3ReportPublisher.AwsCredentials credentials =
            new S3ReportPublisher.AwsCredentials(
                    "AKIAIOSFODNN7EXAMPLE",
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    null);

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        endpoint = "localhost:" + wireMock.port();
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        publisher = new S3ReportPublisher(httpClient, credentials, endpoint);

        validConfig = new PublisherConfig(PublicationTarget.S3, Map.of(
                S3ReportPublisher.KEY_BUCKET, TEST_BUCKET,
                S3ReportPublisher.KEY_REGION, TEST_REGION,
                S3ReportPublisher.KEY_PREFIX, "reports/test-run"
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
    @DisplayName("should upload JSON and HTML reports to S3")
    void shouldUploadJsonAndHtmlToS3() {
        // Stub S3 PUT for any object
        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(200)));

        publisher.publish(report, validConfig);

        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/report.json")));
        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/report.html")));
    }

    @Test
    @DisplayName("should return S3 target")
    void shouldReturnS3Target() {
        assertThat(publisher.getTarget()).isEqualTo(PublicationTarget.S3);
    }

    @Test
    @DisplayName("should handle 200 OK response")
    void shouldHandle200Response() {
        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(200)));

        // Should not throw
        publisher.publish(report, validConfig);
    }

    @Test
    @DisplayName("should include AWS Signature V4 Authorization header")
    void shouldIncludeAwsSignatureHeader() {
        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(200)));

        publisher.publish(report, validConfig);

        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/report.json"))
                .withHeader("Authorization",
                        com.github.tomakehurst.wiremock.client.WireMock
                                .containing("AWS4-HMAC-SHA256"))
                .withHeader("x-amz-content-sha256",
                        com.github.tomakehurst.wiremock.client.WireMock
                                .matching("[a-f0-9]{64}"))
                .withHeader("x-amz-date",
                        com.github.tomakehurst.wiremock.client.WireMock
                                .matching("\\d{8}T\\d{6}Z")));
    }

    @Test
    @DisplayName("should include session token header when present")
    void shouldIncludeSessionTokenHeader() {
        var sessionCreds = new S3ReportPublisher.AwsCredentials(
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "FQoGZXIvYXdzENr...");
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var sessionPublisher = new S3ReportPublisher(httpClient, sessionCreds, endpoint);

        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(200)));

        sessionPublisher.publish(report, validConfig);

        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/report.json"))
                .withHeader("x-amz-security-token",
                        com.github.tomakehurst.wiremock.client.WireMock
                                .containing("FQoGZXIvYXdzENr")));
    }

    // -------------------------------------------------------------------
    // Upload avec fichiers Gatling
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should upload Gatling simulation logs from output directory")
    void shouldUploadGatlingSimulationLogs(@TempDir Path tempDir) throws Exception {
        // Create a Gatling-like output directory
        Path gatlingDir = tempDir.resolve("gatling-output");
        Files.createDirectory(gatlingDir);
        Files.writeString(gatlingDir.resolve("simulation.log"), "RUN\tcom.example.Test\ttest-run\n");
        Files.writeString(gatlingDir.resolve("index.html"), "<html>Gatling report</html>");

        var taskId = TaskId.of("inj-1");
        var injectionResult = new InjectionResult(taskId, "com.example.Test",
                Duration.ofSeconds(5), 100, 98, 2, 2.0, 20.0,
                10, 20, 30, 50, 100, 200, 5, 15.0,
                gatlingDir, Map.of());
        var reportWithGatling = new CampaignReport(
                ReportId.generate(), ScenarioId.of("sc"), "sc", "1.0",
                List.of(), Map.of(),
                new EnvironmentInfo(List.of("a1"), "Java 21", Map.of()),
                new ExecutionSummary(1, 1, 0, 0,
                        Duration.ZERO, Duration.ofSeconds(5), Duration.ZERO),
                List.of(),
                List.of(new InjectionReportEntry(taskId, injectionResult, gatlingDir)),
                List.of(),
                Verdict.SUCCESS, "ok", Instant.now(), Duration.ofSeconds(6));

        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(200)));

        publisher.publish(reportWithGatling, validConfig);

        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/gatling/simulation.log")));
        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/gatling/index.html")));
    }

    @Test
    @DisplayName("should skip Gatling upload when output directory is missing")
    void shouldSkipGatlingUploadWhenDirMissing() {
        var taskId = TaskId.of("inj-1");
        var missingDir = Path.of("/nonexistent/gatling");
        var injectionResult = new InjectionResult(taskId, "com.example.Test",
                Duration.ofSeconds(5), 100, 98, 2, 2.0, 20.0,
                10, 20, 30, 50, 100, 200, 5, 15.0,
                missingDir, Map.of());
        var reportWithMissingGatling = new CampaignReport(
                ReportId.generate(), ScenarioId.of("sc"), "sc", "1.0",
                List.of(), Map.of(),
                new EnvironmentInfo(List.of("a1"), "Java 21", Map.of()),
                new ExecutionSummary(1, 1, 0, 0,
                        Duration.ZERO, Duration.ofSeconds(5), Duration.ZERO),
                List.of(),
                List.of(new InjectionReportEntry(taskId, injectionResult, missingDir)),
                List.of(),
                Verdict.SUCCESS, "ok", Instant.now(), Duration.ofSeconds(6));

        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(200)));

        // Should not throw — Gatling dir is silently skipped
        publisher.publish(reportWithMissingGatling, validConfig);

        // Only report.json and report.html uploaded, no gatling files
        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/report.json")));
        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/reports/test-run/report.html")));
    }

    // -------------------------------------------------------------------
    // Configuration manquante
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should throw when bucket is missing")
    void shouldThrowWhenBucketMissing() {
        var config = new PublisherConfig(PublicationTarget.S3, Map.of(
                S3ReportPublisher.KEY_REGION, TEST_REGION));

        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    @DisplayName("should throw when region is missing")
    void shouldThrowWhenRegionMissing() {
        var config = new PublisherConfig(PublicationTarget.S3, Map.of(
                S3ReportPublisher.KEY_BUCKET, TEST_BUCKET));

        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("region");
    }

    @Test
    @DisplayName("should use report id as default prefix when not configured")
    void shouldUseReportIdAsDefaultPrefix() {
        var configWithoutPrefix = new PublisherConfig(PublicationTarget.S3, Map.of(
                S3ReportPublisher.KEY_BUCKET, TEST_BUCKET,
                S3ReportPublisher.KEY_REGION, TEST_REGION));

        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/" + report.id().value() + "/.*"))
                .willReturn(aResponse().withStatus(200)));

        publisher.publish(report, configWithoutPrefix);

        wireMock.verify(putRequestedFor(
                urlEqualTo("/" + TEST_BUCKET + "/" + report.id().value() + "/report.json")));
    }

    // -------------------------------------------------------------------
    // Erreurs S3
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should throw PublicationException on HTTP 403")
    void shouldThrowOnForbidden() {
        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(403)
                        .withBody("<?xml version=\"1.0\"?><Error><Code>AccessDenied</Code>"
                                + "<Message>Access Denied</Message></Error>")));

        assertThatThrownBy(() -> publisher.publish(report, validConfig))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("S3 upload failed");
    }

    @Test
    @DisplayName("should throw PublicationException on HTTP 404")
    void shouldThrowOnNotFound() {
        wireMock.stubFor(put(urlMatching("/" + TEST_BUCKET + "/reports/test-run/.*"))
                .willReturn(aResponse().withStatus(404)
                        .withBody("<?xml version=\"1.0\"?><Error><Code>NoSuchBucket</Code>"
                                + "<Message>Bucket does not exist</Message></Error>")));

        assertThatThrownBy(() -> publisher.publish(report, validConfig))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("S3 upload failed");
    }

    // -------------------------------------------------------------------
    // Construction HTML
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should build valid HTML report")
    void shouldBuildValidHtmlReport() {
        var html = S3ReportPublisher.buildHtmlReport(report);

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("<title>Performance Report: test-scenario</title>");
        assertThat(html).contains("<h2>Execution Summary</h2>");
        assertThat(html).contains("<h2>Verdict</h2>");
        assertThat(html).contains("SUCCESS");
        assertThat(html).contains("prepare-db");
        assertThat(html).contains("com.example.LoadTest");
        assertThat(html).contains("response time");
        assertThat(html).contains("<h2>Environment</h2>");
        assertThat(html).contains("Java 21");
    }

    @Test
    @DisplayName("should handle empty preparation/injection/assertion lists in HTML")
    void shouldHandleEmptySectionsInHtml() {
        var minimalReport = new CampaignReport(
                ReportId.generate(), ScenarioId.of("min"), "min", "1.0",
                List.of(), Map.of(),
                new EnvironmentInfo(List.of("a1"), "Java 21", Map.of()),
                new ExecutionSummary(0, 0, 0, 0,
                        Duration.ZERO, Duration.ZERO, Duration.ZERO),
                List.of(), List.of(), List.of(),
                Verdict.FAILED, "minimal", Instant.now(), Duration.ofSeconds(1));

        var html = S3ReportPublisher.buildHtmlReport(minimalReport);

        assertThat(html).doesNotContain("<h2>Preparation</h2>");
        assertThat(html).doesNotContain("<h2>Injection</h2>");
        assertThat(html).doesNotContain("<h2>Assertions</h2>");
        assertThat(html).contains("FAILED");
    }

    // -------------------------------------------------------------------
    // JSON serialisation
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should serialize report to valid JSON")
    void shouldSerializeToValidJson() throws Exception {
        byte[] jsonBytes = publisher.serializeToJson(report);

        var json = new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("\"scenarioName\"");
        assertThat(json).contains("\"test-scenario\"");
        assertThat(json).contains("\"verdict\"");
        assertThat(json).contains("\"SUCCESS\"");
    }

    // -------------------------------------------------------------------
    // AWS Signature V4
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should produce correct AWS Signature V4 Authorization header")
    void shouldProduceAwsSigV4Header() {
        var request = java.net.http.HttpRequest.newBuilder()
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString("hello"))
                .uri(java.net.URI.create("http://" + TEST_BUCKET
                        + ".s3." + TEST_REGION + ".amazonaws.com/test-key"))
                .build();
        byte[] payload = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var now = Instant.parse("2025-01-15T10:30:00Z");

        var auth = S3ReportPublisher.awsSign(request,
                TEST_BUCKET + ".s3." + TEST_REGION + ".amazonaws.com",
                TEST_REGION, payload, now, credentials);

        assertThat(auth).startsWith("AWS4-HMAC-SHA256");
        assertThat(auth).contains("Credential=AKIAIOSFODNN7EXAMPLE/20250115/"
                + TEST_REGION + "/s3/aws4_request");
        assertThat(auth).contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date");
        assertThat(auth).contains("Signature=");
    }

    @Test
    @DisplayName("should produce consistent SHA-256 hex")
    void shouldHashConsistently() {
        byte[] data = "test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] hash = S3ReportPublisher.hash(data);

        // SHA-256 of "test" = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        assertThat(S3ReportPublisher.toHex(hash))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    @DisplayName("should compute signing key deterministically")
    void shouldComputeSigningKey() {
        byte[] key = S3ReportPublisher.awsSigningKey(
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "20250115", TEST_REGION, "s3");

        assertThat(key).isNotNull();
        assertThat(key.length).isEqualTo(32); // HMAC-SHA256 = 32 bytes
    }

    @Test
    @DisplayName("should URI-encode S3 key paths")
    void shouldUriEncodePaths() {
        assertThat(S3ReportPublisher.uriEncodePath("/test-bucket/my report.json"))
                .isEqualTo("/test-bucket/my%20report.json");
        assertThat(S3ReportPublisher.uriEncodePath("/simple/path"))
                .isEqualTo("/simple/path");
        assertThat(S3ReportPublisher.uriEncodePath("/"))
                .isEqualTo("/");
    }

    @Test
    @DisplayName("should choose path-style for custom endpoints")
    void shouldUsePathStyleForCustomEndpoints() {
        var path = S3ReportPublisher.s3Path("my-bucket", "prefix/report.json",
                "localhost:12345");
        assertThat(path).isEqualTo("/my-bucket/prefix/report.json");
    }

    @Test
    @DisplayName("should choose virtual-hosted-style for real S3")
    void shouldUseVirtualHostedStyleForRealS3() {
        var path = S3ReportPublisher.s3Path("my-bucket", "prefix/report.json",
                "my-bucket.s3.us-east-1.amazonaws.com");
        assertThat(path).isEqualTo("/prefix/report.json");
    }

    // -------------------------------------------------------------------
    // Credentials
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should reject credentials with blank access key")
    void shouldRejectBlankAccessKey() {
        assertThatThrownBy(() -> new S3ReportPublisher.AwsCredentials("", "secret", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKeyId");
    }

    @Test
    @DisplayName("should reject credentials with blank secret key")
    void shouldRejectBlankSecretKey() {
        assertThatThrownBy(() -> new S3ReportPublisher.AwsCredentials("AKID", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretAccessKey");
    }

    @Test
    @DisplayName("should accept credentials with null session token")
    void shouldAcceptNullSessionToken() {
        var creds = new S3ReportPublisher.AwsCredentials("AKID", "secret", null);
        assertThat(creds.sessionToken()).isNull();
    }

    @Test
    @DisplayName("should throw when AWS env vars are not set")
    void shouldThrowWhenAwsEnvVarsNotSet() {
        // Uses the no-arg constructor which calls resolveCredentials().
        // This test requires AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
        // to NOT be set in the test environment.
        assertThatThrownBy(S3ReportPublisher::new)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWS credentials not found");
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
