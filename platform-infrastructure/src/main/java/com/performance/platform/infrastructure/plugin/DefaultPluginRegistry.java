package com.performance.platform.infrastructure.plugin;

import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.infrastructure.executor.UnsupportedTaskNameException;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation par defaut du {@link PluginRegistry}.
 *
 * <p>Fusionne les {@link TaskExecutor} internes (beans Spring) et externes
 * (charges depuis les JARs du repertoire {@code platform.plugins.dir}).
 * La fusion a lieu dans le constructeur — le registre est immuable apres
 * initialisation.</p>
 *
 * <p>Regles de resolution :
 * <ul>
 *   <li>Phase deduite de l'annotation : {@code @Preparation} → PREPARATION,
 *       {@code @Injection} → INJECTION, {@code @Assertion} → ASSERTION</li>
 *   <li>Collision meme {@code (phase, name)} : l'externe ecrase l'interne
 *       (un {@link PluginWarning} est loggue)</li>
 *   <li>Collision entre deux JARs externes : le dernier charge gagne
 *       (ordre alphabetique des JARs dans le repertoire)</li>
 *   <li>{@code platform.plugins.enabled=false} : seuls les executors internes
 *       sont enregistres</li>
 * </ul>
 */
@Component
public class DefaultPluginRegistry implements PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginRegistry.class);

    private final EnumMap<Phase, Map<String, TaskExecutor>> registry;

    /**
     * Construit le registre unifie.
     *
     * <p>Ordre d'enregistrement :
     * <ol>
     *   <li>Enregistrement des executors internes (beans Spring)</li>
     *   <li>Appel de {@link PluginLoader#load(Path)} sur le repertoire configure</li>
     *   <li>Enregistrement des executors externes — ecrase les internes en cas de collision</li>
     * </ol>
     *
     * <p><b>CC-02</b>: constructor ~59 lines — single initialization flow
     * (validate inputs, register internals, load externals, merge with override).
     * Extracting sub-steps would scatter the three-phase registration logic and
     * require passing mutable intermediate state (registry, warnings count)
     * across methods.</p>
     *
     * @param internalExecutors les {@link TaskExecutor} fournis par Spring (peut etre vide)
     * @param loader            le chargeur de plugins JAR
     * @param pluginDir         le chemin du repertoire de plugins (depuis {@code platform.plugins.dir})
     */
    public DefaultPluginRegistry(
            List<TaskExecutor> internalExecutors,
            PluginLoader loader,
            @Value("${platform.plugins.dir:./plugins}") String pluginDir) {

        Objects.requireNonNull(internalExecutors, "internalExecutors must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        Objects.requireNonNull(pluginDir, "pluginDir must not be null");

        registry = new EnumMap<>(Phase.class);

        // 1. Register internal executors first
        for (TaskExecutor executor : internalExecutors) {
            Phase phase = derivePhase(executor);
            String name = executor.getSupportedTaskName();
            registerTo(phase, name, executor);
            log.info("action=register_internal phase={} name={} class={}",
                    phase, name, executor.getClass().getSimpleName());
        }

        // 2. Load external plugins (handle non-existent directory gracefully)
        Path dir = Path.of(pluginDir);
        PluginLoadResult result;
        try {
            result = loader.load(dir);
        } catch (IllegalArgumentException e) {
            // Directory does not exist or is not a valid directory — no external plugins
            log.info("action=plugin_directory_unavailable dir={} message=No external plugins loaded: {}",
                    dir.toAbsolutePath(), e.getMessage());
            result = new PluginLoadResult(0, 0, List.of(), List.of(), List.of());
        }

        // 3. Register external executors — external wins on collision
        for (TaskExecutor executor : result.externalExecutors()) {
            Phase phase = derivePhase(executor);
            String name = executor.getSupportedTaskName();

            Map<String, TaskExecutor> phaseRegistry = registry.get(phase);
            if (phaseRegistry != null && phaseRegistry.containsKey(name)) {
                TaskExecutor displaced = phaseRegistry.put(name, executor);
                log.warn("action=plugin_override phase={} name={} external={} displaces={}",
                        phase, name, executor.getClass().getSimpleName(),
                        displaced.getClass().getSimpleName());
            } else {
                registerTo(phase, name, executor);
                log.info("action=register_external phase={} name={} class={}",
                        phase, name, executor.getClass().getSimpleName());
            }
        }

        log.info("action=plugin_registry_initialized internal={} external={} total_preparation={} total_injection={} total_assertion={} warnings={} errors={}",
                internalExecutors.size(),
                result.executorsRegistered(),
                executorCount(Phase.PREPARATION),
                executorCount(Phase.INJECTION),
                executorCount(Phase.ASSERTION),
                result.warnings().size(),
                result.errors().size());
    }

    @Override
    public TaskExecutor lookup(Phase phase, String name) {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Map<String, TaskExecutor> phaseRegistry = registry.get(phase);
        if (phaseRegistry == null || !phaseRegistry.containsKey(name)) {
            throw new UnsupportedTaskNameException(
                    "No TaskExecutor registered for phase=" + phase + " name=" + name, name);
        }
        TaskExecutor executor = phaseRegistry.get(name);
        return executor;
    }

    @Override
    public boolean contains(Phase phase, String name) {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            return false;
        }
        Map<String, TaskExecutor> phaseRegistry = registry.get(phase);
        return phaseRegistry != null && phaseRegistry.containsKey(name);
    }

    @Override
    public Set<String> namesFor(Phase phase) {
        Objects.requireNonNull(phase, "phase must not be null");
        Map<String, TaskExecutor> phaseRegistry = registry.get(phase);
        if (phaseRegistry == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(phaseRegistry.keySet());
    }

    /**
     * Derive la {@link Phase} a partir de l'annotation presente sur la classe
     * de l'executor.
     *
     * @param executor le {@link TaskExecutor} a inspecter
     * @return la phase correspondante
     * @throws IllegalStateException si l'executor n'a aucune des trois annotations
     */
    private Phase derivePhase(TaskExecutor executor) {
        Class<?> clazz = executor.getClass();
        if (clazz.isAnnotationPresent(Preparation.class)) {
            return Phase.PREPARATION;
        }
        if (clazz.isAnnotationPresent(Injection.class)) {
            return Phase.INJECTION;
        }
        if (clazz.isAnnotationPresent(Assertion.class)) {
            return Phase.ASSERTION;
        }
        throw new IllegalStateException(
                "TaskExecutor " + clazz.getName()
                + " is missing @Preparation, @Injection, or @Assertion annotation");
    }

    private void registerTo(Phase phase, String name, TaskExecutor executor) {
        registerName(name);
        registry.computeIfAbsent(phase, p -> new ConcurrentHashMap<>())
                .put(name, executor);
    }

    private void registerName(String name) {
        // Validate name is not blank (defensive)
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "TaskExecutor.getSupportedTaskName() must return a non-blank name");
        }
    }

    private int executorCount(Phase phase) {
        Map<String, TaskExecutor> phaseRegistry = registry.get(phase);
        return phaseRegistry == null ? 0 : phaseRegistry.size();
    }

    /**
     * Retourne les noms de taches enregistres pour chaque phase.
     * Package-private — utilise dans les tests.
     */
    Map<Phase, Map<String, TaskExecutor>> allRegistries() {
        return Collections.unmodifiableMap(registry);
    }
}
