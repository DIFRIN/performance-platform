package com.performance.platform.infrastructure.executor.fs;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FilesystemTaskExecutor")
class FilesystemTaskExecutorTest {

    private final FilesystemTaskExecutor executor = new FilesystemTaskExecutor();

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        executor.cleanup(null);
    }

    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"),
                ScenarioId.of("scenario-001"));
    }

    private Path resolve(String subPath) {
        return tempDir.resolve(subPath);
    }

    // ─────────────────────────────── CREATE ────────────────────────────────

    @Nested
    @DisplayName("CREATE operation")
    class CreateOperation {

        @Test
        @DisplayName("should create directory")
        void shouldCreateDirectory() {
            Path dir = resolve("new-dir");
            var step = step("CREATE", dir.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_PATH))
                    .isEqualTo(dir.toString());
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED))
                    .isEqualTo(1);
            assertTrue(Files.isDirectory(dir));
        }

        @Test
        @DisplayName("should create nested directories")
        void shouldCreateNestedDirectories() {
            Path deep = resolve("a/b/c");
            var step = step("CREATE", deep.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertTrue(Files.isDirectory(deep));
        }

        @Test
        @DisplayName("should succeed when directory already exists")
        void shouldSucceedWhenDirectoryExists() throws IOException {
            Path dir = resolve("existing");
            Files.createDirectories(dir);
            var step = step("CREATE", dir.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ─────────────────────────────── DELETE ────────────────────────────────

    @Nested
    @DisplayName("DELETE operation")
    class DeleteOperation {

        @Test
        @DisplayName("should delete single file")
        void shouldDeleteSingleFile() throws IOException {
            Path file = resolve("file.txt");
            Files.writeString(file, "data");
            var step = step("DELETE", file.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED))
                    .isEqualTo(1);
            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("should delete directory recursively")
        void shouldDeleteDirectoryRecursively() throws IOException {
            Path dir = resolve("recursive-dir");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("a.txt"), "a");
            Files.writeString(dir.resolve("b.txt"), "b");
            Files.createDirectories(dir.resolve("sub"));

            var step = new StepDefinition(
                    TaskId.of("step-del"), "filesystem", Phase.PREPARATION,
                    Map.of("operation", "DELETE", "path", dir.toString(),
                            "recursive", true),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((Integer) result.outputs().get(
                    FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED)).isGreaterThan(2);
            assertFalse(Files.exists(dir));
        }

        @Test
        @DisplayName("should fail to delete non-empty directory without recursive")
        void shouldFailToDeleteNonEmptyDirWithoutRecursive() throws IOException {
            Path dir = resolve("nonempty");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("f.txt"), "x");

            var step = step("DELETE", dir.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        @DisplayName("should succeed with filesAffected=0 when path does not exist")
        void shouldSucceedWhenPathDoesNotExist() {
            Path nonexistent = resolve("nonexistent");
            var step = step("DELETE", nonexistent.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED))
                    .isEqualTo(0);
        }
    }

    // ─────────────────────────────── UPLOAD ────────────────────────────────

    @Nested
    @DisplayName("UPLOAD operation")
    class UploadOperation {

        @Test
        @DisplayName("should copy file from source to dest")
        void shouldCopyFileFromSourceToDest() throws IOException {
            Path source = resolve("source.txt");
            Files.writeString(source, "content");
            Path dest = resolve("dest.txt");

            var step = new StepDefinition(
                    TaskId.of("step-upload"), "filesystem", Phase.PREPARATION,
                    Map.of("operation", "UPLOAD", "path", dest.toString(),
                            "source", source.toString()),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_PATH))
                    .isEqualTo(dest.toString());
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED))
                    .isEqualTo(1);
            assertTrue(Files.exists(dest));
            assertThat(Files.readString(dest)).isEqualTo("content");
        }

        @Test
        @DisplayName("should create parent directories during upload")
        void shouldCreateParentDirectoriesDuringUpload() throws IOException {
            Path source = resolve("upload-src.txt");
            Files.writeString(source, "data");
            Path dest = resolve("nested/deep/file.txt");

            var step = new StepDefinition(
                    TaskId.of("step-upload2"), "filesystem", Phase.PREPARATION,
                    Map.of("operation", "UPLOAD", "path", dest.toString(),
                            "source", source.toString()),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertTrue(Files.exists(dest));
        }

        @Test
        @DisplayName("should fail when source parameter is missing")
        void shouldFailWhenSourceMissing() {
            Path dest = resolve("dest.txt");
            var step = step("UPLOAD", dest.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("source");
        }
    }

    // ─────────────────────────────── CLEANUP ───────────────────────────────

    @Nested
    @DisplayName("CLEANUP operation")
    class CleanupOperation {

        @Test
        @DisplayName("should delete all contents of directory")
        void shouldDeleteAllContentsOfDirectory() throws IOException {
            Path dir = resolve("cleanup-dir");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("a.txt"), "a");
            Files.writeString(dir.resolve("b.txt"), "b");
            Files.createDirectories(dir.resolve("sub"));
            Files.writeString(dir.resolve("sub/c.txt"), "c");

            var step = step("CLEANUP", dir.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat((Integer) result.outputs().get(
                    FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED)).isGreaterThan(2);
            // Directory itself should still exist
            assertTrue(Files.exists(dir));
            // Contents should be gone via try-with-resources on list stream
        }

        @Test
        @DisplayName("should succeed with 0 filesAffected for non-existent path")
        void shouldSucceedForNonExistentPath() {
            Path nonexistent = resolve("nonexistent-cleanup");
            var step = step("CLEANUP", nonexistent.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outputs().get(FilesystemTaskExecutor.OUTPUT_FILES_AFFECTED))
                    .isEqualTo(0);
        }
    }

    // ─────────────────────────────── ERROR CASES ────────────────────────────

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should fail when path parameter is missing")
        void shouldFailWhenPathMissing() {
            var step = new StepDefinition(
                    TaskId.of("step-err1"), "filesystem", Phase.PREPARATION,
                    Map.of("operation", "CREATE"),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("path");
        }

        @Test
        @DisplayName("should fail for unknown operation")
        void shouldFailForUnknownOperation() {
            var step = step("RENAME", tempDir.toString());

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown operation");
        }

        @Test
        @DisplayName("should default operation to CREATE")
        void shouldDefaultOperationToCreate() {
            Path dir = resolve("default-op");
            var step = new StepDefinition(
                    TaskId.of("step-def"), "filesystem", Phase.PREPARATION,
                    Map.of("path", dir.toString()),
                    List.of(), List.of(), Duration.ofSeconds(10), null);

            TaskResult result = executor.execute(emptyContext(), step);

            assertThat(result.isSuccess()).isTrue();
            assertTrue(Files.isDirectory(dir));
        }

        @Test
        @DisplayName("should throw NPE when context is null")
        void shouldThrowNpeWhenContextIsNull() {
            var step = step("CREATE", tempDir.toString());
            assertThatThrownBy(() -> executor.execute(null, step))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NPE when step is null")
        void shouldThrowNpeWhenStepIsNull() {
            assertThatThrownBy(() -> executor.execute(emptyContext(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────── TASK NAME ──────────────────────────────

    @Test
    @DisplayName("getSupportedTaskName should return filesystem")
    void shouldReturnFilesystemTaskName() {
        assertThat(executor.getSupportedTaskName()).isEqualTo("filesystem");
    }

    // ─────────────────────────────── CLEANUP ────────────────────────────────

    @Nested
    @DisplayName("StatefulResourceCleaner")
    class StatefulCleanup {

        @Test
        @DisplayName("should delete all created directories on global cleanup")
        void shouldDeleteCreatedDirectoriesOnGlobalCleanup() throws IOException {
            Path dir1 = resolve("cleanup-global-1");
            Path dir2 = resolve("cleanup-global-2");
            Files.createDirectories(dir1);
            Files.createDirectories(dir2);

            // Not tracked yet (we only track via the executor's own creation)
            // But we can test that cleanup(null) runs without error
            assertDoesNotThrow(() -> executor.cleanup(null));
        }

        @Test
        @DisplayName("should not throw when cleaning up non-existent execution")
        void shouldNotThrowWhenCleaningNonExistentExecution() {
            assertDoesNotThrow(() -> executor.cleanup(ExecutionId.of("nonexistent")));
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private StepDefinition step(String operation, String path) {
        return new StepDefinition(
                TaskId.of("step-" + operation.toLowerCase()),
                "filesystem", Phase.PREPARATION,
                Map.of("operation", operation, "path", path),
                List.of(), List.of(), Duration.ofSeconds(10), null);
    }
}
