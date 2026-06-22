package com.performance.platform.app.plugin;

import com.performance.platform.infrastructure.plugin.PluginLoadResult;
import com.performance.platform.infrastructure.plugin.PluginLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bootstrap de chargement des plugins au demarrage (CF-06).
 *
 * <p>Execute une seule fois au demarrage de l'application Spring Boot.
 * Si {@code platform.plugins.enabled=true}, scanne le repertoire
 * {@code platform.plugins.dir} via {@link PluginLoader#load(Path)}.
 * Les erreurs (JAR invalide, classe non-instanciable) sont non-bloquantes
 * et loggees en WARN, la plateforme continue de demarrer.</p>
 *
 * <p>Logge le {@link PluginLoadResult} en INFO avec les compteurs
 * (jarsLoaded, executorsRegistered) et les avertissements/erreurs.</p>
 */
@Component
public class PluginBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PluginBootstrap.class);

    private final PluginLoader loader;
    private final PluginProperties props;

    public PluginBootstrap(PluginLoader loader, PluginProperties props) {
        this.loader = loader;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.enabled()) {
            log.info("action=plugin_loading_skipped reason=disabled");
            return;
        }

        var pluginDir = Path.of(props.dir());
        if (!Files.isDirectory(pluginDir)) {
            log.warn("action=plugin_loading_skipped reason=not_a_directory dir={}", pluginDir);
            return;
        }

        log.info("action=plugin_loading_started dir={}", pluginDir);
        PluginLoadResult result = loader.load(pluginDir);
        log.info("action=plugin_loading_completed jarsLoaded={} executorsRegistered={} warnings={} errors={}",
                result.jarsLoaded(), result.executorsRegistered(),
                result.warnings().size(), result.errors().size());

        if (result.hasErrors()) {
            result.errors().forEach(error ->
                    log.warn("action=plugin_load_error pluginJar={} message={}",
                            error.pluginJar(), error.message(), error.cause()));
        }
        if (result.hasWarnings()) {
            result.warnings().forEach(warning ->
                    log.warn("action=plugin_load_warning pluginJar={} message={}",
                            warning.pluginJar(), warning.message()));
        }
    }
}
