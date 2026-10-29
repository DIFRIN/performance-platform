package com.performance.platform.infrastructure.plugin;

import com.performance.platform.infrastructure.plugin.testfixture.ValidPreparationPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultPluginLoader}.
 *
 * Uses compiled test fixture classes (in testfixture/ package) packaged into JARs
 * at test time. The JARs are placed in a temporary directory and loaded by the
 * PluginLoader to verify real classloading behavior.
 */
@DisplayName("DefaultPluginLoader")
class DefaultPluginLoaderTest {

    private DefaultPluginLoader loader;

    @BeforeEach
    void setUp() {
        loader = new DefaultPluginLoader(true);
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("throws NullPointerException when pluginDirectory is null")
        void throwsOnNullDirectory() {
            assertThatThrownBy(() -> loader.load(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("pluginDirectory");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when path is not a directory")
        void throwsOnNonDirectory(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("not-a-directory.txt");
            Files.writeString(file, "content");

            assertThatThrownBy(() -> loader.load(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be an existing directory");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when directory does not exist")
        void throwsOnNonExistentDirectory(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("does-not-exist");

            assertThatThrownBy(() -> loader.load(nonExistent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be an existing directory");
        }
    }

    @Nested
    @DisplayName("Plugin loading disabled")
    class DisabledPlugins {

        @Test
        @DisplayName("returns empty result when platform.plugins.enabled is false")
        void returnsEmptyWhenDisabled(@TempDir Path tempDir) {
            var disabledLoader = new DefaultPluginLoader(false);
            PluginLoadResult result = disabledLoader.load(tempDir);

            assertThat(result.jarsLoaded()).isZero();
            assertThat(result.executorsRegistered()).isZero();
            assertThat(result.warnings()).isEmpty();
            assertThat(result.errors()).isEmpty();
            assertThat(result.hasErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("Empty plugin directory")
    class EmptyDirectory {

        @Test
        @DisplayName("returns empty result when directory contains no JARs")
        void returnsEmptyWhenNoJars(@TempDir Path tempDir) {
            PluginLoadResult result = loader.load(tempDir);

            assertThat(result.jarsLoaded()).isZero();
            assertThat(result.executorsRegistered()).isZero();
            assertThat(result.warnings()).isEmpty();
            assertThat(result.errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Valid plugin JAR")
    class ValidJar {

        @Test
        @DisplayName("loads and instantiates a valid @Preparation TaskExecutor from JAR")
        void loadsValidPreparationPlugin(@TempDir Path tempDir) throws IOException {
            createJarFromTestClass(tempDir,
                    "valid-plugin.jar",
                    "com/performance/platform/infrastructure/plugin/testfixture/ValidPreparationPlugin.class");

            PluginLoadResult result = loader.load(tempDir);

            assertThat(result.jarsLoaded()).isEqualTo(1);
            assertThat(result.executorsRegistered()).isEqualTo(1);
            assertThat(result.errors()).isEmpty();
            assertThat(result.warnings()).isEmpty();
            assertThat(result.hasErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("Corrupted JAR")
    class CorruptedJar {

        @Test
        @DisplayName("reports PluginError when JAR is corrupted but does not throw")
        void ignoresCorruptedJar(@TempDir Path tempDir) throws IOException {
            Path corruptedJar = tempDir.resolve("corrupted.jar");
            // Write random bytes that are not a valid ZIP/JAR
            Files.write(corruptedJar, new byte[] { 0x01, 0x02, 0x03, (byte) 0xFF, 0x00 });

            PluginLoadResult result = loader.load(tempDir);

            assertThat(result.jarsLoaded()).isZero();
            assertThat(result.executorsRegistered()).isZero();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).pluginJar()).isEqualTo("corrupted.jar");
            assertThat(result.errors().get(0).message()).contains("Failed to open JAR");
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        @DisplayName("continues loading after a corrupted JAR — loads valid JARs that follow")
        void continuesAfterCorruptedJar(@TempDir Path tempDir) throws IOException {
            // Create corrupted JAR first (alphabetically before "valid")
            Path corruptedJar = tempDir.resolve("a-corrupted.jar");
            Files.write(corruptedJar, new byte[] { (byte) 0xCA, (byte) 0xFE, 0x00 });

            // Create valid JAR second
            createJarFromTestClass(tempDir,
                    "z-valid.jar",
                    "com/performance/platform/infrastructure/plugin/testfixture/ValidPreparationPlugin.class");

            PluginLoadResult result = loader.load(tempDir);

            assertThat(result.jarsLoaded()).isEqualTo(1);
            assertThat(result.executorsRegistered()).isEqualTo(1);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).pluginJar()).isEqualTo("a-corrupted.jar");
        }
    }

    @Nested
    @DisplayName("Class without no-arg constructor")
    class NoArgConstructor {

        @Test
        @DisplayName("reports PluginError when annotated class has no no-arg constructor")
        void reportsErrorOnMissingNoArgConstructor(@TempDir Path tempDir) throws IOException {
            createJarFromTestClass(tempDir,
                    "no-arg-plugin.jar",
                    "com/performance/platform/infrastructure/plugin/testfixture/NoNoArgConstructorPlugin.class");

            PluginLoadResult result = loader.load(tempDir);

            assertThat(result.jarsLoaded()).isEqualTo(1);
            assertThat(result.executorsRegistered()).isZero(); // Not instantiated
            assertThat(result.errors()).hasSize(1);
            PluginError error = result.errors().get(0);
            assertThat(error.pluginJar()).isEqualTo("no-arg-plugin.jar");
            assertThat(error.message()).contains("no no-arg constructor");
            assertThat(error.cause()).isNotNull();
            assertThat(result.hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("JAR with zero plugin classes (no annotations)")
    class NoPluginsInJar {

        @Test
        @DisplayName("loads JAR but registers 0 executors when no class has plugin annotations")
        void skipsClassesWithoutAnnotations(@TempDir Path tempDir) throws IOException {
            // Create a JAR containing only a non-annotated class (using a system class)
            // We create a dummy JAR with a text file only — no .class files
            Path emptyLikeJar = tempDir.resolve("no-plugins.jar");
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(emptyLikeJar))) {
                jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                jos.write("Manifest-Version: 1.0\n".getBytes());
                jos.closeEntry();
            }

            PluginLoadResult result = loader.load(tempDir);

            assertThat(result.jarsLoaded()).isEqualTo(1);
            assertThat(result.executorsRegistered()).isZero();
            // No errors — JAR is valid, just contains no plugin classes
            assertThat(result.errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("PluginLoadResult immutability")
    class Immutability {

        @Test
        @DisplayName("warnings list is unmodifiable")
        void warningsListIsUnmodifiable() {
            var result = new PluginLoadResult(0, 0,
                    java.util.List.of(),
                    java.util.List.of(new PluginWarning("test.jar", "warning message")),
                    java.util.List.of());

            assertThat(result.warnings()).isUnmodifiable();
        }

        @Test
        @DisplayName("errors list is unmodifiable")
        void errorsListIsUnmodifiable() {
            var result = new PluginLoadResult(0, 0,
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(PluginError.of("test.jar", "error message")));

            assertThat(result.errors()).isUnmodifiable();
        }

        @Test
        @DisplayName("lists are defensive copies — mutations do not affect record")
        void defensiveCopyOnConstruction() {
            var mutableExecutors = new java.util.ArrayList<com.performance.platform.plugin.TaskExecutor>();
            var mutableWarnings = new java.util.ArrayList<PluginWarning>();
            mutableWarnings.add(new PluginWarning("a.jar", "warning"));
            var mutableErrors = new java.util.ArrayList<PluginError>();
            mutableErrors.add(PluginError.of("b.jar", "error"));

            var result = new PluginLoadResult(1, 2, mutableExecutors, mutableWarnings, mutableErrors);

            // Mutate original lists
            mutableExecutors.add(null);
            mutableWarnings.clear();
            mutableErrors.clear();

            // Record should be unaffected
            assertThat(result.externalExecutors()).isEmpty();
            assertThat(result.warnings()).hasSize(1);
            assertThat(result.errors()).hasSize(1);
        }

        @Test
        @DisplayName("rejects negative jarsLoaded")
        void rejectsNegativeJarsLoaded() {
            assertThatThrownBy(() -> new PluginLoadResult(-1, 0, java.util.List.of(), java.util.List.of(), java.util.List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jarsLoaded");
        }

        @Test
        @DisplayName("rejects negative executorsRegistered")
        void rejectsNegativeExecutorsRegistered() {
            assertThatThrownBy(() -> new PluginLoadResult(0, -1, java.util.List.of(), java.util.List.of(), java.util.List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("executorsRegistered");
        }

        @Test
        @DisplayName("externalExecutors list is unmodifiable")
        void externalExecutorsListIsUnmodifiable() {
            var result = new PluginLoadResult(0, 0,
                    java.util.List.of(new ValidPreparationPlugin()),
                    java.util.List.of(),
                    java.util.List.of());

            assertThat(result.externalExecutors()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("PluginError construction")
    class PluginErrorConstruction {

        @Test
        @DisplayName("PluginError.of with cause creates correct instance")
        void ofWithCause() {
            var cause = new RuntimeException("boom");
            var error = PluginError.of("test.jar", "failure message", cause);
            assertThat(error.pluginJar()).isEqualTo("test.jar");
            assertThat(error.message()).isEqualTo("failure message");
            assertThat(error.cause()).isSameAs(cause);
        }

        @Test
        @DisplayName("PluginError.of without cause has null cause")
        void ofWithoutCause() {
            var error = PluginError.of("test.jar", "failure message");
            assertThat(error.pluginJar()).isEqualTo("test.jar");
            assertThat(error.message()).isEqualTo("failure message");
            assertThat(error.cause()).isNull();
        }

        @Test
        @DisplayName("PluginError rejects blank pluginJar")
        void rejectsBlankPluginJar() {
            assertThatThrownBy(() -> PluginError.of("  ", "message"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pluginJar");
        }

        @Test
        @DisplayName("PluginError rejects blank message")
        void rejectsBlankMessage() {
            assertThatThrownBy(() -> PluginError.of("test.jar", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("message");
        }
    }

    @Nested
    @DisplayName("PluginWarning construction")
    class PluginWarningConstruction {

        @Test
        @DisplayName("constructs valid PluginWarning")
        void validWarning() {
            var warning = new PluginWarning("test.jar", "warning message");
            assertThat(warning.pluginJar()).isEqualTo("test.jar");
            assertThat(warning.message()).isEqualTo("warning message");
        }

        @Test
        @DisplayName("rejects blank pluginJar")
        void rejectsBlankPluginJar() {
            assertThatThrownBy(() -> new PluginWarning("  ", "message"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pluginJar");
        }

        @Test
        @DisplayName("rejects blank message")
        void rejectsBlankMessage() {
            assertThatThrownBy(() -> new PluginWarning("test.jar", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("message");
        }
    }

    // --- Helper methods ---

    /**
     * Creates a JAR file from a compiled test class found on the classpath.
     * The .class file is located via the classloader and packaged into a JAR
     * in the specified directory.
     *
     * @param outputDir the directory where the JAR will be created
     * @param jarName   the filename for the JAR (e.g. "plugin.jar")
     * @param className the binary name of the class inside the JAR
     *                  (e.g. "com/example/Plugin.class" — note: slash-separated path)
     */
    private void createJarFromTestClass(Path outputDir, String jarName, String resourcePath) throws IOException {
        Path jarPath = outputDir.resolve(jarName);

        // Locate the .class file via classloader
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream classStream = classLoader.getResourceAsStream(resourcePath)) {
            if (classStream == null) {
                throw new IllegalStateException("Test class resource not found: " + resourcePath
                        + " — ensure test fixtures are compiled (mvn test-compile)");
            }

            byte[] classBytes = classStream.readAllBytes();

            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
                // Add manifest
                jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                jos.write("Manifest-Version: 1.0\n".getBytes());
                jos.closeEntry();

                // Add the class file
                jos.putNextEntry(new ZipEntry(resourcePath));
                jos.write(classBytes);
                jos.closeEntry();
            }
        }
    }
}
