package com.performance.platform.infrastructure.publisher.git;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GitReportPublisher")
class GitReportPublisherTest {

    private static final String TEST_REPO_URL = "https://github.com/test-org/test-repo.git";
    private static final String TEST_TOKEN = "ghp_testToken123";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";

    private ExecutorService executor;
    private GitReportPublisher publisher;
    private CampaignReport report;

    @TempDir
    Path tempDir;

    private Path remoteRepo;
    private String remoteRepoUrl;

    @BeforeEach
    void setUp() throws Exception {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        publisher = new GitReportPublisher(executor);

        // Create a non-bare repo with initial commit to serve as the "remote"
        remoteRepo = tempDir.resolve("remote-repo");
        Files.createDirectories(remoteRepo);
        runGitRaw(remoteRepo, "git", "init");
        runGitRaw(remoteRepo, "git", "config", "user.email", "test@test.com");
        runGitRaw(remoteRepo, "git", "config", "user.name", "Test");
        Files.writeString(remoteRepo.resolve("README.md"), "# Test Repo");
        runGitRaw(remoteRepo, "git", "add", "README.md");
        runGitRaw(remoteRepo, "git", "commit", "-m", "initial commit");
        // Rename to 'main' since git init may default to 'master'
        runGitRaw(remoteRepo, "git", "branch", "-m", "main");
        // Allow pushes to the checked-out branch
        runGitRaw(remoteRepo, "git", "config", "receive.denyCurrentBranch", "ignore");

        remoteRepoUrl = "file://" + remoteRepo.toAbsolutePath();

        report = stubReport();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // -------------------------------------------------------------------
    // getTarget
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should return GIT target")
    void shouldReturnGitTarget() {
        assertThat(publisher.getTarget()).isEqualTo(PublicationTarget.GIT);
    }

    // -------------------------------------------------------------------
    // Cas nominal
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should clone, commit, and push report files")
    void shouldPublishReportToGit() {
        var config = configWith(TEST_REPO_URL, "main", null, null, null,
                "reports/test-run", "Automated report");

        // Use file:// URL pointing to our bare repo
        var localConfig = configWith(remoteRepoUrl, "main", null, null, null,
                "reports/test-run", "Automated report");

        assertThatCode(() -> publisher.publish(report, localConfig))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should publish with custom branch")
    void shouldPublishWithCustomBranch() throws Exception {
        // Create 'develop' branch in the remote repo
        runGitRaw(remoteRepo, "git", "checkout", "-b", "develop");
        Files.writeString(remoteRepo.resolve("dummy.txt"), "branch");
        runGitRaw(remoteRepo, "git", "add", "dummy.txt");
        runGitRaw(remoteRepo, "git", "commit", "-m", "develop branch");
        runGitRaw(remoteRepo, "git", "checkout", "main");

        var config = configWith(remoteRepoUrl, "develop", null, null, null,
                "reports", "Report on develop");
        assertThatCode(() -> publisher.publish(report, config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should publish with minimal config (repo-url only)")
    void shouldPublishWithMinimalConfig() {
        var config = new PublisherConfig(PublicationTarget.GIT, Map.of(
                GitReportPublisher.KEY_REPO_URL, remoteRepoUrl
        ));
        assertThatCode(() -> publisher.publish(report, config))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------
    // Configuration errors
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should throw when repo-url is missing")
    void shouldThrowWhenRepoUrlMissing() {
        var config = new PublisherConfig(PublicationTarget.GIT, Map.of(
                GitReportPublisher.KEY_BRANCH, "main"
        ));
        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("repo-url");
    }

    @Test
    @DisplayName("should throw when repo-url is blank")
    void shouldThrowWhenRepoUrlBlank() {
        var config = new PublisherConfig(PublicationTarget.GIT, Map.of(
                GitReportPublisher.KEY_REPO_URL, "   "
        ));
        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class)
                .hasMessageContaining("repo-url");
    }

    @Test
    @DisplayName("should throw when git clone fails (invalid URL)")
    void shouldThrowWhenCloneFails() {
        var config = configWith("https://invalid.example.com/nonexistent.git",
                "main", null, null, null, "reports", "test commit");
        assertThatThrownBy(() -> publisher.publish(report, config))
                .isInstanceOf(PublicationException.class);
    }

    // -------------------------------------------------------------------
    // buildAuthenticatedUrl
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should inject token into HTTPS URL")
    void shouldInjectTokenIntoUrl() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, TEST_TOKEN, null, null);
        assertThat(result).isEqualTo("https://ghp_testToken123@github.com/test-org/test-repo.git");
    }

    @Test
    @DisplayName("should inject username and password into HTTPS URL")
    void shouldInjectUsernamePasswordIntoUrl() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, null, TEST_USERNAME, TEST_PASSWORD);
        assertThat(result).isEqualTo(
                "https://testuser:testpass@github.com/test-org/test-repo.git");
    }

    @Test
    @DisplayName("should return original URL when no auth")
    void shouldReturnOriginalUrlWhenNoAuth() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, null, null, null);
        assertThat(result).isEqualTo(TEST_REPO_URL);
    }

    @Test
    @DisplayName("should prefer token over username/password")
    void shouldPreferTokenOverUsernamePassword() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, TEST_TOKEN, TEST_USERNAME, TEST_PASSWORD);
        assertThat(result).isEqualTo("https://ghp_testToken123@github.com/test-org/test-repo.git");
    }

    @Test
    @DisplayName("should handle blank token (fall back to username/password)")
    void shouldFallBackToUsernamePasswordWhenTokenBlank() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, "  ", TEST_USERNAME, TEST_PASSWORD);
        assertThat(result).isEqualTo(
                "https://testuser:testpass@github.com/test-org/test-repo.git");
    }

    @Test
    @DisplayName("should handle null username with password")
    void shouldHandleNullUsernameWithPassword() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, null, null, TEST_PASSWORD);
        assertThat(result).isEqualTo(TEST_REPO_URL);
    }

    @Test
    @DisplayName("should handle null password with username")
    void shouldHandleNullPasswordWithUsername() {
        var result = GitReportPublisher.buildAuthenticatedUrl(
                TEST_REPO_URL, null, TEST_USERNAME, null);
        assertThat(result).isEqualTo(TEST_REPO_URL);
    }

    // -------------------------------------------------------------------
    // Constantes package-visible
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should expose KEY_REPO_URL constant")
    void shouldExposeKeyRepoUrl() {
        assertThat(GitReportPublisher.KEY_REPO_URL).isEqualTo("repo-url");
    }

    @Test
    @DisplayName("should expose KEY_BRANCH constant")
    void shouldExposeKeyBranch() {
        assertThat(GitReportPublisher.KEY_BRANCH).isEqualTo("branch");
    }

    @Test
    @DisplayName("should expose KEY_TOKEN constant")
    void shouldExposeKeyToken() {
        assertThat(GitReportPublisher.KEY_TOKEN).isEqualTo("token");
    }

    @Test
    @DisplayName("should expose KEY_PATH constant")
    void shouldExposeKeyPath() {
        assertThat(GitReportPublisher.KEY_PATH).isEqualTo("path");
    }

    // -------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------

    @Test
    @DisplayName("should handle report with no tasks")
    void shouldHandleEmptyTaskList() throws Exception {
        var emptyReport = new CampaignReport(
                ReportId.generate(),
                ScenarioId.of("empty-scenario"),
                "empty-scenario",
                "1.0",
                List.of(),
                Map.of(),
                new EnvironmentInfo(List.of(), "Java 25", Map.of()),
                new ExecutionSummary(0, 0, 0, 0,
                        Duration.ZERO, Duration.ZERO, Duration.ZERO),
                List.of(),
                List.of(),
                List.of(),
                Verdict.SUCCESS,
                "No tasks executed",
                Instant.now(),
                Duration.ofSeconds(1)
        );
        var config = configWith(remoteRepoUrl, "main", null, null, null,
                "empty-report", "Empty report test");
        assertThatCode(() -> publisher.publish(emptyReport, config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should handle report with FAILED verdict")
    void shouldHandleFailedVerdict() throws Exception {
        var failedReport = new CampaignReport(
                ReportId.generate(),
                ScenarioId.of("failed-scenario"),
                "failed-scenario",
                "1.0",
                List.of("critical"),
                Map.of("env", "staging"),
                new EnvironmentInfo(List.of(), "Java 25", Map.of()),
                new ExecutionSummary(1, 0, 1, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO),
                List.of(new TaskReportEntry(TaskId.of("task-1"), "test",
                        TaskStatus.FAILED, Duration.ofSeconds(1), Map.of())),
                List.of(),
                List.of(),
                Verdict.FAILED,
                "Test failed: assertion error",
                Instant.now(),
                Duration.ofSeconds(2)
        );
        var config = configWith(remoteRepoUrl, "main", null, null, null,
                "failed-report", "Failed report");
        assertThatCode(() -> publisher.publish(failedReport, config))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------
    // Stub helpers
    // -------------------------------------------------------------------

    private static PublisherConfig configWith(String repoUrl, String branch,
                                              String token, String username,
                                              String password, String path,
                                              String commitMessage) {
        var props = new java.util.HashMap<String, String>();
        props.put(GitReportPublisher.KEY_REPO_URL, repoUrl);
        if (branch != null) props.put(GitReportPublisher.KEY_BRANCH, branch);
        if (token != null) props.put(GitReportPublisher.KEY_TOKEN, token);
        if (username != null) props.put(GitReportPublisher.KEY_USERNAME, username);
        if (password != null) props.put(GitReportPublisher.KEY_PASSWORD, password);
        if (path != null) props.put(GitReportPublisher.KEY_PATH, path);
        if (commitMessage != null) props.put(GitReportPublisher.KEY_COMMIT_MESSAGE, commitMessage);
        return new PublisherConfig(PublicationTarget.GIT, Map.copyOf(props));
    }

    private static CampaignReport stubReport() {
        var taskId = TaskId.of("task-1");
        var injectTaskId = TaskId.of("inject-1");
        var assertTaskId = TaskId.of("assert-1");
        return new CampaignReport(
                ReportId.generate(),
                ScenarioId.of("test-scenario"),
                "test-scenario",
                "1.0",
                List.of("smoke", "critical"),
                Map.of("env", "staging"),
                new EnvironmentInfo(List.of("agent-1", "agent-2"), "Java 25", Map.of()),
                new ExecutionSummary(3, 2, 1, 0,
                        Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(1)),
                List.of(new TaskReportEntry(taskId, "prepare-db",
                        TaskStatus.SUCCESS, Duration.ofSeconds(2), Map.of())),
                List.of(new InjectionReportEntry(injectTaskId,
                        new InjectionResult(injectTaskId, "com.example.LoadTest",
                                Duration.ofSeconds(10), 1000, 995, 5, 0.5, 100.0,
                                45, 78, 120, 150, 200, 300, 10, 50.5,
                                Path.of("/tmp/gatling"), Map.of()),
                        Path.of("/tmp/gatling"))),
                List.of(new AssertionReportEntry(assertTaskId,
                        new AssertionResult(assertTaskId, AssertionStatus.PASSED,
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

    /**
     * Runs a raw git command (for test setup only, no authentication).
     */
    private static void runGitRaw(Path workDir, String... command) throws Exception {
        var pb = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Git command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            var output = new String(process.getInputStream().readAllBytes());
            throw new RuntimeException("Git command failed: " + String.join(" ", command)
                    + " — " + output);
        }
    }
}
