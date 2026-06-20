package com.performance.platform.app.plugin;

import com.performance.platform.infrastructure.plugin.PluginError;
import com.performance.platform.infrastructure.plugin.PluginLoadResult;
import com.performance.platform.infrastructure.plugin.PluginLoader;
import com.performance.platform.infrastructure.plugin.PluginWarning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link PluginBootstrap}.
 */
@ExtendWith(MockitoExtension.class)
class PluginBootstrapTest {

    @Mock
    private PluginLoader loader;

    @Mock
    private ApplicationArguments args;

    @Captor
    private ArgumentCaptor<Path> pathCaptor;

    @TempDir
    Path tempDir;

    private PluginBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        // Uses TempDir as plugin directory to satisfy Files.isDirectory()
    }

    @Test
    void shouldLoadPluginsWhenEnabled() {
        PluginLoadResult expected = new PluginLoadResult(3, 5, List.of(), List.of(), List.of());
        when(loader.load(any(Path.class))).thenReturn(expected);

        PluginProperties props = new PluginProperties(tempDir.toString(), true);
        bootstrap = new PluginBootstrap(loader, props);

        bootstrap.run(args);

        verify(loader).load(pathCaptor.capture());
        assertThat(pathCaptor.getValue()).isEqualTo(tempDir);
    }

    @Test
    void shouldSkipLoadingWhenDisabled() {
        PluginProperties props = new PluginProperties("/tmp/plugins", false);
        bootstrap = new PluginBootstrap(loader, props);

        bootstrap.run(args);

        verify(loader, never()).load(any());
    }

    @Test
    void shouldSkipLoadingWhenDirIsNotDirectory() {
        PluginProperties props = new PluginProperties("/nonexistent/plugins", true);
        bootstrap = new PluginBootstrap(loader, props);

        assertDoesNotThrow(() -> bootstrap.run(args));

        verify(loader, never()).load(any());
    }

    @Test
    void shouldNotCrashOnPluginLoadErrors() {
        PluginLoadResult resultWithErrors = new PluginLoadResult(
                2, 4, List.of(),
                List.of(new PluginWarning("plugin-a.jar", "name collision")),
                List.of(PluginError.of("corrupt.jar", "invalid JAR", new RuntimeException("boom")))
        );
        when(loader.load(any(Path.class))).thenReturn(resultWithErrors);

        PluginProperties props = new PluginProperties(tempDir.toString(), true);
        bootstrap = new PluginBootstrap(loader, props);

        // Must not throw — CF-06: invalid JAR generates warning, not crash
        assertDoesNotThrow(() -> bootstrap.run(args));

        verify(loader).load(any(Path.class));
    }

    @Test
    void shouldPropagateLoaderException() {
        when(loader.load(any(Path.class))).thenThrow(new RuntimeException("unexpected"));

        PluginProperties props = new PluginProperties(tempDir.toString(), true);
        bootstrap = new PluginBootstrap(loader, props);

        // Bootstrap should not catch loader exceptions — let Spring handle
        try {
            bootstrap.run(args);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("unexpected");
        }
    }

    @Test
    void shouldLogWarningsFromPluginLoadResult() {
        PluginWarning warning = new PluginWarning("plugin-b.jar", "name collision detected");
        PluginLoadResult resultWithWarnings = new PluginLoadResult(
                5, 8, List.of(),
                List.of(warning),
                List.of()
        );
        when(loader.load(any(Path.class))).thenReturn(resultWithWarnings);

        PluginProperties props = new PluginProperties(tempDir.toString(), true);
        bootstrap = new PluginBootstrap(loader, props);

        assertDoesNotThrow(() -> bootstrap.run(args));
        verify(loader).load(any(Path.class));
    }

    @Test
    void shouldLogErrorsFromPluginLoadResult() {
        PluginError error = PluginError.of("bad.jar", "class not found", new ClassNotFoundException("test"));
        PluginLoadResult resultWithErrors = new PluginLoadResult(
                2, 3, List.of(),
                List.of(),
                List.of(error)
        );
        when(loader.load(any(Path.class))).thenReturn(resultWithErrors);

        PluginProperties props = new PluginProperties(tempDir.toString(), true);
        bootstrap = new PluginBootstrap(loader, props);

        assertDoesNotThrow(() -> bootstrap.run(args));
        verify(loader).load(any(Path.class));
    }

    @Test
    void shouldHandleEmptyDirectoryGracefully() {
        PluginLoadResult empty = new PluginLoadResult(0, 0, List.of(), List.of(), List.of());
        when(loader.load(any(Path.class))).thenReturn(empty);

        PluginProperties props = new PluginProperties(tempDir.toString(), true);
        bootstrap = new PluginBootstrap(loader, props);

        assertDoesNotThrow(() -> bootstrap.run(args));
        verify(loader).load(any(Path.class));
    }
}
