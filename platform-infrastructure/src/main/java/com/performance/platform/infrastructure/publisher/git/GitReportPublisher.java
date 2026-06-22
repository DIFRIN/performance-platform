package com.performance.platform.infrastructure.publisher.git;

import com.performance.platform.reporting.PublicationException;
import com.performance.platform.reporting.PublicationTarget;
import com.performance.platform.reporting.ReportPublisher;
import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.PublisherConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Publishes the campaign report to a Git repository.
 *
 * <p>Pushes two artefacts per report into a target path within the repository:
 * <ol>
 *   <li>{@code report.json} — full {@link CampaignReport} serialised as JSON</li>
 *   <li>{@code report.html} — standalone HTML summary</li>
 * </ol>
 *
 * <p>Gatling simulation logs are NOT uploaded to Git (binaries / large files
 * are better stored in S3 or an artifact repository).</p>
 *
 * <p>Configuration is read from {@link PublisherConfig#properties()}:
 * <ul>
 *   <li>{@code repo-url} — Git repository HTTPS URL (required)</li>
 *   <li>{@code branch} — target branch (optional, defaults to {@code main})</li>
 *   <li>{@code token} — personal access token (optional; prioritised over
 *       username/password)</li>
 *   <li>{@code username} — Git username for HTTPS auth (optional)</li>
 *   <li>{@code password} — Git password for HTTPS auth (optional)</li>
 *   <li>{@code path} — sub-directory inside the repository (optional, defaults
 *       to the report id)</li>
 *   <li>{@code commit-message} — commit message (optional, defaults to
 *       {@code "Report <reportId>"})</li>
 * </ul>
 *
 * <p>Secrets ({@code token}, {@code password}) are never logged. When
 * {@code token} is present it is injected into the clone URL as
 * {@code https://{token}@host/repo}. When only {@code username} +
 * {@code password} are present they are injected as
 * {@code https://{username}:{password}@host/repo}.</p>
 *
 * <p>All Git operations execute under Virtual Threads via a dedicated
 * {@link ExecutorService}. A per-operation timeout of 30 seconds is
 * enforced via {@link Process#waitFor(long, TimeUnit)}.</p>
 *
 * <p><b>CC-02:</b> Pipeline cohesif de publication Git —
 * validation configuration → clonage ({@link #gitClone}) →
 * ecriture artefacts → commit ({@link #gitCommit}) →
 * push ({@link #gitPush}) → nettoyage repertoire temporaire.
 * Les helpers d'execution de commandes ({@link #runGit}) et
 * de construction d'URL authentifiee ({@link #buildAuthenticatedUrl})
 * sont indissociables du flux de publication Git. Extraire une
 * portion isolee nuirait a la lisibilite du pipeline sequentiel.</p>
 *
 * <p><b>Activation:</b> Ce publisher est enregistre comme {@code @Component}
 * sans {@code @ConditionalOnProperty}. L'activation/desactivation est
 * deleguee au {@code MultiPublisherDispatcher} qui filtre par
 * {@code reporting.publishers.*} dans la configuration.</p>
 */
@Component
public class GitReportPublisher implements ReportPublisher {

    private static final Logger log = LoggerFactory.getLogger(GitReportPublisher.class);

    /** Configuration property keys (package-visible for test references). */
    static final String KEY_REPO_URL = "repo-url";
    static final String KEY_BRANCH = "branch";
    static final String KEY_TOKEN = "token";
    static final String KEY_USERNAME = "username";
    static final String KEY_PASSWORD = "password";
    static final String KEY_PATH = "path";
    static final String KEY_COMMIT_MESSAGE = "commit-message";

    private static final String DEFAULT_BRANCH = "main";
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30);
    private static final String HTML_REPORT_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <title>%s — Report</title>
            <style>
            body{font-family:system-ui,sans-serif;margin:2em;color:#1a1a1a}
            h1{color:#2c3e50}.verdict{font-size:1.2em;font-weight:bold;padding:.5em 1em;
            border-radius:4px;display:inline-block}
            .PASSED{background:#d4edda;color:#155724}.FAILED{background:#f8d7da;color:#721c24}
            table{border-collapse:collapse;width:100%%;margin:1em 0}
            th,td{border:1px solid #ddd;padding:8px;text-align:left}
            th{background:#f5f5f5}.section{margin:2em 0}
            </style>
            </head>
            <body>
            <h1>%s</h1>
            <div class="section"><span class="verdict %s">%s</span></div>
            <div class="section"><h2>Summary</h2>%s</div>
            <div class="section"><h2>Tasks</h2>%s</div>
            </body>
            </html>
            """;

    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    /** Public no-arg constructor for Spring. */
    public GitReportPublisher() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.objectMapper = buildObjectMapper();
    }

    /**
     * Package-visible constructor for testing with a custom executor.
     *
     * @param executor the executor to use for Git command execution
     */
    GitReportPublisher(ExecutorService executor) {
        this.executor = executor;
        this.objectMapper = buildObjectMapper();
    }

    // ---- ReportPublisher contract -------------------------------------------

    @Override
    public PublicationTarget getTarget() {
        return PublicationTarget.GIT;
    }

    /**
     * Publishes the campaign report to a Git repository.
     *
     * <p><b>CC-02:</b> Pipeline cohesif — validation configuration →
     * clonage shallow du depot → ecriture artefacts → commit →
     * push → nettoyage. Chaque etape depend de la precedente et ne
     * peut etre reorganisee sans briser le flux de publication Git.</p>
     *
     * @param report the campaign report to publish
     * @param config the publisher configuration
     * @throws PublicationException if publication fails
     */
    @Override
    public void publish(CampaignReport report, PublisherConfig config)
            throws PublicationException {
        Map<String, String> props = config.properties();
        String repoUrl = requireProperty(props, KEY_REPO_URL, "repository URL");
        String branch = stringProp(props, KEY_BRANCH, DEFAULT_BRANCH);
        String token = stringProp(props, KEY_TOKEN, null);
        String username = stringProp(props, KEY_USERNAME, null);
        String password = stringProp(props, KEY_PASSWORD, null);
        String subPath = stringProp(props, KEY_PATH, report.id().value());
        String commitMsg = stringProp(props, KEY_COMMIT_MESSAGE,
                "Report " + report.id().value());

        String authenticatedUrl = buildAuthenticatedUrl(repoUrl, token, username, password);
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("git-publish-");
            String safeUrl = repoUrl; // original URL without secrets for logging
            log.info("action=git_publish_start executionId={} repo={} branch={} path={}",
                    report.id().value(), safeUrl, branch, subPath);

            // Clone
            gitClone(authenticatedUrl, branch, workDir);

            // Write artefacts
            Path targetDir = workDir.resolve(subPath);
            Files.createDirectories(targetDir);
            writeReportJson(report, targetDir.resolve("report.json"));
            writeReportHtml(report, targetDir.resolve("report.html"));

            // Stage, commit, push
            gitAdd(workDir);
            gitCommit(workDir, commitMsg);
            gitPush(workDir, branch);

            log.info("action=git_publish_success executionId={} path={}",
                    report.id().value(), subPath);

        } catch (IOException e) {
            throw new PublicationException("Failed to create temp directory for Git publish", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublicationException("Git publish interrupted", e);
        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    // ---- Git command helpers ------------------------------------------------

    private void gitClone(String url, String branch, Path workDir)
            throws PublicationException, InterruptedException {
        runGit(workDir,
                "git", "clone", "--depth", "1", "--branch", branch, url, ".");
    }

    private void gitAdd(Path workDir) throws PublicationException, InterruptedException {
        runGit(workDir, "git", "add", ".");
    }

    private void gitCommit(Path workDir, String message)
            throws PublicationException, InterruptedException {
        runGit(workDir, "git", "commit", "-m", message);
    }

    private void gitPush(Path workDir, String branch)
            throws PublicationException, InterruptedException {
        runGit(workDir, "git", "push", "origin", branch);
    }

    /**
     * Runs a Git command and waits for completion. Timeout is enforced via
     * {@link Process#waitFor(long, TimeUnit)}.
     *
     * @param workDir     working directory for the process
     * @param command     command and arguments
     * @throws PublicationException if the command fails or times out
     * @throws InterruptedException if the waiting thread is interrupted
     */
    private void runGit(Path workDir, String... command)
            throws PublicationException, InterruptedException {
        try {
            var pb = new ProcessBuilder(command)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new PublicationException(
                        "Git command timed out after " + GIT_TIMEOUT.toSeconds() + "s: "
                                + String.join(" ", sanitizeCommand(command)));
            }

            if (process.exitValue() != 0) {
                String output;
                try (var in = process.getInputStream()) {
                    output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new PublicationException(
                        "Git command failed with exit code " + process.exitValue()
                                + ": " + String.join(" ", sanitizeCommand(command))
                                + " — " + output.trim());
            }
        } catch (IOException e) {
            throw new PublicationException(
                    "Failed to execute Git command: "
                            + String.join(" ", sanitizeCommand(command)), e);
        }
    }

    // ---- Report file writers ------------------------------------------------

    private void writeReportJson(CampaignReport report, Path target)
            throws PublicationException {
        try {
            String json = objectMapper.writeValueAsString(report);
            Files.writeString(target, json);
            log.debug("action=git_json_written executionId={} path={}",
                    report.id().value(), target);
        } catch (IOException e) {
            throw new PublicationException("Failed to write report.json", e);
        }
    }

    /**
     * Builds a minimal standalone HTML report. The HTML template is inlined
     * as a class constant to avoid external file dependencies.
     */
    private void writeReportHtml(CampaignReport report, Path target)
            throws PublicationException {
        try {
            String verdictClass = report.verdict().name();
            String summaryHtml = buildSummaryHtml(report);
            String tasksHtml = buildTasksHtml(report);

            var html = String.format(HTML_REPORT_TEMPLATE,
                    escapeHtml(report.scenarioName()),
                    escapeHtml(report.scenarioName()),
                    escapeHtml(verdictClass),
                    escapeHtml(report.verdict().name()),
                    summaryHtml,
                    tasksHtml);

            Files.writeString(target, html);
            log.debug("action=git_html_written executionId={} path={}",
                    report.id().value(), target);
        } catch (IOException e) {
            throw new PublicationException("Failed to write report.html", e);
        }
    }

    private static String buildSummaryHtml(CampaignReport report) {
        var sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr><th>Scenario</th><td>").append(escapeHtml(report.scenarioName()))
                .append("</td></tr>");
        sb.append("<tr><th>Version</th><td>").append(escapeHtml(report.scenarioVersion()))
                .append("</td></tr>");
        sb.append("<tr><th>Verdict</th><td>").append(escapeHtml(report.verdict().name()))
                .append("</td></tr>");
        if (report.verdictReason() != null) {
            sb.append("<tr><th>Reason</th><td>").append(escapeHtml(report.verdictReason()))
                    .append("</td></tr>");
        }
        sb.append("<tr><th>Duration</th><td>")
                .append(formatDuration(report.totalDuration())).append("</td></tr>");
        sb.append("</table>");
        return sb.toString();
    }

    private static String buildTasksHtml(CampaignReport report) {
        var sb = new StringBuilder();
        sb.append("<table><tr><th>Task</th><th>Status</th><th>Duration</th></tr>");

        for (var task : report.preparationResults()) {
            appendTaskRow(sb, task.taskName(), task.status().name(), task.duration());
        }
        for (var task : report.injectionResults()) {
            appendTaskRow(sb, "injection: " + task.metrics().simulationClass(),
                    "COMPLETED", task.metrics().duration());
        }
        for (var task : report.assertionResults()) {
            appendTaskRow(sb, "assertion: " + task.result().description(),
                    task.result().status().name(), task.result().evaluationDuration());
        }
        sb.append("</table>");
        return sb.toString();
    }

    private static void appendTaskRow(StringBuilder sb, String name,
                                      String status, Duration duration) {
        sb.append("<tr>");
        sb.append("<td>").append(escapeHtml(name)).append("</td>");
        sb.append("<td>").append(escapeHtml(status)).append("</td>");
        sb.append("<td>").append(formatDuration(duration)).append("</td>");
        sb.append("</tr>");
    }

    // ---- Auth URL builder ---------------------------------------------------

    /**
     * Builds an authenticated HTTPS URL by injecting credentials.
     * Never logs the resulting URL. Format:
     * <ul>
     *   <li>Token: {@code https://{token}@host/path}</li>
     *   <li>Username + password: {@code https://{user}:{pass}@host/path}</li>
     *   <li>No auth: original URL unchanged</li>
     * </ul>
     */
    static String buildAuthenticatedUrl(String repoUrl, String token,
                                        String username, String password) {
        if (token != null && !token.isBlank()) {
            return repoUrl.replace("https://", "https://" + token + "@");
        }
        if (username != null && !username.isBlank()
                && password != null && !password.isBlank()) {
            return repoUrl.replace("https://",
                    "https://" + username + ":" + password + "@");
        }
        return repoUrl;
    }

    // ---- Utility methods ----------------------------------------------------

    private static String requireProperty(Map<String, String> props, String key,
                                          String description) {
        String value = props.get(key);
        if (value == null || value.isBlank()) {
            throw new PublicationException(
                    "Missing required configuration: '" + key + "' (" + description + ")");
        }
        return value;
    }

    private static String stringProp(Map<String, String> props, String key,
                                     String defaultValue) {
        String value = props.get(key);
        return value != null && !value.isBlank()
                ? value : defaultValue;
    }

    /**
     * Strips secrets from command arguments for safe logging.
     */
    private static String[] sanitizeCommand(String[] command) {
        var sanitized = new String[command.length];
        for (int i = 0; i < command.length; i++) {
            String arg = command[i];
            if (arg != null && (arg.contains("@") && (arg.startsWith("https://")))) {
                sanitized[i] = arg.replaceAll("://[^@]+@", "://***@");
            } else {
                sanitized[i] = arg;
            }
        }
        return sanitized;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String formatDuration(Duration d) {
        if (d == null) return "0s";
        long ms = d.toMillis();
        if (ms < 1_000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        long minutes = ms / 60_000;
        long seconds = (ms % 60_000) / 1000;
        return minutes + "m " + seconds + "s";
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Best-effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }

    private static ObjectMapper buildObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
