package com.performance.platform.infrastructure.publisher.s3;

import com.performance.platform.reporting.PublicationException;
import com.performance.platform.reporting.PublicationTarget;
import com.performance.platform.reporting.ReportPublisher;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.EnvironmentInfo;
import com.performance.platform.reporting.model.ExecutionSummary;
import com.performance.platform.reporting.model.PublisherConfig;
import com.performance.platform.reporting.model.TaskReportEntry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Publishes the campaign report to an S3 bucket.
 *
 * <p>Uploads three artefacts per report:
 * <ol>
 *   <li>{@code report.json} — full {@link CampaignReport} serialised as JSON</li>
 *   <li>{@code report.html} — standalone HTML summary</li>
 *   <li>Gatling simulation logs — {@code gatling/*} from injection output
 *       directories referenced in the report</li>
 * </ol>
 *
 * <p>Configuration is read from {@link PublisherConfig#properties()}:
 * <ul>
 *   <li>{@code bucket} — S3 bucket name (required)</li>
 *   <li>{@code region} — AWS region, e.g. {@code us-east-1} (required)</li>
 *   <li>{@code prefix} — S3 key prefix / folder (optional, defaults to
 *       the report id)</li>
 * </ul>
 *
 * <p>Credentials are resolved from the standard AWS environment-variable
 * chain: {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY}, and
 * optionally {@code AWS_SESSION_TOKEN} for temporary credentials.
 * These values are never logged.</p>
 *
 * <p>All HTTP I/O executes under Virtual Threads
 * ({@link HttpClient} with non-blocking I/O underneath).</p>
 *
 * <p><b>CC-02:</b> Pipeline cohesif de publication S3 —
 * validation configuration → calcul prefixe → upload JSON
 * ({@link #putObject}) → upload HTML ({@link #putObject}) →
 * upload logs Gatling ({@link #uploadGatlingOutputs}). Les helpers
 * de signature AWS ({@link #awsSign}, {@link #awsSigningKey},
 * {@link #hash}, {@link #toHex}) et de construction HTML
 * ({@link #buildHtmlReport}) sont indissociables du flux de
 * publication S3. Extraire une portion isolee nuirait a la
 * lisibilite du pipeline sequentiel.</p>
 *
 * <p><b>Activation:</b> Ce publisher est enregistre comme {@code @Component}
 * sans {@code @ConditionalOnProperty}. L'activation/desactivation est
 * deleguee au {@code MultiPublisherDispatcher} qui filtre par
 * {@code reporting.publishers.*} dans la configuration.</p>
 */
@Component
public class S3ReportPublisher implements ReportPublisher {

    private static final Logger log = LoggerFactory.getLogger(S3ReportPublisher.class);

    private static final String S3_SERVICE = "s3";
    private static final String SIGNING_ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String AWS_ACCESS_KEY_ID_ENV = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_ACCESS_KEY_ENV = "AWS_SECRET_ACCESS_KEY";
    private static final String AWS_SESSION_TOKEN_ENV = "AWS_SESSION_TOKEN";
    private static final DateTimeFormatter AWS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AWS_DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /** Configuration property keys (package-visible for test references). */
    static final String KEY_BUCKET = "bucket";
    static final String KEY_REGION = "region";
    static final String KEY_PREFIX = "prefix";

    private final HttpClient httpClient;
    private final AwsCredentials credentials;
    private final ObjectMapper objectMapper;
    private final String endpointOverride;

    /** Public no-arg constructor for Spring. */
    public S3ReportPublisher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.credentials = resolveCredentials();
        this.objectMapper = buildObjectMapper();
        this.endpointOverride = null;
    }

    /**
     * Package-visible constructor for testing with custom HTTP client,
     * credentials, and an optional S3 endpoint override
     * (e.g. {@code "localhost:12345"} for WireMock).
     */
    S3ReportPublisher(HttpClient httpClient, AwsCredentials credentials) {
        this(httpClient, credentials, null);
    }

    /**
     * Package-visible constructor with endpoint override for integration tests.
     */
    S3ReportPublisher(HttpClient httpClient, AwsCredentials credentials,
                      String endpointOverride) {
        this.httpClient = httpClient;
        this.credentials = credentials;
        this.objectMapper = buildObjectMapper();
        this.endpointOverride = endpointOverride;
    }

    @Override
    public PublicationTarget getTarget() {
        return PublicationTarget.S3;
    }

    /**
     * Publishes the campaign report to S3.
     * Validates required configuration, serialises the report to JSON,
     * builds an HTML summary, uploads both to S3, and copies Gatling
     * simulation logs.
     *
     * <p><b>CC-02:</b> Pipeline cohesif — validation config
     * ({@link #requireProperty}) → calcul prefixe → upload JSON
     * ({@link #putObject}) → upload HTML ({@link #putObject}) →
     * upload logs Gatling ({@link #uploadGatlingOutputs}). Chaque
     * etape est inseparable du flux de publication S3.</p>
     *
     * @param report the campaign report to publish
     * @param config publisher configuration (bucket, region, prefix)
     * @throws PublicationException if S3 upload fails or config is invalid
     */
    @Override
    public void publish(CampaignReport report, PublisherConfig config)
            throws PublicationException {
        Map<String, String> props = config.properties();

        String bucket = requireProperty(props, KEY_BUCKET);
        String region = requireProperty(props, KEY_REGION);
        String prefix = props.getOrDefault(KEY_PREFIX, report.id().value());

        String host = resolveHost(bucket, region);

        log.info("action=s3_publish_start executionId={} bucket={} region={} prefix={}",
                report.id().value(), bucket, region, prefix);

        try {
            // 1. Upload JSON report
            byte[] jsonPayload = serializeToJson(report);
            putObject(host, region, bucket, prefix + "/report.json",
                    "application/json", jsonPayload, report.id().value());

            // 2. Upload HTML report
            String htmlBody = buildHtmlReport(report);
            byte[] htmlPayload = htmlBody.getBytes(StandardCharsets.UTF_8);
            putObject(host, region, bucket, prefix + "/report.html",
                    "text/html; charset=utf-8", htmlPayload, report.id().value());

            // 3. Upload Gatling simulation logs
            uploadGatlingOutputs(host, region, bucket, prefix, report);

            log.info("action=s3_publish_success executionId={} prefix={}",
                    report.id().value(), prefix);
        } catch (IOException e) {
            log.error("action=s3_publish_io_error executionId={} error={}",
                    report.id().value(), e.getMessage());
            throw new PublicationException(
                    "Failed to upload to S3: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublicationException("S3 publish interrupted", e);
        }
    }

    // ---- internal helpers ------------------------------------------------

    /**
     * Extracts a required property, throwing {@link PublicationException}
     * with a clear message if missing.
     */
    private static String requireProperty(Map<String, String> props, String key) {
        String value = props.get(key);
        if (value == null || value.isBlank()) {
            throw new PublicationException(
                    "Missing required S3 config property: '" + key + "'");
        }
        return value;
    }

    /**
     * Resolves the S3 endpoint host.
     * In production: {@code {bucket}.s3.{region}.amazonaws.com}.
     * With override (testing): {@code {override}}.
     */
    private String resolveHost(String bucket, String region) {
        if (endpointOverride != null) {
            return endpointOverride;
        }
        return bucket + ".s3." + region + ".amazonaws.com";
    }

    /**
     * Builds the full S3 URI from host and path.
     * Uses HTTPS for real S3, HTTP for endpoint overrides (testing).
     */
    private String buildS3Uri(String host, String s3Path) {
        if (endpointOverride != null) {
            return "http://" + host + s3Path;
        }
        return "https://" + host + s3Path;
    }

    /**
     * Uploads a single object to S3 via HTTP PUT with AWS Signature V4.
     */
    private void putObject(String host, String region, String bucket,
                           String key, String contentType, byte[] payload,
                           String executionId) throws IOException, InterruptedException {
        Instant now = Instant.now();
        String payloadHash = toHex(hash(payload));
        String s3Path = s3Path(bucket, key, host);
        String uri = buildS3Uri(host, s3Path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                .uri(URI.create(uri))
                .header("Content-Type", contentType)
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", AWS_DATE_FORMAT.format(now));

        if (credentials.sessionToken != null && !credentials.sessionToken.isBlank()) {
            builder.header("x-amz-security-token", credentials.sessionToken);
        }

        String authorization = awsSign(builder.build(), host, region, payload, now,
                credentials);
        builder.header("Authorization", authorization);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("action=s3_upload_success executionId={} key={} status={}",
                    executionId, key, response.statusCode());
        } else {
            log.error("action=s3_upload_error executionId={} key={} status={} body={}",
                    executionId, key, response.statusCode(),
                    truncate(response.body()));
            throw new IOException("S3 upload failed: HTTP " + response.statusCode()
                    + " for key " + key);
        }
    }

    /**
     * Returns the request path: for production (virtual hosted-style) it is
     * just {@code /{key}}; with an endpoint override (path-style) it is
     * {@code /{bucket}/{key}}.
     */
    static String s3Path(String bucket, String key, String host) {
        if (host.contains(".amazonaws.com")) {
            return "/" + key;
        }
        // Path-style for custom endpoints (WireMock)
        return "/" + bucket + "/" + key;
    }

    /**
     * Uploads Gatling simulation logs from injection output directories.
     * Walks each injection entry's {@code gatlingOutputPath}, uploading
     * files into {@code {prefix}/gatling/}.
     */
    private void uploadGatlingOutputs(String host, String region, String bucket,
                                      String prefix, CampaignReport report)
            throws IOException, InterruptedException {
        for (var entry : report.injectionResults()) {
            Path gatlingDir = entry.gatlingReportDirectory();
            if (gatlingDir == null || !Files.isDirectory(gatlingDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(gatlingDir)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String relativePath = gatlingDir.relativize(file).toString();
                    String s3Key = prefix + "/gatling/" + relativePath;
                    byte[] payload = Files.readAllBytes(file);
                    String contentType = Files.probeContentType(file);
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }
                    putObject(host, region, bucket, s3Key, contentType,
                            payload, report.id().value());
                }
            }
        }
    }

    /**
     * Serialises the full {@link CampaignReport} to indented JSON.
     */
    byte[] serializeToJson(CampaignReport report) throws IOException {
        return objectMapper.writeValueAsBytes(report);
    }

    /**
     * Builds a standalone HTML summary of the campaign report.
     * <p><b>CC-02:</b> Pipeline cohesif — header → execution summary →
     * verdict → task tables → environment → footer. Chaque section est
     * un bloc HTML inseparable du flux de construction du rapport.</p>
     */
    static String buildHtmlReport(CampaignReport report) {
        var sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>Performance Report: ").append(escapeHtml(report.scenarioName()))
                .append("</title>\n");
        sb.append("<style>\n")
          .append("body{font-family:system-ui,sans-serif;max-width:960px;margin:0 auto;")
          .append("padding:2em;color:#1a1a1a;}\n")
          .append("h1{border-bottom:3px solid #2563eb;padding-bottom:.5em;}\n")
          .append("h2{color:#2563eb;margin-top:1.5em;}\n")
          .append("table{border-collapse:collapse;width:100%;margin:.5em 0;}\n")
          .append("th,td{border:1px solid #ddd;padding:.5em;text-align:left;}\n")
          .append("th{background:#f3f4f6;}\n")
          .append(".verdict{font-size:1.5em;font-weight:bold;padding:.5em 1em;")
          .append("border-radius:4px;display:inline-block;}\n")
          .append(".SUCCESS{background:#dcfce7;color:#166534;}\n")
          .append(".WARNING{background:#fef9c3;color:#854d0e;}\n")
          .append(".FAILED{background:#fee2e2;color:#991b1b;}\n")
          .append(".meta{color:#6b7280;font-size:.9em;}\n")
          .append("</style>\n</head>\n<body>\n");

        sb.append("<h1>Performance Report: ").append(escapeHtml(report.scenarioName()))
                .append("</h1>\n");
        sb.append("<p class=\"meta\">Scenario: ")
                .append(escapeHtml(report.scenarioId().value()))
                .append(" v").append(escapeHtml(report.scenarioVersion()))
                .append(" | Report: ").append(escapeHtml(report.id().value()))
                .append(" | Generated: ").append(report.generatedAt())
                .append(" | Duration: ").append(formatDuration(report.totalDuration()))
                .append("</p>\n");

        // Execution summary
        ExecutionSummary summary = report.executionSummary();
        sb.append("<h2>Execution Summary</h2>\n<table>\n");
        sb.append("<tr><th>Metric</th><th>Value</th></tr>\n");
        sb.append("<tr><td>Total tasks</td><td>").append(summary.totalTasks()).append("</td></tr>\n");
        sb.append("<tr><td>Successful</td><td>").append(summary.successfulTasks()).append("</td></tr>\n");
        sb.append("<tr><td>Failed</td><td>").append(summary.failedTasks()).append("</td></tr>\n");
        sb.append("<tr><td>Skipped</td><td>").append(summary.skippedTasks()).append("</td></tr>\n");
        sb.append("<tr><td>Preparation</td><td>")
                .append(formatDuration(summary.preparationDuration())).append("</td></tr>\n");
        sb.append("<tr><td>Injection</td><td>")
                .append(formatDuration(summary.injectionDuration())).append("</td></tr>\n");
        sb.append("<tr><td>Assertion</td><td>")
                .append(formatDuration(summary.assertionDuration())).append("</td></tr>\n");
        sb.append("</table>\n");

        // Verdict
        sb.append("<h2>Verdict</h2>\n");
        sb.append("<span class=\"verdict ").append(escapeHtml(report.verdict().name()))
                .append("\">").append(escapeHtml(report.verdict().name())).append("</span>\n");
        if (report.verdictReason() != null && !report.verdictReason().isBlank()) {
            sb.append("<p><em>").append(escapeHtml(report.verdictReason())).append("</em></p>\n");
        }

        // Task results
        if (!report.preparationResults().isEmpty()) {
            sb.append("<h2>Preparation</h2>\n");
            appendTaskTable(sb, report.preparationResults());
        }
        if (!report.injectionResults().isEmpty()) {
            sb.append("<h2>Injection</h2>\n");
            appendInjectionTable(sb, report.injectionResults());
        }
        if (!report.assertionResults().isEmpty()) {
            sb.append("<h2>Assertions</h2>\n");
            appendAssertionTable(sb, report.assertionResults());
        }

        // Environment
        EnvironmentInfo env = report.environment();
        sb.append("<h2>Environment</h2>\n<table>\n");
        sb.append("<tr><td>JVM</td><td>").append(escapeHtml(env.jvmVersion())).append("</td></tr>\n");
        sb.append("<tr><td>Agents</td><td>")
                .append(escapeHtml(String.join(", ", env.agentIds())))
                .append("</td></tr>\n");
        sb.append("</table>\n");

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private static void appendTaskTable(StringBuilder sb,
                                         List<TaskReportEntry> entries) {
        sb.append("<table>\n<tr><th>Task</th><th>Status</th><th>Duration</th></tr>\n");
        for (var entry : entries) {
            sb.append("<tr><td>").append(escapeHtml(entry.taskName())).append("</td>");
            sb.append("<td>").append(entry.status().name()).append("</td>");
            sb.append("<td>").append(formatDuration(entry.duration())).append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private static void appendInjectionTable(StringBuilder sb,
                                              List<com.performance.platform.reporting.model.InjectionReportEntry> entries) {
        sb.append("<table>\n<tr><th>Simulation</th><th>Duration</th><th>Requests</th>")
          .append("<th>OK</th><th>KO</th><th>Error%</th><th>Throughput</th>")
          .append("<th>p50</th><th>p95</th><th>p99</th></tr>\n");
        for (var entry : entries) {
            var m = entry.metrics();
            sb.append("<tr><td>").append(escapeHtml(m.simulationClass())).append("</td>");
            sb.append("<td>").append(formatDuration(m.duration())).append("</td>");
            sb.append("<td>").append(m.totalRequests()).append("</td>");
            sb.append("<td>").append(m.successfulRequests()).append("</td>");
            sb.append("<td>").append(m.failedRequests()).append("</td>");
            sb.append("<td>").append(String.format("%.2f", m.errorRate())).append("%</td>");
            sb.append("<td>").append(String.format("%.1f", m.throughput())).append("/s</td>");
            sb.append("<td>").append(m.p50Ms()).append("ms</td>");
            sb.append("<td>").append(m.p95Ms()).append("ms</td>");
            sb.append("<td>").append(m.p99Ms()).append("ms</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private static void appendAssertionTable(StringBuilder sb,
                                              List<com.performance.platform.reporting.model.AssertionReportEntry> entries) {
        sb.append("<table>\n<tr><th>Assertion</th><th>Verdict</th><th>Expected</th>")
          .append("<th>Actual</th><th>Operator</th></tr>\n");
        for (var entry : entries) {
            var r = entry.result();
            var e = r.evidence() != null ? r.evidence() : entry.evidence();
            sb.append("<tr><td>").append(escapeHtml(r.description())).append("</td>");
            sb.append("<td>").append(r.status().name()).append("</td>");
            sb.append("<td>").append(e != null ? String.valueOf(e.expectedValue()) : "-").append("</td>");
            sb.append("<td>").append(e != null ? String.valueOf(e.actualValue()) : "-").append("</td>");
            sb.append("<td>").append(e != null ? e.operator().name() : "-").append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    // ---- AWS Signature V4 ------------------------------------------------

    /**
     * Signs an HTTP request with AWS Signature V4.
     *
     * <p><b>CC-02:</b> Pipeline protocolaire AWS SigV4 cohesif —
     * construction de la canonical request → construction du string to sign →
     * derivation de la signing key → calcul de la signature HMAC-SHA256 →
     * assemblage du header Authorization final. Chaque etape est
     * indissociable de la specification AWS SigV4 et ne peut etre
     * extraite independamment sans briser la conformite au protocole.</p>
     *
     * @param request the request to sign (without Authorization header)
     * @param host    the S3 host
     * @param region  the AWS region
     * @param payload the request body
     * @param now     the signing timestamp
     * @return the {@code Authorization} header value
     */
    static String awsSign(HttpRequest request, String host, String region,
                          byte[] payload, Instant now, AwsCredentials credentials) {
        String dateStamp = AWS_DATE_ONLY.format(now);
        String amzDate = AWS_DATE_FORMAT.format(now);
        String payloadHash = toHex(hash(payload));
        String credentialScope = dateStamp + "/" + region + "/" + S3_SERVICE + "/aws4_request";

        // Canonical request
        String canonicalUri = uriEncodePath(request.uri().getPath());
        String canonicalQuery = request.uri().getQuery() != null
                ? request.uri().getQuery() : "";
        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

        if (credentials.sessionToken != null && !credentials.sessionToken.isBlank()) {
            canonicalHeaders += "x-amz-security-token:" + credentials.sessionToken + "\n";
            signedHeaders += ";x-amz-security-token";
        }

        canonicalHeaders += "\n";

        String canonicalRequest = request.method() + "\n"
                + canonicalUri + "\n"
                + canonicalQuery + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        // String to sign
        String stringToSign = SIGNING_ALGORITHM + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + toHex(hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        // Signing key
        byte[] signingKey = awsSigningKey(credentials.secretAccessKey, dateStamp, region,
                S3_SERVICE);

        // Signature
        byte[] signature = hmacSha256(signingKey,
                stringToSign.getBytes(StandardCharsets.UTF_8));

        return SIGNING_ALGORITHM + " Credential=" + credentials.accessKeyId + "/"
                + credentialScope + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + toHex(signature);
    }

    /**
     * Derives the AWS Signature V4 signing key.
     */
    static byte[] awsSigningKey(String secretKey, String dateStamp, String region,
                                String service) {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp.getBytes(StandardCharsets.UTF_8));
        byte[] kRegion = hmacSha256(kDate, region.getBytes(StandardCharsets.UTF_8));
        byte[] kService = hmacSha256(kRegion, service.getBytes(StandardCharsets.UTF_8));
        return hmacSha256(kService, "aws4_request".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes SHA-256 hash of the given data.
     */
    static byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Computes HMAC-SHA256.
     */
    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /**
     * Hex-encodes a byte array.
     */
    static String toHex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }

    /**
     * Minimal URI path encoding for S3 keys.
     * Encodes the path segment by segment, preserving slashes.
     */
    static String uriEncodePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] segments = path.split("/", -1);
        // Skip leading empty segment from absolute paths
        int start = segments.length > 0 && segments[0].isEmpty() ? 1 : 0;
        var sb = new StringBuilder();
        for (int i = start; i < segments.length; i++) {
            sb.append('/');
            sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }
        // Preserve leading / for absolute paths, strip for relative
        if (!path.startsWith("/")) {
            return sb.substring(1);
        }
        if (sb.isEmpty()) {
            return "/";
        }
        return sb.toString();
    }

    // ---- credentials -----------------------------------------------------

    /**
     * Resolves AWS credentials from the standard environment-variable chain.
     * Never logs the access key or secret key.
     */
    private static AwsCredentials resolveCredentials() {
        String accessKeyId = System.getenv(AWS_ACCESS_KEY_ID_ENV);
        String secretAccessKey = System.getenv(AWS_SECRET_ACCESS_KEY_ENV);
        String sessionToken = System.getenv(AWS_SESSION_TOKEN_ENV);

        if (accessKeyId == null || accessKeyId.isBlank()
                || secretAccessKey == null || secretAccessKey.isBlank()) {
            throw new IllegalStateException(
                    "AWS credentials not found. Set " + AWS_ACCESS_KEY_ID_ENV
                            + " and " + AWS_SECRET_ACCESS_KEY_ENV
                            + " environment variables.");
        }
        return new AwsCredentials(accessKeyId, secretAccessKey,
                sessionToken != null && !sessionToken.isBlank() ? sessionToken : null);
    }

    private static ObjectMapper buildObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    // ---- formatting helpers -----------------------------------------------

    private static String formatDuration(Duration d) {
        if (d == null) return "N/A";
        long s = d.toSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String truncate(String s) {
        return s == null ? "null" :
                s.length() <= 500 ? s : s.substring(0, 500) + "...";
    }

    // ---- AWS credentials record (package-private) -------------------------

    /**
     * AWS credentials resolved from environment.
     * Package-visible for test injection.
     */
    record AwsCredentials(String accessKeyId, String secretAccessKey,
                          String sessionToken) {
        AwsCredentials {
            if (accessKeyId == null || accessKeyId.isBlank()) {
                throw new IllegalArgumentException("accessKeyId required");
            }
            if (secretAccessKey == null || secretAccessKey.isBlank()) {
                throw new IllegalArgumentException("secretAccessKey required");
            }
        }
    }
}
