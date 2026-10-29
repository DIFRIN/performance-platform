package com.performance.platform.scenario;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves test fixtures without relying on a machine-specific checkout path.
 * <p>
 * Maven/Surefire runs tests with {@code user.dir} set to the module directory,
 * while an IDE may use the repository root. Tests therefore locate the repository
 * by walking up the directory tree, and resolve classpath directories through the
 * test classloader.
 */
public final class TestPaths {

    private TestPaths() {
    }

    /**
     * Repository root: the nearest ancestor that contains {@code pom.xml} and
     * {@code distribution/examples/scenarios}.
     */
    public static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("distribution/examples/scenarios"))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Cannot locate the repository root from user.dir=" + current
                        + " (expected pom.xml and distribution/examples/scenarios)");
    }

    /** Shared example scenarios from {@code distribution}. */
    public static Path exampleScenarios() {
        return repositoryRoot().resolve("distribution/examples/scenarios");
    }

    /**
     * Resolves a directory published on the test classpath (for example
     * {@code scenarios} under {@code src/test/resources}).
     */
    public static Path classpathDirectory(String name) {
        URL url = TestPaths.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new IllegalStateException("Classpath directory not found: " + name);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Classpath entry '" + name + "' is not a file-system directory: " + url, e);
        }
    }
}
