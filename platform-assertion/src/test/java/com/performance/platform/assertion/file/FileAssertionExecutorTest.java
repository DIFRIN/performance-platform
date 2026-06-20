package com.performance.platform.assertion.file;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static com.performance.platform.domain.scenario.Phase.ASSERTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileAssertionExecutor")
class FileAssertionExecutorTest {

    private FileAssertionExecutor executor;

    private static final TaskId ASSERTION_ID = new TaskId("assertion-001");
    private static final ExecutionId EXEC_ID = new ExecutionId("exec-001");
    private static final ScenarioId SCENARIO_ID = new ScenarioId("scenario-001");

    @TempDir
    Path tempDir;

    private Path existingFile;
    private Path nonExistingFile;

    @BeforeEach
    void setUp() throws IOException {
        executor = new FileAssertionExecutor();

        // Creer un fichier de test avec contenu connu
        existingFile = tempDir.resolve("test-file.txt");
        Files.writeString(existingFile, "Hello, world! This is a test file for assertions.");

        // Fichier qui n'existe pas
        nonExistingFile = tempDir.resolve("does-not-exist.txt");
    }

    // --- Helpers ---

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(EXEC_ID, SCENARIO_ID);
    }

    private static StepDefinition step(Map<String, Object> params) {
        return new StepDefinition(ASSERTION_ID, "file", ASSERTION,
                params, null, null, null, null);
    }

    // ==================== EXISTS ====================

    @Nested
    @DisplayName("EXISTS check")
    class ExistsCheck {

        @Test
        @DisplayName("should PASS when file exists")
        void shouldPassWhenFileExists() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "EXISTS"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.isPassed()).isTrue();
            assertThat(ar.description()).contains("PASSED", "exists");
        }

        @Test
        @DisplayName("should FAIL when file does not exist")
        void shouldFailWhenFileNotExists() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", nonExistingFile.toString(),
                    "check", "EXISTS"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.isPassed()).isFalse();
        }

        @Test
        @DisplayName("EXISTS should be case-insensitive")
        void existsShouldBeCaseInsensitive() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "exists"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
        }
    }

    // ==================== NOT_EXISTS ====================

    @Nested
    @DisplayName("NOT_EXISTS check")
    class NotExistsCheck {

        @Test
        @DisplayName("should PASS when file does not exist")
        void shouldPassWhenFileNotExists() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", nonExistingFile.toString(),
                    "check", "NOT_EXISTS"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.isPassed()).isTrue();
        }

        @Test
        @DisplayName("should FAIL when file exists")
        void shouldFailWhenFileExists() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "NOT_EXISTS"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.isPassed()).isFalse();
        }
    }

    // ==================== CHECKSUM ====================

    @Nested
    @DisplayName("CHECKSUM check")
    class ChecksumCheck {

        @Test
        @DisplayName("should PASS when sha256 matches")
        void shouldPassWhenChecksumMatches() throws Exception {
            String expected = "sha256:" + sha256Hex(existingFile);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "CHECKSUM",
                    "checksum", expected));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.isPassed()).isTrue();
            assertThat(ar.evidence().actualValue()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should FAIL when sha256 does not match")
        void shouldFailWhenChecksumMismatch() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "CHECKSUM",
                    "checksum", "sha256:0000000000000000000000000000000000000000000000000000000000000000"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
            assertThat(ar.isPassed()).isFalse();
        }

        @Test
        @DisplayName("should be case-insensitive for hex comparison")
        void shouldBeCaseInsensitive() throws Exception {
            String hex = sha256Hex(existingFile).toUpperCase();
            String expected = "sha256:" + hex;
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "CHECKSUM",
                    "checksum", expected));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("should return ERROR when file missing for checksum")
        void shouldErrorWhenFileMissing() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", nonExistingFile.toString(),
                    "check", "CHECKSUM",
                    "checksum", "sha256:abcd"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("File not found");
        }

        @Test
        @DisplayName("should return ERROR when checksum missing sha256 prefix")
        void shouldErrorOnMissingPrefix() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "CHECKSUM",
                    "checksum", "abcdef"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("sha256:");
        }

        @Test
        @DisplayName("should return ERROR when checksum missing required parameter")
        void shouldErrorOnMissingChecksumParam() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "CHECKSUM"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "checksum");
        }

        @Test
        @DisplayName("should return ERROR when checksum hex is blank after prefix")
        void shouldErrorOnBlankChecksumHex() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "CHECKSUM",
                    "checksum", "sha256:"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("hex value is empty");
        }
    }

    // ==================== SIZE ====================

    @Nested
    @DisplayName("SIZE check")
    class SizeCheck {

        @Test
        @DisplayName("SIZE_GT should PASS when file larger than threshold")
        void shouldPassSizeGt() throws IOException {
            long fileSize = Files.size(existingFile);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_GT",
                    "sizeBytes", (double) (fileSize - 1)));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
            assertThat(ar.evidence().actualValue()).isEqualTo((double) fileSize);
            assertThat(ar.evidence().unit()).isEqualTo("bytes");
        }

        @Test
        @DisplayName("SIZE_GT should FAIL when file smaller than threshold")
        void shouldFailSizeGt() throws IOException {
            long fileSize = Files.size(existingFile);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_GT",
                    "sizeBytes", (double) (fileSize + 1000)));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
        }

        @Test
        @DisplayName("SIZE_GT should FAIL when equal (strict inequality)")
        void shouldFailSizeGtWhenEqual() throws IOException {
            long fileSize = Files.size(existingFile);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_GT",
                    "sizeBytes", (double) fileSize));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
        }

        @Test
        @DisplayName("SIZE_LT should PASS when file smaller than threshold")
        void shouldPassSizeLt() throws IOException {
            long fileSize = Files.size(existingFile);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_LT",
                    "sizeBytes", (double) (fileSize + 1000)));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.PASSED);
        }

        @Test
        @DisplayName("SIZE_LT should FAIL when file larger than threshold")
        void shouldFailSizeLt() throws IOException {
            long fileSize = Files.size(existingFile);
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_LT",
                    "sizeBytes", (double) (fileSize - 1)));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.FAILED);
        }

        @Test
        @DisplayName("should return ERROR when file missing for size check")
        void shouldErrorWhenFileMissing() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", nonExistingFile.toString(),
                    "check", "SIZE_GT",
                    "sizeBytes", 100));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("File not found");
        }

        @Test
        @DisplayName("should return ERROR when sizeBytes parameter missing")
        void shouldErrorOnMissingSizeBytes() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_GT"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "sizeBytes");
        }

        @Test
        @DisplayName("should return ERROR when sizeBytes is non-numeric string")
        void shouldErrorOnNonNumericSizeBytes() {
            ExecutionContext ctx = emptyContext();
            Map<String, Object> p = Map.of(
                    "path", existingFile.toString(),
                    "check", "SIZE_GT",
                    "sizeBytes", "not-a-number");
            StepDefinition step = step(p);

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("must be a number");
        }
    }

    // ==================== Error Cases ====================

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return ERROR for unsupported check")
        void shouldErrorOnUnsupportedCheck() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString(),
                    "check", "INVALID"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Unsupported check");
        }

        @Test
        @DisplayName("should return ERROR for missing required parameter path")
        void shouldErrorOnMissingPath() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "check", "EXISTS"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "path");
        }

        @Test
        @DisplayName("should return ERROR for missing required parameter check")
        void shouldErrorOnMissingCheck() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", existingFile.toString()));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("Missing required parameter", "check");
        }

        @Test
        @DisplayName("should return ERROR for empty path")
        void shouldErrorOnEmptyPath() {
            ExecutionContext ctx = emptyContext();
            StepDefinition step = step(Map.of(
                    "path", "",
                    "check", "EXISTS"));

            AssertionResult ar = executor.evaluate(ctx, step);

            assertThat(ar.status()).isEqualTo(AssertionStatus.ERROR);
            assertThat(ar.description()).contains("must be a non-empty string");
        }
    }

    // ==================== Null safety ====================

    @Test
    @DisplayName("should throw NPE on null context")
    void shouldThrowOnNullContext() {
        StepDefinition step = step(Map.of("path", "/tmp", "check", "EXISTS"));
        assertThatThrownBy(() -> executor.evaluate(null, step))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NPE on null step")
    void shouldThrowOnNullStep() {
        ExecutionContext ctx = emptyContext();
        assertThatThrownBy(() -> executor.evaluate(ctx, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================== getSupportedAssertionName ====================

    @Test
    @DisplayName("should return 'file' as supported assertion name")
    void shouldReturnFileAsSupportedName() {
        assertThat(executor.getSupportedAssertionName()).isEqualTo("file");
    }

    // ==================== Evidence ====================

    @Test
    @DisplayName("should include evaluation timing metadata")
    void shouldIncludeTimingMetadata() {
        ExecutionContext ctx = emptyContext();
        StepDefinition step = step(Map.of(
                "path", existingFile.toString(),
                "check", "EXISTS"));

        AssertionResult ar = executor.evaluate(ctx, step);

        assertThat(ar.assertionId()).isEqualTo(ASSERTION_ID);
        assertThat(ar.evaluationDuration()).isNotNull();
        assertThat(ar.evaluatedAt()).isNotNull();
    }

    // --- Checksum helper ---

    private static String sha256Hex(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(filePath));
        return HexFormat.of().formatHex(hash);
    }
}
